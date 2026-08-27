import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { listActiveConflicts } from '../api/conflicts';
import { getShouldWhere, type ShouldWhere } from '../api/curated';
import { getActualWhere, getIdentityLost, listUnboundCandidates } from '../api/observed';
import type { ActualWhere, ConflictCase, IdentityLostMark, UnboundCandidate } from '../api/types';
import { ApiError } from '../api/types';
import { useDemoUser } from '../auth/DemoUserContext';
import { formatActualWhereValue, formatUnboundLabels } from '../util/format';

const { Title, Paragraph, Text } = Typography;

const REASON_COLOR: Record<string, string> = {
  MISSING_LABEL: 'gold',
  UNKNOWN_OBJECT_ID: 'purple',
};

function envelopeMessage(err: unknown): string {
  return err instanceof ApiError ? `${err.code}: ${err.message}` : String(err);
}

export default function UnboundCandidatesPage() {
  const { userId } = useDemoUser();
  const [rows, setRows] = useState<UnboundCandidate[]>([]);
  const [lostConflicts, setLostConflicts] = useState<ConflictCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  useEffect(() => {
    void load();
  }, [load]);

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
