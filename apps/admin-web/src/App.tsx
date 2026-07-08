import {
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  LogoutOutlined,
  MessageOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu, Space, Typography, message } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { create } from 'zustand';

import { logoutAdmin } from './api/client';

const { Content, Sider } = Layout;
const { Text } = Typography;

interface AdminUiState {
  collapsed: boolean;
  setCollapsed: (collapsed: boolean) => void;
}

const useAdminUi = create<AdminUiState>((set) => ({
  collapsed: false,
  setCollapsed: (collapsed) => set({ collapsed }),
}));

const routeItems = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: 'Dashboard',
    title: 'Dashboard',
  },
  {
    key: '/providers',
    icon: <ApiOutlined />,
    label: 'Providers',
    title: 'Provider Management',
  },
  {
    key: '/models',
    icon: <AppstoreOutlined />,
    label: 'Models',
    title: 'Model Management',
  },
  {
    key: '/chatbots',
    icon: <RobotOutlined />,
    label: 'Chatbots',
    title: 'Chatbot Management',
  },
  {
    key: '/context-policies',
    icon: <MessageOutlined />,
    label: 'Context Policies',
    title: 'Context Policies',
  },
  {
    key: '/evals',
    icon: <ExperimentOutlined />,
    label: 'Eval Harness',
    title: 'Eval Harness',
  },
  {
    key: '/audit-logs',
    icon: <AuditOutlined />,
    label: 'Audit Logs',
    title: 'Audit Logs',
  },
];

const menuItems = routeItems.map((item) => ({
  key: item.key,
  icon: item.icon,
  label: <Link to={item.key}>{item.label}</Link>,
}));

export function App() {
  const { collapsed, setCollapsed } = useAdminUi();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const [loggingOut, setLoggingOut] = useState(false);

  const currentRoute = routeItems.find((item) => location.pathname.startsWith(item.key)) ?? routeItems[0];

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
      <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={200}>
        <div className="brand">
          <span className="brand-mark">
            <MessageOutlined />
          </span>
          {!collapsed && (
            <span className="brand-copy">
              <span>eChat</span>
              <Text>Admin Console</Text>
            </span>
          )}
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[currentRoute.key]} items={menuItems} />
        <div className="admin-sidebar-footer">
          {!collapsed && (
            <Text className="admin-sidebar-status">
              <UserOutlined /> Admin signed in
            </Text>
          )}
          <Button block={!collapsed} icon={<LogoutOutlined />} loading={loggingOut} onClick={handleLogout}>
            {!collapsed && 'Log out'}
          </Button>
        </div>
      </Sider>
      <Layout>
        <Content className="admin-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
