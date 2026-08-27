import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  List,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import { listActiveConflicts } from '../api/conflicts';
import { getShouldWhere, type ShouldWhere } from '../api/curated';
import { acceptUnboundDraftItem, getUnboundDraft, rejectUnboundDraftItem } from '../api/drafts';
import { createUnboundDraft, getActualWhere, getIdentityLost, listUnboundCandidates } from '../api/observed';
import type {
  ActualWhere,
  ConflictCase,
  CuratedDraft,
  CuratedDraftItem,
  IdentityLostMark,
  UnboundCandidate,
} from '../api/types';
import { ApiError } from '../api/types';
import { useDemoUser } from '../auth/DemoUserContext';
import {
  formatActualWhereValue,
  formatUnboundDraftItemKind,
  formatUnboundLabels,
  payloadString,
} from '../util/format';

const { Title, Paragraph, Text } = Typography;

const REASON_COLOR: Record<string, string> = {
  MISSING_LABEL: 'gold',
  UNKNOWN_OBJECT_ID: 'purple',
};

function envelopeMessage(err: unknown): string {
  return err instanceof ApiError ? `${err.code}: ${err.message}` : String(err);
}

function describeUnboundItem(item: CuratedDraftItem): string {
  if (item.kind === 'CREATE_CONTAINER_FROM_UNBOUND') {
    const objectId = payloadString(item.payload, 'immutableObjectId');
    const name = payloadString(item.payload, 'proposedName');
    return `proposedName=${name ?? '—'} · immutableObjectId=${objectId ?? '（无）'}`;
  }
  if (item.kind === 'BIND_UNBOUND_TO_EXISTING') {
    return `subjectId=${item.subjectId ?? '—'}`;
  }
  if (item.kind === 'CURATED_RUNS_ON_INSERT') {
    return `toHostId=${item.toHostId ?? '—'}`;
  }
  return item.kind;
}

