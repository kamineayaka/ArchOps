import { useEffect, useState } from 'react';
import { Alert, Layout, Spin, Typography } from 'antd';

const { Header, Content } = Layout;
const { Title, Paragraph, Text } = Typography;

type HealthPayload = {
  success: boolean;
  code: string;
  message: string;
  data?: { status?: string };
};

export default function App() {
  const [loading, setLoading] = useState(true);
  const [health, setHealth] = useState<HealthPayload | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch('/api/health');
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const body = (await res.json()) as HealthPayload;
        if (!cancelled) {
          setHealth(body);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : String(err));
          setHealth(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center' }}>
        <Title level={3} style={{ color: '#fff', margin: 0 }}>
          ArchOps
        </Title>
      </Header>
      <Content style={{ padding: 24, maxWidth: 720, margin: '0 auto', width: '100%' }}>
        <Paragraph type="secondary">Scaffold health check (ADR-0043). Vertical-slice APIs come later.</Paragraph>
        {loading && <Spin tip="Calling /api/health…" />}
        {!loading && error && (
          <Alert type="error" showIcon message="Health check failed" description={error} />
        )}
        {!loading && health && (
          <Alert
            type={health.success && health.data?.status === 'UP' ? 'success' : 'warning'}
            showIcon
            message="API health"
            description={
              <Text code>
                {JSON.stringify(health)}
              </Text>
            }
          />
        )}
      </Content>
    </Layout>
  );
}
