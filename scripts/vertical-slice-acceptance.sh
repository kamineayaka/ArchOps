#!/usr/bin/env bash
# ArchOps vertical-slice HTTP acceptance (Linux / Cloud VM).
# Primary seam: control-plane public HTTP API (+ Agent ingest). SSH uses fake by default.
#
# Prerequisites:
#   - API reachable (bootRun or archops container on :8080)
#   - Postgres + Redis up
#   - curl, jq, docker (only for hollow negative clock backdate)
#
# Usage:
#   bash scripts/vertical-slice-acceptance.sh
#   API=http://127.0.0.1:8080 bash scripts/vertical-slice-acceptance.sh
set -euo pipefail

API="${API:-http://127.0.0.1:8080}"
GENERAL="${GENERAL:-user-general-demo}"
SENIOR="${SENIOR:-user-senior-demo}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/compose/compose.yaml}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${OUT:-${ROOT}/.scratch/vertical-slice-acceptance}"
mkdir -p "$OUT"
LOG="$OUT/demo-log.txt"
: >"$LOG"

pass=0
fail=0
note() { echo -e "$*" | tee -a "$LOG"; }
ok() { pass=$((pass + 1)); note "PASS  $*"; }
bad() { fail=$((fail + 1)); note "FAIL  $*"; }
hdr() { note "\n======== $* ========"; }

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing dependency: $1" >&2
    exit 1
  }
}
need curl
need jq

api() {
  local method="$1" path="$2" body="${3:-}" user="${4:-}"
  local args=(-sS -X "$method" -H "Accept: application/json" -H "Content-Type: application/json")
  [[ -n "$user" ]] && args+=(-H "X-ArchOps-User-Id: $user")
  if [[ -n "$body" ]]; then
    curl "${args[@]}" -d "$body" "${API}${path}"
  else
    curl "${args[@]}" "${API}${path}"
  fi
}

json_get() { jq -r "$1"; }

hdr "0. Health"
h="$(api GET /api/health)"
echo "$h" | tee -a "$LOG" >/dev/null
echo "$h" | jq -e '.data.status=="UP"' >/dev/null && ok "GET /api/health UP" || bad "health"

SUFFIX="$(date +%s)"
OBJECT_ID="ctr-demo-${SUFFIX}"
AGENT_ID="agent-demo-${SUFFIX}"

hdr "1. Curate hosts A/B + container X runs-on A"
hostA="$(api POST /api/curated/hosts "{\"name\":\"demo-a-${SUFFIX}\"}" "$GENERAL" | tee -a "$LOG" | json_get '.data.id')"
hostB="$(api POST /api/curated/hosts "{\"name\":\"demo-b-${SUFFIX}\"}" "$GENERAL" | tee -a "$LOG" | json_get '.data.id')"
ctr="$(api POST /api/curated/containers "{\"name\":\"app-${OBJECT_ID}\",\"objectId\":\"${OBJECT_ID}\"}" "$GENERAL" | tee -a "$LOG" | json_get '.data.id')"
api POST /api/curated/facts/runs-on "{\"containerId\":\"${ctr}\",\"hostId\":\"${hostA}\"}" "$GENERAL" | tee -a "$LOG" >/dev/null
should="$(api GET "/api/curated/asks/should-where?containerId=${ctr}" "" "$GENERAL")"
echo "$should" | tee -a "$LOG" >/dev/null
echo "$should" | jq -e --arg a "$hostA" '.data.curatedValue.hostId==$a and .data.question=="应该在哪"' >/dev/null \
  && ok "策展：X 运行于 A（应该在哪）" || bad "策展 should-where"

hdr "2. Agent heartbeat+snapshot: X observed on B"
hb="$(api POST /api/agent/heartbeat "{\"agentId\":\"${AGENT_ID}\",\"hostId\":\"${hostB}\",\"snapshot\":{\"containers\":[{\"runtimeId\":\"docker-x\",\"name\":\"app\",\"labels\":{\"archops.object_id\":\"${OBJECT_ID}\"}}]}}")"
echo "$hb" | tee -a "$LOG" >/dev/null
echo "$hb" | jq -e '.data.matched|length==1' >/dev/null && ok "心跳快照匹配 object_id" || bad "heartbeat match"

