import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { listUnboundCandidates } from '../api/observed';
import type { UnboundCandidate } from '../api/types';
import { ApiError } from '../api/types';
import { useDemoUser } from '../auth/DemoUserContext';
import { formatUnboundLabels } from '../util/format';

const { Title, Paragraph, Text } = Typography;

const REASON_COLOR: Record<string, string> = {
  MISSING_LABEL: 'gold',
  UNKNOWN_OBJECT_ID: 'purple',
};

export default function UnboundCandidatesPage() {
  const { userId } = useDemoUser();
  const [rows, setRows] = useState<UnboundCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listUnboundCandidates();
      setRows(data);
    } catch (err) {
      const msg = err instanceof ApiError ? `${err.code}: ${err.message}` : String(err);
      setError(msg);
      setRows([]);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    void load();
  }, [load]);

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
            <Text>
              archops.object_id={objectId ? objectId : '（无）'}
            </Text>
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
    </Space>
  );
}
