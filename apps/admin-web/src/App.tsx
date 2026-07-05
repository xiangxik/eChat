import {
  ApiOutlined,
  AppstoreOutlined,
  DashboardOutlined,
  LogoutOutlined,
  MessageOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu, Space, theme, Typography, message } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { create } from 'zustand';

import { logoutAdmin } from './api/client';

const { Header, Content, Sider } = Layout;
const { Text, Title } = Typography;

interface AdminUiState {
  collapsed: boolean;
  setCollapsed: (collapsed: boolean) => void;
}

const useAdminUi = create<AdminUiState>((set) => ({
  collapsed: false,
  setCollapsed: (collapsed) => set({ collapsed }),
}));

const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: <Link to="/dashboard">Dashboard</Link> },
  { key: '/providers', icon: <ApiOutlined />, label: <Link to="/providers">Providers</Link> },
  { key: '/models', icon: <AppstoreOutlined />, label: <Link to="/models">Models</Link> },
  { key: '/chatbots', icon: <RobotOutlined />, label: <Link to="/chatbots">Chatbots</Link> },
  { key: '/context-policies', icon: <MessageOutlined />, label: <Link to="/context-policies">Context Policies</Link> },
];

export function App() {
  const { collapsed, setCollapsed } = useAdminUi();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const [loggingOut, setLoggingOut] = useState(false);
  const {
    token: { colorBgContainer },
  } = theme.useToken();

  const selectedKey = menuItems.find((item) => location.pathname.startsWith(item.key))?.key ?? '/dashboard';

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logoutAdmin();
      queryClient.clear();
      navigate('/login', { replace: true });
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Logout failed');
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <Layout className="admin-shell">
      {contextHolder}
      <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={252}>
        <div className="brand">
          <MessageOutlined />
          {!collapsed && <span>eChat Admin</span>}
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={menuItems} />
      </Sider>
      <Layout>
        <Header className="admin-header" style={{ background: colorBgContainer }}>
          <div>
            <Title level={3}>Chatbot Configuration</Title>
            <Text type="secondary">Operational controls for providers, models, chatbots, and context policies.</Text>
          </div>
          <Space className="admin-session-control">
            <Text type="secondary">
              <UserOutlined /> Admin signed in
            </Text>
            <Button icon={<LogoutOutlined />} loading={loggingOut} onClick={handleLogout}>
              Log out
            </Button>
          </Space>
        </Header>
        <Content className="admin-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}