actual="$(api GET "/api/observed/asks/actual-where?containerId=${ctr}" "" "$GENERAL")"
echo "$actual" | tee -a "$LOG" >/dev/null
echo "$actual" | jq -e --arg a "$hostA" --arg b "$hostB" \
  '.data.observedValue.hostId==$b and .data.curatedValue.hostId==$a' >/dev/null \
  && ok "实际在哪=B，同屏策展=A" || bad "actual-where"

hdr "3. Conflict warning (may precede diagnosis READY)"
cnf="$(api GET "/api/conflicts/by-merge-key?subjectId=${ctr}&relationType=RUNS_ON" "" "$GENERAL")"
echo "$cnf" | tee -a "$LOG" >/dev/null
conflictId="$(echo "$cnf" | json_get '.data.id')"
diagStatus="$(echo "$cnf" | json_get '.data.diagnosisStatus')"
echo "$cnf" | jq -e '.data.status=="OPEN"' >/dev/null \
  && ok "冲突 OPEN id=${conflictId} diagnosisStatus=${diagStatus}" || bad "conflict warn"

hdr "4. Collaboration: claim → ACCEPTED handler"
claim="$(api POST "/api/conflicts/${conflictId}/claim" "" "$GENERAL")"
echo "$claim" | tee -a "$LOG" >/dev/null
echo "$claim" | jq -e --arg u "$GENERAL" \
  '.data.collaboration.handlerAcceptance=="ACCEPTED" and .data.collaboration.handlerUserId==$u' >/dev/null \
  && ok "认领成功 → 已接受处理人" || bad "claim"

st=""
for _ in $(seq 1 40); do
  d="$(api GET "/api/conflicts/${conflictId}/diagnosis" "" "$GENERAL" || true)"
  st="$(echo "$d" | jq -r '.data.status // empty' 2>/dev/null || true)"
  if [[ "$st" == "READY" ]]; then
    echo "$d" | tee -a "$LOG" >/dev/null
    ok "诊断 READY forks=$(echo "$d" | jq -c '[.data.forks[].id]')"
    break
  fi
  sleep 0.25
done
[[ "${st}" == "READY" ]] || bad "diagnosis not READY"

hdr "5. FIX_ACTUAL → approve → execute (SSH fake)"
deny="$(api POST "/api/conflicts/${conflictId}/branch-selection" '{"forkId":"FIX_ACTUAL_TO_CURATED"}' "$SENIOR" || true)"
echo "$deny" | tee -a "$LOG" >/dev/null
[[ "$(echo "$deny" | jq -r '.code // empty')" == "PLAN_REQUIRES_ACCEPTED_HANDLER" ]] \
  && ok "非处理人选支被拒" || bad "non-handler gate"

plan="$(api POST "/api/conflicts/${conflictId}/branch-selection" '{"forkId":"FIX_ACTUAL_TO_CURATED"}' "$GENERAL")"
echo "$plan" | tee -a "$LOG" >/dev/null
planId="$(echo "$plan" | json_get '.data.id')"
echo "$plan" | jq -e '.data.status=="DRAFT_REVIEW" and .data.skipsDraft==true and .data.executionIntent==false' >/dev/null \
  && ok "生成计划 ${planId}（跳过草案）" || bad "branch selection"

pre="$(api POST "/api/operation-plans/${planId}/start-execution" "" "$GENERAL" || true)"
echo "$pre" | tee -a "$LOG" >/dev/null
[[ "$(echo "$pre" | jq -r '.code')" == "PLAN_NOT_APPROVED" ]] && ok "未审批不可执行" || bad "pre-approve exec"

ap="$(api POST "/api/operation-plans/${planId}/approve" "" "$GENERAL")"
echo "$ap" | tee -a "$LOG" >/dev/null
echo "$ap" | jq -e '.data.status=="APPROVED" and .data.executionIntent==true' >/dev/null && ok "人审通过" || bad "approve"

ex="$(api POST "/api/operation-plans/${planId}/start-execution" "" "$GENERAL")"
echo "$ex" | tee -a "$LOG" >/dev/null
echo "$ex" | jq -e '.data.status=="COMPLETED" and .data.completedSteps==3' >/dev/null \
  && ok "SSH fake 执行完成（3 steps）" || bad "execute"

