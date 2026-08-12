import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Link } from 'react-router-dom';
import { listActiveConflicts } from '../api/conflicts';
import type { ConflictCase } from '../api/types';
import { ApiError } from '../api/types';
import { useDemoUser } from '../auth/DemoUserContext';
import { formatTrack } from '../util/format';

const { Title, Paragraph, Text } = Typography;

const STATUS_COLOR: Record<string, string> = {
  OPEN: 'orange',
  PENDING_CLOSE: 'blue',
  SUSPENDED: 'default',
  CLOSED: 'green',
};

export default function ConflictListPage() {
  const { userId } = useDemoUser();
  const [rows, setRows] = useState<ConflictCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listActiveConflicts();
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

  const columns: ColumnsType<ConflictCase> = [
    {
      title: '对象',
      key: 'subject',
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Text strong>{row.subject.name ?? row.mergeKey.subjectId}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {row.mergeKey.relationLabel}
          </Text>
        </Space>
      ),
    },
    {
      title: '应该（策展）',
      key: 'curated',
      render: (_, row) => formatTrack(row.curatedValue),
    },
    {
      title: '实际（观测）',
      key: 'observed',
      render: (_, row) =>
        row.observationHollow ? (
          <Text type="secondary">空洞（不可信）</Text>
        ) : (
          formatTrack(row.observedValue)
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: string) => <Tag color={STATUS_COLOR[status] ?? 'default'}>{status}</Tag>,
    },
    {
      title: '诊断',
      dataIndex: 'diagnosisStatus',
      key: 'diagnosisStatus',
      width: 100,
    },
    {
      title: '',
      key: 'actions',
      width: 88,
      render: (_, row) => <Link to={`/conflicts/${row.id}`}>打开</Link>,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>
            开放冲突
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            双轨偏差列表。空洞 ≠ 冲突；待确认关闭仍出现在此表。
          </Paragraph>
        </div>
        <Button onClick={() => void load()} loading={loading}>
          刷新
        </Button>
      </div>
      {error && <Alert type="error" showIcon message={error} />}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '暂无开放冲突' }}
      />
    </Space>
  );
}
