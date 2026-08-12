import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  List,
  Radio,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import { Link, useParams } from 'react-router-dom';
import {
  acknowledgeAndSelfAppoint,
  acknowledgeConflict,
  claimConflict,
  confirmCloseConflict,
  getConflict,
  getDiagnosis,
} from '../api/conflicts';
import { approvePlan, getActivePlan, selectBranch, startExecution } from '../api/plans';
import type { ConflictCase, ConflictDiagnosis, OperationPlan } from '../api/types';
import { ApiError } from '../api/types';
import { useDemoUser } from '../auth/DemoUserContext';
import { formatTrack, isAcceptedHandler } from '../util/format';

const { Title, Paragraph, Text } = Typography;

const FIX_ACTUAL_FORK = 'FIX_ACTUAL_TO_CURATED';

export default function ConflictDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const { userId, user } = useDemoUser();

  const [conflict, setConflict] = useState<ConflictCase | null>(null);
  const [diagnosis, setDiagnosis] = useState<ConflictDiagnosis | null>(null);
  const [diagnosisError, setDiagnosisError] = useState<string | null>(null);
  const [plan, setPlan] = useState<OperationPlan | null>(null);
  const [planError, setPlanError] = useState<string | null>(null);
  const [selectedForkId, setSelectedForkId] = useState<string>(FIX_ACTUAL_FORK);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const c = await getConflict(id);
      setConflict(c);

      try {
        const d = await getDiagnosis(id);
        setDiagnosis(d);
        setDiagnosisError(null);
        const fix = d.forks.find((f) => f.id === FIX_ACTUAL_FORK);
        setSelectedForkId(fix?.id ?? d.forks[0]?.id ?? FIX_ACTUAL_FORK);
      } catch (err) {
        setDiagnosis(null);
        if (err instanceof ApiError && err.code === 'DIAGNOSIS_NOT_FOUND') {
          setDiagnosisError('诊断尚未生成');
        } else {
          setDiagnosisError(err instanceof ApiError ? err.message : String(err));
        }
      }

      // Active-plan API only accepts OPEN conflicts; skip when closed/pending/suspended.
      if (c.status === 'OPEN') {
        try {
          const p = await getActivePlan(id);
          setPlan(p);
          setPlanError(null);
        } catch (err) {
          setPlan(null);
          if (err instanceof ApiError && err.code === 'PLAN_NOT_FOUND') {
            setPlanError(null);
          } else {
            setPlanError(err instanceof ApiError ? err.message : String(err));
          }
        }
      } else {
        setPlan(null);
        setPlanError(null);
      }
    } catch (err) {
      setConflict(null);
      const msg = err instanceof ApiError ? `${err.code}: ${err.message}` : String(err);
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [id, userId]);

  useEffect(() => {
    void load();
  }, [load]);

  const runAction = async (label: string, action: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await action();
      message.success(label);
      await load();
    } catch (err) {
      const msg = err instanceof ApiError ? `${err.code}: ${err.message}` : String(err);
      message.error(msg);
    } finally {
      setBusy(false);
    }
  };

  if (loading && !conflict) {
    return <Spin tip="加载冲突…" />;
  }

  if (error || !conflict) {
    return (
      <Space direction="vertical">
        <Link to="/">← 返回列表</Link>
        <Alert type="error" showIcon message={error ?? '未找到冲突'} />
      </Space>
    );
  }

  const collab = conflict.collaboration;
  const accepted = isAcceptedHandler(collab, userId);
  const isSenior = user?.role === 'SENIOR';
  const isGeneral = user?.role === 'GENERAL';
  const canCollab =
    conflict.status === 'OPEN' &&
    !collab.acknowledged &&
    collab.handlerAcceptance !== 'ACCEPTED';

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Link to="/">← 返回列表</Link>
        <Title level={4} style={{ marginTop: 8, marginBottom: 4 }}>
          冲突详情
        </Title>
        <Space wrap>
          <Tag>{conflict.status}</Tag>
          {conflict.pendingCloseReminderVisible && <Tag color="blue">待确认关闭</Tag>}
          {conflict.observationHollow && <Tag>观测空洞</Tag>}
          <Text type="secondary">{conflict.id}</Text>
        </Space>
      </div>

      <Card size="small" title="双轨">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="对象">
            {conflict.subject.name ?? conflict.mergeKey.subjectId} · {conflict.mergeKey.relationLabel}
          </Descriptions.Item>
          <Descriptions.Item label="应该（策展）">
            {formatTrack(conflict.curatedValue)}
          </Descriptions.Item>
          <Descriptions.Item label="实际（观测）">
            {conflict.observationHollow
              ? '空洞（不可信）'
              : formatTrack(conflict.observedValue)}
          </Descriptions.Item>
          <Descriptions.Item label="诊断状态">{conflict.diagnosisStatus}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card size="small" title="协作">
        <Descriptions column={1} size="small" style={{ marginBottom: 12 }}>
          <Descriptions.Item label="已知悉">{collab.acknowledged ? '是' : '否'}</Descriptions.Item>
          <Descriptions.Item label="归属">{collab.ownerUserId ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="处理人">
            {collab.handlerUserId ?? '—'}（{collab.handlerAcceptance}）
          </Descriptions.Item>
        </Descriptions>
        <Space wrap>
          {canCollab && isGeneral && (
            <Button
              type="primary"
              loading={busy}
              onClick={() => void runAction('已认领', () => claimConflict(conflict.id))}
            >
              认领
            </Button>
          )}
          {canCollab && isSenior && (
            <>
              <Button
                loading={busy}
                onClick={() => void runAction('已已知悉', () => acknowledgeConflict(conflict.id))}
              >
                已知悉
              </Button>
              <Button
                type="primary"
                loading={busy}
                onClick={() =>
                  void runAction('已知悉并自任', () => acknowledgeAndSelfAppoint(conflict.id))
                }
              >
                已知悉并自任处理人
              </Button>
            </>
          )}
          {!canCollab && !accepted && (
            <Text type="secondary">当前身份不是已接受处理人，无法开计划或确认关闭。</Text>
          )}
          {accepted && <Tag color="green">你是已接受处理人</Tag>}
        </Space>
      </Card>

      <Card size="small" title="诊断 / 分叉">
        {diagnosisError && !diagnosis && (
          <Alert type="info" showIcon message={diagnosisError} style={{ marginBottom: 12 }} />
        )}
        {diagnosis && (
          <>
            <Paragraph>{diagnosis.summary}</Paragraph>
            <Text type="secondary">
              {diagnosis.status} · {diagnosis.source}
            </Text>
            <Divider style={{ margin: '12px 0' }} />
            <Radio.Group
              value={selectedForkId}
              onChange={(e) => setSelectedForkId(e.target.value as string)}
              style={{ width: '100%' }}
            >
              <Space direction="vertical" style={{ width: '100%' }}>
                {diagnosis.forks.map((fork) => (
                  <Radio key={fork.id} value={fork.id} style={{ alignItems: 'flex-start' }}>
                    <Space direction="vertical" size={0}>
                      <Text strong>
                        {fork.label}
                        {fork.id === FIX_ACTUAL_FORK ? '（竖切主路径）' : ''}
                      </Text>
                      <Text type="secondary">{fork.description}</Text>
                    </Space>
                  </Radio>
                ))}
              </Space>
            </Radio.Group>
            <Divider style={{ margin: '12px 0' }} />
            <Button
              type="primary"
              disabled={!accepted || diagnosis.status !== 'READY' || !!plan}
              loading={busy}
              onClick={() =>
                void runAction('已选择分叉并生成计划', () =>
                  selectBranch(conflict.id, selectedForkId),
                )
              }
            >
              选择分叉并生成操作计划
            </Button>
            {plan && (
              <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
                已有活跃计划，不可再选支。
              </Paragraph>
            )}
          </>
        )}
      </Card>

      <Card size="small" title="操作计划">
        {planError && <Alert type="warning" showIcon message={planError} style={{ marginBottom: 12 }} />}
        {!plan && !planError && (
          <Text type="secondary">尚无活跃操作计划。成为已接受处理人后选择「修实际回策展宿主」生成。</Text>
        )}
        {plan && (
          <>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="计划 ID">{plan.id}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag>{plan.status}</Tag>
                {plan.executionIntent && <Tag color="blue">已冻结执行意图</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="选支">{plan.selectedForkId}</Descriptions.Item>
              <Descriptions.Item label="分支">{plan.branchKind}</Descriptions.Item>
              {plan.voidReason && (
                <Descriptions.Item label="作废原因">{plan.voidReason}</Descriptions.Item>
              )}
            </Descriptions>
            <List
              size="small"
              header="步骤（只读审查）"
              dataSource={plan.steps}
              renderItem={(step) => (
                <List.Item>
                  <Text>
                    {step.seq}. [{step.action}] {step.description}
                  </Text>
                </List.Item>
              )}
            />
            <Space wrap style={{ marginTop: 12 }}>
              <Button
                type="primary"
                disabled={!accepted || plan.status !== 'DRAFT_REVIEW'}
                loading={busy}
                onClick={() => void runAction('计划已批准', () => approvePlan(plan.id))}
              >
                批准计划
              </Button>
              <Button
                disabled={!accepted || plan.status !== 'APPROVED'}
                loading={busy}
                onClick={() =>
                  void runAction('已启动受控执行', () => startExecution(plan.id))
                }
              >
                启动受控执行
              </Button>
            </Space>
            <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
              执行走控制面计划冻结路径（fake/MINA SSH），界面不提供旁路直连或终端。
            </Paragraph>
          </>
        )}
      </Card>

      <Card size="small" title="确认关闭">
        {conflict.status === 'PENDING_CLOSE' ? (
          <>
            <Alert
              type="info"
              showIcon
              message="策展与当前可用观测已对齐，等待已接受处理人确认关闭。"
              style={{ marginBottom: 12 }}
            />
            <Button
              type="primary"
              danger
              disabled={!accepted}
              loading={busy}
              onClick={() => void runAction('冲突已关闭', () => confirmCloseConflict(conflict.id))}
            >
              确认关闭
            </Button>
          </>
        ) : (
          <Text type="secondary">
            仅在 PENDING_CLOSE 时可确认关闭。当前状态：{conflict.status}。
          </Text>
        )}
      </Card>

      <Button onClick={() => void load()} loading={loading || busy}>
        刷新
      </Button>
    </Space>
  );
}
