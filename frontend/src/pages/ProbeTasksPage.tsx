import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { confirmCloseConflict, getConflict, getDiagnosis } from '../api/conflicts';
import { createHost } from '../api/curated';
import { acceptDraftItem, rejectDraftItem } from '../api/drafts';
import { createProbeTask, getProbeTask, type ProbeTask } from '../api/probeTasks';
import { selectBranch } from '../api/plans';
import type { ConflictCase, ConflictDiagnosis, CuratedDraft, OperationPlan } from '../api/types';
import { ApiError } from '../api/types';

const { Title, Paragraph, Text } = Typography;

const TASK_IDS_KEY = 'archops.probeTaskIds';
const CONFLICT_IDS_KEY = 'archops.probeConflictIds';
const FIX_ACTUAL = 'FIX_ACTUAL_TO_CURATED';
const CHANGE_CURATED = 'CHANGE_CURATED_TO_OBSERVED';

function envelopeMessage(err: unknown): string {
  return err instanceof ApiError ? `${err.code}: ${err.message}` : String(err);
}

function judgmentLabel(judgment: string): string {
  if (judgment === 'NORMAL') {
    return '正常';
  }
  if (judgment === 'CONFLICT') {
    return '冲突';
  }
  if (judgment === 'HOLLOW') {
    return '空洞';
  }
  return judgment;
}

function formatJson(value: unknown): string {
  if (value == null) {
    return '—';
  }
  return JSON.stringify(value, null, 2);
}

function readTaskIds(): string[] {
  try {
    const raw = sessionStorage.getItem(TASK_IDS_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed.filter((id) => typeof id === 'string') : [];
  } catch {
    return [];
  }
}

function writeTaskIds(ids: string[]): void {
  try {
    sessionStorage.setItem(TASK_IDS_KEY, JSON.stringify(ids));
  } catch {
    // The in-memory list still shows the tasks created in this view.
  }
}

function readConflictIds(): Record<string, string> {
  try {
    const raw = sessionStorage.getItem(CONFLICT_IDS_KEY);
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw) as unknown;
    if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    const ids: Record<string, string> = {};
    for (const [taskId, conflictId] of Object.entries(parsed)) {
      if (typeof conflictId === 'string' && conflictId) {
        ids[taskId] = conflictId;
      }
    }
    return ids;
  } catch {
    return {};
  }
}

function rememberConflictId(taskId: string, conflictId: string): void {
  try {
    const ids = readConflictIds();
    ids[taskId] = conflictId;
    sessionStorage.setItem(CONFLICT_IDS_KEY, JSON.stringify(ids));
  } catch {
    // Confirm-close still works in this view from component state.
  }
}

function isPlan(result: OperationPlan | CuratedDraft): result is OperationPlan {
  return 'steps' in result;
}