export default function UnboundCandidatesPage() {
  const { userId } = useDemoUser();
  const { draftId: draftIdParam } = useParams<{ draftId?: string }>();
  const navigate = useNavigate();
  const [rows, setRows] = useState<UnboundCandidate[]>([]);
  const [lostConflicts, setLostConflicts] = useState<ConflictCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [draft, setDraft] = useState<CuratedDraft | null>(null);
  const [draftError, setDraftError] = useState<string | null>(null);

  const [askId, setAskId] = useState('');
  const [askBusy, setAskBusy] = useState(false);
  const [askError, setAskError] = useState<string | null>(null);
  const [lostMark, setLostMark] = useState<IdentityLostMark | null>(null);
  const [lostAbsent, setLostAbsent] = useState(false);
  const [shouldWhere, setShouldWhere] = useState<ShouldWhere | null>(null);
  const [actualWhere, setActualWhere] = useState<ActualWhere | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [candidates, conflicts] = await Promise.all([
        listUnboundCandidates(),
        listActiveConflicts().catch(() => [] as ConflictCase[]),
      ]);
      setRows(candidates);
      setLostConflicts(conflicts.filter((row) => row.identityLost));
    } catch (err) {
      const msg = envelopeMessage(err);
      setError(msg);
      setRows([]);
      setLostConflicts([]);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  const loadDraft = useCallback(async (draftId: string) => {
    try {
      const dft = await getUnboundDraft(draftId);
      setDraft(dft);
      setDraftError(null);
      return dft;
    } catch (err) {
      setDraft(null);
      const msg = envelopeMessage(err);
      setDraftError(msg);
      message.error(msg);
      return null;
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (draftIdParam) {
      void loadDraft(draftIdParam);
    }
  }, [draftIdParam, loadDraft, userId]);

  const lookupObject = useCallback(async (rawId: string) => {
    const containerId = rawId.trim();
    if (!containerId) {
      setAskError('请输入已知策展对象 ID');
      return;
    }
    setAskBusy(true);
    setAskError(null);
    setLostMark(null);
    setLostAbsent(false);
    setShouldWhere(null);
    setActualWhere(null);
    try {
      try {
        setShouldWhere(await getShouldWhere(containerId));
      } catch (err) {
        setShouldWhere(null);
        if (!(err instanceof ApiError)) {
          throw err;
        }
        setAskError(envelopeMessage(err));
      }

      try {
        setActualWhere(await getActualWhere(containerId));
      } catch (err) {
        setActualWhere(null);
        if (!(err instanceof ApiError)) {
          throw err;
        }
        setAskError((prev) => prev ?? envelopeMessage(err));
      }

      try {
        setLostMark(await getIdentityLost(containerId));
        setLostAbsent(false);
      } catch (err) {
        setLostMark(null);
        if (err instanceof ApiError && err.code === 'IDENTITY_LOST_NOT_FOUND') {
          setLostAbsent(true);
        } else if (err instanceof ApiError) {
          setAskError((prev) => prev ?? envelopeMessage(err));
        } else {
          throw err;
        }
      }
    } catch (err) {
      const msg = envelopeMessage(err);
      setAskError(msg);
      message.error(msg);
    } finally {
      setAskBusy(false);
    }
  }, []);

  const runDraftAction = async (label: string, action: () => Promise<CuratedDraft | null>) => {
    setBusy(true);
    try {
      const next = await action();
      if (next) {
        message.success(label);
        setDraft(next);
        setDraftError(null);
        await load();
      }
    } catch (err) {
      const msg = envelopeMessage(err);
      setDraftError(msg);
      message.error(msg);
    } finally {
      setBusy(false);
    }
  };

  const startDraft = (candidateId: string) => {
    void runDraftAction('已发起未绑定草案', async () => {
      const created = await createUnboundDraft(candidateId);
      navigate(`/unbound/drafts/${encodeURIComponent(created.id)}`);
      return created;
    });
  };

  const columns: ColumnsType<UnboundCandidate> = [
    {
      title: '原因',
      dataIndex: 'reason',
      key: 'reason',
      width: 160,
      render: (reason: string) => <Tag color={REASON_COLOR[reason] ?? 'default'}>{reason}</Tag>,
    },
    {
      title: '宿主 sourceHostId',
      dataIndex: 'sourceHostId',
      key: 'sourceHostId',
      render: (id: string) => <Text code>{id}</Text>,
    },
    {
      title: 'runtimeId',
      dataIndex: 'runtimeId',
      key: 'runtimeId',
      render: (id: string) => <Text code>{id}</Text>,
    },
    {
      title: 'name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '标签线索',
      dataIndex: 'labels',
      key: 'labels',
      render: (labels: Record<string, string>) => {
        const objectId = labels['archops.object_id'];
        return (
          <Space direction="vertical" size={0}>
            <Text>archops.object_id={objectId ? objectId : '（无）'}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {formatUnboundLabels(labels)}
            </Text>
          </Space>
        );
      },
    },
    {
      title: '升级链',
      dataIndex: 'upgradeChainPromised',
      key: 'upgradeChainPromised',
      width: 140,
      render: (promised: boolean) =>
        promised ? <Tag color="red">true</Tag> : <Tag>false · 不承诺升级链</Tag>,
    },
    {
      title: '',
      key: 'actions',
      width: 120,
      render: (_, row) => (
        <Button
          type="link"
          size="small"
          loading={busy}
          onClick={() => startDraft(row.id)}
        >
          发起草案
        </Button>
      ),
    },
  ];

  const actualAvailability = actualWhere?.observedValue.availability;
  const actualIsLost = Boolean(
    actualWhere?.identityLost || actualAvailability === 'IDENTITY_LOST',
  );

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>
            待并入未绑定观测候选
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            匹配失败列出的现场实体，不是冲突，也不是身份失联。默认只看待并入；弱线索不承诺升级链。
          </Paragraph>
        </div>
        <Button onClick={() => void load()} loading={loading}>
          刷新
        </Button>
      </div>
      {error ? <Alert type="error" showIcon message={error} /> : null}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '暂无待并入未绑定观测候选。空列表可演示：匹配失败才会出现在此。' }}
      />

      <Card size="small" title="未绑定草案（不挂冲突）">
        <Paragraph type="secondary">
          从待并入候选发起。逐条确认走 GET/POST /api/curated-drafts/...，不走冲突处理人路由。任一条目接受即写入；拒绝不写。
          失败信封会显示 AUTH_REQUIRED、UNBOUND_DRAFT_ALREADY_OPEN、UNBOUND_CANDIDATE_CONSUMED、UNBOUND_BIND_TARGET_ALREADY_BOUND、UNBOUND_BIND_TARGET_HEALTHY、UNBOUND_CREATE_IMMUTABLE_ID_MISSING、DRAFT_VOIDED。
        </Paragraph>
        {draftError ? <Alert type="error" showIcon message={draftError} style={{ marginBottom: 12 }} /> : null}
        {!draft && !draftError ? (
          <Text type="secondary">尚未打开草案。对上表一行点「发起草案」。</Text>
        ) : null}
        {draft ? (
          <>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="草案 ID">{draft.id}</Descriptions.Item>
              <Descriptions.Item label="origin">{draft.origin ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="conflictId">{draft.conflictId ?? 'null'}</Descriptions.Item>
              <Descriptions.Item label="candidateId">{draft.candidateId ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={draft.status === 'VOIDED' ? 'default' : undefined}>{draft.status}</Tag>
              </Descriptions.Item>
            </Descriptions>
            <List
              size="small"
              header="条目（至少两条，独立确认）"
              dataSource={[...draft.items].sort((a, b) => a.seq - b.seq)}
              renderItem={(item) => (
                <List.Item
                  actions={
                    draft.status === 'OPEN' && item.status === 'PENDING'
                      ? [
                          <Button
                            key="accept"
                            type="link"
                            size="small"
                            loading={busy}
                            onClick={() =>
                              void runDraftAction('条目已接受', () =>
                                acceptUnboundDraftItem(draft.id, item.id),
                              )
                            }
                          >
                            接受
                          </Button>,
                          <Button
                            key="reject"
                            type="link"
                            size="small"
                            danger
                            loading={busy}
                            onClick={() =>
                              void runDraftAction('条目已拒绝', () =>
                                rejectUnboundDraftItem(draft.id, item.id),
                              )
                            }
                          >
                            拒绝
                          </Button>,
                        ]
                      : undefined
                  }
                >
                  <Space direction="vertical" size={0}>
                    <Text>
                      {item.seq}. {formatUnboundDraftItemKind(item.kind)}
                    </Text>
                    <Text type="secondary">{describeUnboundItem(item)}</Text>
                    <Space size="small" wrap>
                      <Tag
                        color={
                          item.status === 'ACCEPTED'
                            ? 'green'
                            : item.status === 'REJECTED'
                              ? 'red'
                              : undefined
                        }
                      >
                        {item.status}
                      </Tag>
                      {item.kind === 'BIND_UNBOUND_TO_EXISTING' && item.subjectId ? (
                        <Button
                          type="link"
                          size="small"
                          onClick={() => {
                            const subjectId = item.subjectId;
                            if (!subjectId) {
                              return;
                            }
                            setAskId(subjectId);
                            void lookupObject(subjectId);
                          }}
                        >
                          查询该对象问法
                        </Button>
                      ) : null}
                    </Space>
                  </Space>
                </List.Item>
              )}
            />
          </>
        ) : null}
      </Card>

      <Card size="small" title="身份失联 · 规范问法">
        <Paragraph type="secondary">
          身份失联不是观测空洞，也不是观测消失。对已知策展对象查询既有 GET；400 IDENTITY_LOST_NOT_FOUND
          表示未失联。没有失联集合路由。
        </Paragraph>
        {lostConflicts.length > 0 ? (
          <Space wrap style={{ marginBottom: 12 }}>
            <Text type="secondary">冲突列表旁路（identityLost=true，本页不认领/选支）：</Text>
            {lostConflicts.map((row) => (
              <Button
                key={row.id}
                size="small"
                onClick={() => {
                  const id = row.mergeKey.subjectId;
                  setAskId(id);
                  void lookupObject(id);
                }}
              >
                {row.subject.name ?? row.mergeKey.subjectId}
              </Button>
            ))}
          </Space>
        ) : (
          <Paragraph type="secondary">当前开放冲突中没有 identityLost=true 的旁路行。</Paragraph>
        )}
        <Input.Search
          value={askId}
          onChange={(e) => setAskId(e.target.value)}
          onSearch={(value) => void lookupObject(value)}
          placeholder="策展容器 ID"
          enterButton="查询应该在哪 / 实际在哪"
          loading={askBusy}
          allowClear
        />
        {askError ? (
          <Alert type="error" showIcon message={askError} style={{ marginTop: 12 }} />
        ) : null}
        {(lostMark || lostAbsent || shouldWhere || actualWhere) && (
          <Descriptions column={1} size="small" style={{ marginTop: 16 }} bordered>
            <Descriptions.Item label="失联标">
              {lostMark ? (
                <Space wrap>
                  <Tag color="orange">身份失联</Tag>
                  <Text>{lostMark.reason}</Text>
                  <Text type="secondary">
                    sourceHostId={lostMark.sourceHostId} · upgradeChainPromised=
                    {String(lostMark.upgradeChainPromised)}
                  </Text>
                </Space>
              ) : lostAbsent ? (
                <Tag>未失联（IDENTITY_LOST_NOT_FOUND）</Tag>
              ) : (
                '—'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="应该在哪">
              {shouldWhere
                ? shouldWhere.curatedValue.hostName
                  ? `${shouldWhere.curatedValue.hostName} (${shouldWhere.curatedValue.hostId})`
                  : shouldWhere.curatedValue.hostId
                : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="实际在哪">
              {actualWhere ? (
                <Space direction="vertical" size={0}>
                  <Text>
                    {formatActualWhereValue(actualWhere.observedValue, actualWhere.identityLost)}
                  </Text>
                  <Text type="secondary">
                    availability={actualAvailability ?? '—'}
                    {actualIsLost ? ' · 不得展示为 PRESENT，旧宿主不是可用实际' : ''}
                  </Text>
                </Space>
              ) : (
                '—'
              )}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>
    </Space>
  );
}
