import { Layout, Select, Space, Typography } from 'antd';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { DEMO_USERS, useDemoUser } from './auth/DemoUserContext';
import ConflictDetailPage from './pages/ConflictDetailPage';
import ConflictListPage from './pages/ConflictListPage';
import UnboundCandidatesPage from './pages/UnboundCandidatesPage';

const { Header, Content } = Layout;
const { Title, Text } = Typography;

export default function App() {
  const { userId, user, loading, setUserId } = useDemoUser();

  return (
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          background: '#1f2a24',
          paddingInline: 24,
        }}
      >
        <Space size="middle">
          <Link to="/" style={{ textDecoration: 'none' }}>
            <Title level={3} style={{ color: '#e8f0ea', margin: 0, letterSpacing: '0.04em' }}>
              ArchOps
            </Title>
          </Link>
          <Text style={{ color: '#9bb59f' }}>关系真相 · 竖切演示</Text>
          <Link to="/" style={{ color: '#c5d6c8' }}>
            开放冲突
          </Link>
          <Link to="/unbound" style={{ color: '#c5d6c8' }}>
            未绑定 / 身份失联
          </Link>
        </Space>
        <Space>
          <Text style={{ color: '#c5d6c8' }}>演示身份</Text>
          <Select
            value={userId}
            onChange={setUserId}
            style={{ width: 220 }}
            options={DEMO_USERS.map((u) => ({ value: u.id, label: u.label }))}
          />
          {!loading && user && (
            <Text style={{ color: '#9bb59f' }}>
              {user.displayName} · {user.roleLabel}
            </Text>
          )}
        </Space>
      </Header>
      <Content style={{ padding: 24, maxWidth: 1080, margin: '0 auto', width: '100%' }}>
        <Routes>
          <Route path="/" element={<ConflictListPage />} />
          <Route path="/conflicts/:id" element={<ConflictDetailPage />} />
          <Route path="/unbound" element={<UnboundCandidatesPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Content>
    </Layout>
  );
}
