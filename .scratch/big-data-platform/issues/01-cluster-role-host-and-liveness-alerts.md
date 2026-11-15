# 01 — 集群、角色、主机，以及角色不在的告警

**What to build:** 在新的 Python 控制面（FastAPI + PostgreSQL）上，人登记一个大数据集群，把 HDFS 或 YARN 的角色放到主机上，并写下配置（HDFS 副本数与容量，YARN 队列的内存与 vcore）。角色只允许 NameNode、DataNode、ResourceManager、NodeManager。Go Host Agent 交回该主机各角色是否在。角色不在则控制面记下当前故障并给出告警；角色恢复在则告警结束。不按策展/观测判断。不记录操作者。没有原因种类。不改 `backend/` 里的 Java。

**Blocked by:** None. 合同是 ADR-0050、ADR-0051、ADR-0052。修复不在本票。现有 Java 冲突、草案、探测接口停在原地。

**Status:** ready-for-agent

**TDD:** capability。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)。主接缝是新 Python 控制面的 HTTP API。Spec：[`docs/specs/big-data-platform.md`](../../../docs/specs/big-data-platform.md)。

- [ ] 登记集群、主机和 NameNode（或 NodeManager）后，读回角色以及副本或队列配置
- [ ] 角色名不是 NameNode、DataNode、ResourceManager、NodeManager 时拒绝
- [ ] Go Agent 报告角色不在：当前告警存在，证据是交回的原始结果
- [ ] 随后报告该角色在：同一条告警结束
- [ ] 同一主机另一个角色的存活不改变第一条告警
- [ ] 不带角色存活字段的上报不制造告警
- [ ] 登记、上报、读告警不带用户身份，不记录操作者
- [ ] `backend/` 无改动

**Out of this ticket:** 修复命令、容量耗尽、节点丢失、对象存储、Spark on K8s、Flink on K8s、删除 Java 代码、原因种类、处理人、确认关闭、Kubernetes 清单、编排层、模型建议、前端页面、执行引擎。

## Comments

### Cycle A — 登记集群、主机、NameNode 与 NodeManager 后读回副本或队列配置
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_registered_namenode_and_nodemanager_configs_are_readable -q`
```
E   ModuleNotFoundError: No module named 'archops_control'
tests/test_cluster_role_liveness_http.py:54: ModuleNotFoundError
ERROR tests/test_cluster_role_liveness_http.py::test_registered_namenode_and_nodemanager_configs_are_readable
1 warning, 1 error in 0.32s
```
Green command: same test, exit 0.
Refactor: SQL lives in `migrations/001_cluster_host_role.sql`; one `role_json` maps the stored row. Same test re-run, exit 0.
Commit: `816177e` Read back a registered NameNode replica and a NodeManager queue.

### Cycle B — 角色名不是 NameNode、DataNode、ResourceManager、NodeManager 时拒绝
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_role_outside_namenode_datanode_resourcemanager_nodemanager_is_rejected -q`
```
AssertionError: {"success":true,"code":"OK",...,"role":"JournalNode",...}
assert 200 == 400
FAILED tests/test_cluster_role_liveness_http.py::test_role_outside_namenode_datanode_resourcemanager_nodemanager_is_rejected
```
Green command: same test, exit 0. `ROLE_NOT_ALLOWED`, data null, role not stored.
Refactor: `require_role` checks the four-name allow-list. Same test re-run, exit 0. Cycle A still green.
Commit: `6241901` Reject a role name outside NameNode, DataNode, ResourceManager, and NodeManager.

### Cycle C — 角色不在则当前告警存在，证据是交回的原始结果
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_role_down_report_opens_current_alert_with_raw_evidence -q`
```
AssertionError: {"detail":"Not Found"}
assert 404 == 200
FAILED tests/test_cluster_role_liveness_http.py::test_role_down_report_opens_current_alert_with_raw_evidence
```
Green command: same test, exit 0. `GET /api/alerts` returns one current NameNode alert whose `evidence` is the posted `raw` string.
Refactor: `record_role_down` opens one current alert for a placed role. Same test re-run, exit 0.
Commit: `0610e9a` Open a current alert when a placed role is reported down.

### Cycle D — 随后报告该角色在，同一条告警结束
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_role_up_report_ends_the_same_alert -q`
```
AssertionError: {"detail":"Not Found"}
assert 404 == 200
FAILED tests/test_cluster_role_liveness_http.py::test_role_up_report_ends_the_same_alert
```
Green command: same test, exit 0. Same alert id, `current` false, original `evidence` kept, absent from `GET /api/alerts`.
Refactor: alert reads share `_ALERT_COLUMNS`. Same test re-run, exit 0. Cycle C still green.
Commit: `98dc385` End the same alert when that role is reported up.

### Cycle E — 同一主机另一个角色的存活不改变第一条告警
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_other_role_liveness_leaves_the_first_alert_unchanged -q`
First-run green (exit 0). Opening and ending an alert already match `host_id` and `role` (cycle D). This test locks that a DataNode down and a later DataNode up leave the NameNode alert's id, current flag, and raw evidence unchanged. No production removed to manufacture a red; no production change.
Refactor: tests share `_host` and `_place`. Full file re-run, 5 passed.
Commit: `759bafe` Lock that another role's liveness leaves the first alert unchanged.

### Cycle F — 不带角色存活字段的上报不制造告警
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_report_without_role_liveness_field_opens_no_alert -q`
```
E   KeyError: 'roleLiveness'
src/archops_control/app.py:192: KeyError
FAILED tests/test_cluster_role_liveness_http.py::test_report_without_role_liveness_field_opens_no_alert
```
Green command: same test, exit 0. Report body has `sentAt` and no `roleLiveness`; `GET /api/alerts` is `[]`.
Refactor: no structural change. Same test re-run, exit 0. Role-down report still green.
Commit: `674fed2` Ignore a report that does not carry role liveness.

### Cycle G — 登记、上报、读告警不带用户身份，不记录操作者
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_register_report_and_read_alert_need_no_user_and_record_no_operator -q`
First-run green (exit 0). Register, place, report, and both alert reads accept `X-ArchOps-User-Id: user-should-not-be-stored` and also succeed with no identity header. That id is absent from every body, and no operator / user / actor key is present. The API never grew an auth gate or an operator column, so there was no production behavior to remove.
Refactor: no structural change.
Commit: `b1e2338` Record that registration, liveness, and alerts carry no operator.

### Cycle H — Go Agent 报告 NameNode 不在，证据是进程列表原文
Red command: `cd control-plane && pytest tests/test_cluster_role_liveness_http.py::test_go_agent_down_report_opens_alert_with_process_listing -q`
```
E   FileNotFoundError: [Errno 2] No such file or directory: PosixPath('/agent/repos/ArchOps/host-agent')
FAILED tests/test_cluster_role_liveness_http.py::test_go_agent_down_report_opens_alert_with_process_listing
```
Green command: same test, exit 0. Evidence is `java -Dproc=org.apache.hadoop.hdfs.server.datanode.DataNode`. `go test ./...` in `host-agent` passes: raw listing, omitted `roleLiveness` when no role is collected, no user header.
Refactor: liveness helpers take a `psycopg.Connection`. Full Python file re-run, 8 passed.
Commit: pending Report a down NameNode from the Go host agent.