hdr "6. Observation back to A → PENDING_CLOSE → confirm"
api POST /api/agent/heartbeat "{\"agentId\":\"${AGENT_ID}-aligned\",\"hostId\":\"${hostA}\",\"snapshot\":{\"containers\":[{\"runtimeId\":\"docker-x\",\"name\":\"app\",\"labels\":{\"archops.object_id\":\"${OBJECT_ID}\"}}]}}" \
  | tee -a "$LOG" >/dev/null

pc="$(api GET "/api/conflicts/${conflictId}" "" "$GENERAL")"
echo "$pc" | tee -a "$LOG" >/dev/null
echo "$pc" | jq -e --arg a "$hostA" \
  '.data.status=="PENDING_CLOSE" and .data.curatedValue.hostId==$a and .data.observedValue.hostId==$a' >/dev/null \
  && ok "待确认关闭（策展=观测=A）" || bad "pending close"

nd="$(api POST "/api/conflicts/${conflictId}/confirm-close" "" "$SENIOR" || true)"
echo "$nd" | tee -a "$LOG" >/dev/null
[[ "$(echo "$nd" | jq -r '.code')" == "CONFIRM_CLOSE_REQUIRES_ACCEPTED_HANDLER" ]] \
  && ok "非处理人不可确认关闭" || bad "confirm gate"

cl="$(api POST "/api/conflicts/${conflictId}/confirm-close" "" "$GENERAL")"
echo "$cl" | tee -a "$LOG" >/dev/null
echo "$cl" | jq -e '.data.status=="CLOSED"' >/dev/null && ok "处理人确认关闭 → CLOSED" || bad "confirm close"

ev="$(api GET "/api/conflicts/${conflictId}/events" "" "$GENERAL")"
echo "$ev" | tee -a "$LOG" >/dev/null
echo "$ev" | jq -e '[.data[].eventType] | index("WARNED") and index("HANDLER_ACCEPTED") and index("PLAN_COMPLETED") and index("PENDING_CLOSE") and index("CLOSED")' >/dev/null \
  && ok "审计事件链完整" || bad "events trail"

hdr "7a. Negative: sensitive business read → reject"
sr="$(api POST /api/workbench/sensitive-reads '{"target":"business_db.orders","intent":"READ_CUSTOMER_ORDERS"}' "$GENERAL" || true)"
echo "$sr" | tee -a "$LOG" >/dev/null
[[ "$(echo "$sr" | jq -r '.code')" == "SENSITIVE_BUSINESS_READ_DENIED" ]] && ok "敏感读拒绝" || bad "sensitive read"

hdr "7b. Negative: unlabeled snapshot → no upgrade-chain promise"
hostU="$(api POST /api/curated/hosts "{\"name\":\"demo-unb-${SUFFIX}\"}" "$GENERAL" | json_get '.data.id')"
ctrLost="$(api POST /api/curated/containers "{\"name\":\"app-lost-${SUFFIX}\",\"objectId\":\"ctr-lost-${SUFFIX}\"}" "$GENERAL" | json_get '.data.id')"
api POST /api/curated/facts/runs-on "{\"containerId\":\"${ctrLost}\",\"hostId\":\"${hostU}\"}" "$GENERAL" >/dev/null
unb="$(api POST /api/agent/heartbeat "{\"agentId\":\"agent-unb-${SUFFIX}\",\"hostId\":\"${hostU}\",\"snapshot\":{\"containers\":[{\"runtimeId\":\"mystery\",\"name\":\"m\",\"labels\":{}},{\"runtimeId\":\"u\",\"name\":\"u\",\"labels\":{\"archops.object_id\":\"never-curated-${SUFFIX}\"}}],\"identityLostObjectIds\":[\"ctr-lost-${SUFFIX}\"]}}")"
echo "$unb" | tee -a "$LOG" >/dev/null
echo "$unb" | jq -e '([.data.unbound[].upgradeChainPromised] | all(.==false)) and (.data.identityLost|length==1) and (.data.identityLost[0].upgradeChainPromised==false)' >/dev/null \
  && ok "未打标/身份失联不承诺升级链" || bad "unlabeled"