async function waitForDiagnosis(conflictId: string): Promise<ConflictDiagnosis> {
  const deadline = Date.now() + 8_000;
  let latest: ConflictDiagnosis | null = null;
  while (Date.now() < deadline) {
    try {
      latest = await getDiagnosis(conflictId);
      if (latest.status === 'READY' || latest.status === 'FAILED') {
        return latest;
      }
    } catch (err) {
      if (!(err instanceof ApiError) || err.code !== 'DIAGNOSIS_NOT_FOUND') {
        throw err;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  if (latest) {
    return latest;
  }
  throw new ApiError('DIAGNOSIS_NOT_READY', '诊断尚未生成', 404);
}

export default function ProbeTasksPage() {
  const [hostName, setHostName] = useState('');
  const [hostId, setHostId] = useState('');
  const [command, setCommand] = useState(`printf '{"up":true}'`);
  const [intervalSeconds, setIntervalSeconds] = useState(60);
  const [curatedText, setCuratedText] = useState('{\n  "up": true\n}');
  const [tasks, setTasks] = useState<ProbeTask[]>([]);
  const [plans, setPlans] = useState<Record<string, OperationPlan>>({});
  const [drafts, setDrafts] = useState<Record<string, CuratedDraft>>({});
  const [conflicts, setConflicts] = useState<Record<string, ConflictCase>>({});
  const [restored, setRestored] = useState<Record<string, boolean>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async (ids: string[]) => {
    if (ids.length === 0) {
      setTasks([]);
      return;
    }
    const rows = await Promise.all(ids.map((id) => getProbeTask(id)));
    setTasks(rows);
  }, []);

  useEffect(() => {
    const ids = readTaskIds();
    if (ids.length === 0) {
      return;
    }
    void refresh(ids).catch((err: unknown) => {
      const msg = envelopeMessage(err);
      setError(msg);
      message.error(msg);
    });
  }, [refresh]);

  useEffect(() => {
    const stored = readConflictIds();
    let cancelled = false;
    for (const task of tasks) {
      const conflictId = task.conflictId || stored[task.id];
      if (!conflictId) {
        continue;
      }
      if (task.conflictId) {
        rememberConflictId(task.id, task.conflictId);
      }
      void getConflict(conflictId)
        .then((conflict) => {
          if (cancelled) {
            return;
          }
          if (conflict.status === 'PENDING_CLOSE' || conflict.status === 'CLOSED') {
            setConflicts((current) => ({ ...current, [task.id]: conflict }));
          }
        })
        .catch(() => {
          // A missing conflict does not hide the task judgment.
        });
    }
    return () => {
      cancelled = true;
    };
  }, [tasks]);

  const remember = (next: ProbeTask) => {
    setTasks((current) => {
      const without = current.filter((row) => row.id !== next.id);
      const ids = [next.id, ...without.map((row) => row.id)];
      writeTaskIds(ids);
      return [next, ...without];
    });
    setHostId(next.hostId);
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      let curatedExpected: unknown;
      try {
        curatedExpected = JSON.parse(curatedText) as unknown;
      } catch {
        throw new ApiError('VALIDATION_ERROR', '策展结构化值不是 JSON', 400);
      }
      if (curatedExpected == null || typeof curatedExpected !== 'object' || Array.isArray(curatedExpected)) {
        throw new ApiError('VALIDATION_ERROR', '策展结构化值必须是 JSON 对象', 400);
      }
      let boundHostId = hostId.trim();
      if (!boundHostId) {
        const name = hostName.trim();
        if (!name) {
          throw new ApiError('VALIDATION_ERROR', '填写主机名称或主机 ID', 400);
        }
        const host = await createHost(name);
        boundHostId = host.id;
        setHostId(host.id);
      }
      const created = await createProbeTask({
        hostId: boundHostId,
        command: command.trim(),
        intervalSeconds,
        curatedExpected,
      });
      remember(created);
      message.success('已保存探测任务');
    } catch (err) {
      const msg = envelopeMessage(err);
      setError(msg);
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const reloadOne = async (taskId: string) => {
    const next = await getProbeTask(taskId);
    setTasks((current) => current.map((row) => (row.id === taskId ? next : row)));
    return next;
  };

  const requestFixActual = async (task: ProbeTask) => {
    if (!task.conflictId) {
      return;
    }
    setBusyId(task.id);
    setError(null);
    try {
      await waitForDiagnosis(task.conflictId);
      const result = await selectBranch(task.conflictId, FIX_ACTUAL);
      if (!isPlan(result)) {
        throw new ApiError('PLAN_NOT_FOUND', '修实际没有打开待审计划', 400);
      }
      setPlans((current) => ({ ...current, [task.id]: result }));
      message.success('已打开待审计划');
    } catch (err) {
      const msg = envelopeMessage(err);
      setError(msg);
      message.error(msg);
    } finally {
      setBusyId(null);
    }
  };

  const requestChangeCurated = async (task: ProbeTask) => {
    if (!task.conflictId) {
      return;
    }
    setBusyId(task.id);
    setError(null);
    try {
      await waitForDiagnosis(task.conflictId);
      const result = await selectBranch(task.conflictId, CHANGE_CURATED);
      if (isPlan(result)) {
        throw new ApiError('DRAFT_NOT_FOUND', '改理想没有打开草案', 400);
      }
      setDrafts((current) => ({ ...current, [task.id]: result }));
      if (result.conflictId) {
        rememberConflictId(task.id, result.conflictId);
      }
      await reloadOne(task.id);
      message.success('已打开草案，策展值未写入');
    } catch (err) {
      const msg = envelopeMessage(err);
      setError(msg);
      message.error(msg);
    } finally {
      setBusyId(null);
    }
  };

  const reviewDraftItem = async (task: ProbeTask, itemId: string, decision: 'accept' | 'reject') => {
    const draft = drafts[task.id];
    if (!draft?.conflictId) {
      return;
    }
    setBusyId(task.id);
    setError(null);
    try {
      const next =
        decision === 'accept'
          ? await acceptDraftItem(draft.conflictId, itemId)
          : await rejectDraftItem(draft.conflictId, itemId);
      setDrafts((current) => ({ ...current, [task.id]: next }));
      rememberConflictId(task.id, draft.conflictId);
      await reloadOne(task.id);
      const conflict = await getConflict(draft.conflictId);
      setConflicts((current) => ({ ...current, [task.id]: conflict }));
      message.success(decision === 'accept' ? '已接受条目并写入策展' : '已拒绝条目，策展未改');
    } catch (err) {
      const msg = envelopeMessage(err);
      setError(msg);
      message.error(msg);
    } finally {
      setBusyId(null);
    }
  };

  const confirmClose = async (taskId: string) => {
    const conflict = conflicts[taskId];
    if (!conflict) {
      return;
    }
    setBusyId(taskId);
    setError(null);
    try {
      const closed = await confirmCloseConflict(conflict.id);
      setConflicts((current) => ({ ...current, [taskId]: closed }));
      await reloadOne(taskId);
      message.success('已确认关闭');
    } catch (err) {
      const msg = envelopeMessage(err);
      setError(msg);
      message.error(msg);
    } finally {
      setBusyId(null);
    }
  };

  const requestRestoreChannel = (taskId: string) => {
    setRestored((current) => ({ ...current, [taskId]: true }));
    message.success('这条任务只恢复观测通道');
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={3} style={{ marginBottom: 4 }}>
          探测任务
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          策展结构化值是唯一的正常。控制面判决交回；本页不执行命令。同一主机上的任务各自独立。
        </Paragraph>
      </div>

      {error ? <Alert type="error" showIcon message={error} /> : null}

      <Card size="small" title="保存探测任务">
        <Form layout="vertical" onFinish={() => void save()}>
          <Form.Item label="主机名称" extra="没有主机 ID 时，用名称登记一台物理主机再绑定。">
            <Input value={hostName} onChange={(event) => setHostName(event.target.value)} placeholder="probe-host" />
          </Form.Item>
          <Form.Item label="主机 ID" extra="已有主机时填写。保存后会留在这里，下一条任务仍绑这台主机。">
            <Input value={hostId} onChange={(event) => setHostId(event.target.value)} placeholder="host-…" />
          </Form.Item>
          <Form.Item label="命令" required>
            <Input value={command} onChange={(event) => setCommand(event.target.value)} />
          </Form.Item>
          <Form.Item label="间隔秒数" required>
            <InputNumber min={0} value={intervalSeconds} onChange={(value) => setIntervalSeconds(value ?? 0)} />
          </Form.Item>
          <Form.Item label="策展结构化值" required>
            <Input.TextArea
              rows={4}
              value={curatedText}
              onChange={(event) => setCuratedText(event.target.value)}
            />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={saving}>
            保存探测任务
          </Button>
        </Form>
      </Card>

      <Space>
        <Button
          onClick={() => {
            const ids = tasks.map((task) => task.id);
            void refresh(ids).catch((err: unknown) => {
              const msg = envelopeMessage(err);
              setError(msg);
              message.error(msg);
            });
          }}
        >
          刷新判决
        </Button>
        <Text type="secondary">{tasks.length} 条任务</Text>
      </Space>

      {tasks.map((task) => {
        const plan = plans[task.id] ?? null;
        const draft = drafts[task.id] ?? null;
        const hollow = task.judgment === 'HOLLOW';
        const conflict = task.judgment === 'CONFLICT';
        const normal = task.judgment === 'NORMAL';
        return (
          <Card
            key={task.id}
            size="small"
            title={
              <Space wrap>
                <span>探测任务</span>
                <Tag color={conflict ? 'orange' : normal ? 'green' : 'default'}>
                  {judgmentLabel(task.judgment)}
                </Tag>
                <Text type="secondary">{task.id}</Text>
              </Space>
            }
          >
            <Descriptions column={1} size="small">
              <Descriptions.Item label="主机">{task.hostId}</Descriptions.Item>
              <Descriptions.Item label="命令">{task.command}</Descriptions.Item>
              <Descriptions.Item label="间隔秒数">{task.intervalSeconds}</Descriptions.Item>
              <Descriptions.Item label="策展值">
                <pre style={{ margin: 0 }}>{formatJson(task.curatedExpected)}</pre>
              </Descriptions.Item>
              <Descriptions.Item label="观测值">
                <pre style={{ margin: 0 }}>{formatJson(task.observedValue)}</pre>
              </Descriptions.Item>
              {task.evidence ? (
                <Descriptions.Item label="原始证据">{task.evidence}</Descriptions.Item>
              ) : null}
            </Descriptions>

            {normal ? <Paragraph style={{ marginTop: 12 }}>与策展值相等。没有冲突。</Paragraph> : null}

            {conflict ? (
              <Space wrap style={{ marginTop: 12 }}>
                <Button
                  type="primary"
                  loading={busyId === task.id}
                  onClick={() => void requestFixActual(task)}
                >
                  修实际
                </Button>
                <Button loading={busyId === task.id} onClick={() => void requestChangeCurated(task)}>
                  改理想
                </Button>
              </Space>
            ) : null}

            {hollow ? (
              <div style={{ marginTop: 12 }}>
                <Button type="primary" onClick={() => requestRestoreChannel(task.id)}>
                  恢复观测通道
                </Button>
                {restored[task.id] ? (
                  <Paragraph style={{ marginTop: 8, marginBottom: 0 }}>
                    这条任务只恢复观测通道。控制面不执行该命令，也不打开计划或草案。通道靠 Host Agent 的下一次心跳恢复，再由控制面判决。
                  </Paragraph>
                ) : (
                  <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
                    空洞不是冲突。允许的动作只有恢复观测通道。
                  </Paragraph>
                )}
              </div>
            ) : null}

            {plan ? (
              <Descriptions column={1} size="small" style={{ marginTop: 12 }} title="待审计划">
                <Descriptions.Item label="计划">{plan.id}</Descriptions.Item>
                <Descriptions.Item label="状态">{plan.status}</Descriptions.Item>
                <Descriptions.Item label="选支">{plan.selectedForkId}</Descriptions.Item>
              </Descriptions>
            ) : null}

            {draft ? (
              <div style={{ marginTop: 12 }}>
                <Descriptions column={1} size="small" title="改理想草案">
                  <Descriptions.Item label="草案">{draft.id}</Descriptions.Item>
                  <Descriptions.Item label="状态">{draft.status}</Descriptions.Item>
                </Descriptions>
                {draft.items.map((item) => (
                  <Space key={item.id} wrap style={{ marginTop: 8 }}>
                    <Tag>{item.status}</Tag>
                    <Text>把观测结构化值写成策展</Text>
                    {draft.status === 'OPEN' && item.status === 'PENDING' ? (
                      <>
                        <Button
                          type="primary"
                          size="small"
                          loading={busyId === task.id}
                          onClick={() => void reviewDraftItem(task, item.id, 'accept')}
                        >
                          接受
                        </Button>
                        <Button
                          size="small"
                          danger
                          loading={busyId === task.id}
                          onClick={() => void reviewDraftItem(task, item.id, 'reject')}
                        >
                          拒绝
                        </Button>
                      </>
                    ) : null}
                  </Space>
                ))}
                {draft.items.some((item) => item.status === 'PENDING') ? (
                  <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
                    确认前不是策展。上面的策展值尚未改写。
                  </Paragraph>
                ) : null}
              </div>
            ) : null}

            {conflicts[task.id]?.status === 'PENDING_CLOSE' ? (
              <div style={{ marginTop: 12 }}>
                <Button type="primary" loading={busyId === task.id} onClick={() => void confirmClose(task.id)}>
                  确认关闭
                </Button>
              </div>
            ) : null}
            {conflicts[task.id]?.status === 'CLOSED' ? (
              <Paragraph style={{ marginTop: 12, marginBottom: 0 }}>冲突已确认关闭。</Paragraph>
            ) : null}
          </Card>
        );
      })}
    </Space>
  );
}