hdr "7c. Negative: heartbeat timeout → SUSPENDED + VOIDED plan"
OBJECT2="ctr-hollow-${SUFFIX}"
hA="$(api POST /api/curated/hosts "{\"name\":\"hollow-a-${SUFFIX}\"}" "$GENERAL" | json_get '.data.id')"
hB="$(api POST /api/curated/hosts "{\"name\":\"hollow-b-${SUFFIX}\"}" "$GENERAL" | json_get '.data.id')"
c2="$(api POST /api/curated/containers "{\"name\":\"app-${OBJECT2}\",\"objectId\":\"${OBJECT2}\"}" "$GENERAL" | json_get '.data.id')"
api POST /api/curated/facts/runs-on "{\"containerId\":\"${c2}\",\"hostId\":\"${hA}\"}" "$GENERAL" >/dev/null
api POST /api/agent/heartbeat "{\"agentId\":\"agent-${OBJECT2}\",\"hostId\":\"${hB}\",\"snapshot\":{\"containers\":[{\"runtimeId\":\"x\",\"name\":\"a\",\"labels\":{\"archops.object_id\":\"${OBJECT2}\"}}]}}" >/dev/null
c2id="$(api GET "/api/conflicts/by-merge-key?subjectId=${c2}" "" "$GENERAL" | json_get '.data.id')"
api POST "/api/conflicts/${c2id}/claim" "" "$GENERAL" >/dev/null
for _ in $(seq 1 40); do
  st="$(api GET "/api/conflicts/${c2id}/diagnosis" "" "$GENERAL" | jq -r '.data.status // empty')"
  [[ "$st" == "READY" ]] && break
  sleep 0.25
done
p2="$(api POST "/api/conflicts/${c2id}/branch-selection" '{"forkId":"FIX_ACTUAL_TO_CURATED"}' "$GENERAL" | json_get '.data.id')"
api POST "/api/operation-plans/${p2}/approve" "" "$GENERAL" >/dev/null

# Default ARCHOPS_HEARTBEAT_TIMEOUT=5m — backdate beyond TTL (CI clock control only).
if docker compose -f "${ROOT}/${COMPOSE_FILE}" exec -T postgres \
  psql -U archops -d archops -c "UPDATE host_agent SET last_heartbeat_at = now() - interval '10 minutes' WHERE agent_id = 'agent-${OBJECT2}';" \
  >/dev/null 2>&1; then
  ok "backdated host_agent heartbeat (> default 5m TTL)"
else
  bad "could not backdate host_agent (need docker compose postgres)"
fi

scan="$(api POST /api/observed/scan-heartbeat-timeouts "" "$GENERAL")"
echo "$scan" | tee -a "$LOG" >/dev/null
echo "$scan" | jq -e --arg c "$c2id" --arg p "$p2" \
  '(.data.suspendedConflictIds|index($c)) and (.data.voidedPlanIds|index($p))' >/dev/null \
  && ok "空洞扫描：冲突挂起 + 计划作废" || bad "hollow scan"

cs="$(api GET "/api/conflicts/${c2id}" "" "$GENERAL")"
echo "$cs" | tee -a "$LOG" >/dev/null
echo "$cs" | jq -e '.data.status=="SUSPENDED" and .data.observationHollow==true' >/dev/null \
  && ok "冲突 SUSPENDED / hollow" || bad "suspended status"
ps="$(api GET "/api/operation-plans/${p2}" "" "$GENERAL")"
echo "$ps" | tee -a "$LOG" >/dev/null
echo "$ps" | jq -e '.data.status=="VOIDED"' >/dev/null && ok "计划 VOIDED" || bad "plan voided"
block="$(api POST "/api/operation-plans/${p2}/start-execution" "" "$GENERAL" || true)"
[[ "$(echo "$block" | jq -r '.code')" == "PLAN_VOIDED" ]] && ok "作废计划不可再执行" || bad "voided exec blocked"

hdr "SUMMARY"
note "passed=${pass} failed=${fail}"
note "conflictId=${conflictId} planId=${planId} objectId=${OBJECT_ID}"
note "log=${LOG}"
[[ "$fail" -eq 0 ]]
