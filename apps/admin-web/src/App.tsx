import {
  ApiOutlined,
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  LogoutOutlined,
  MessageOutlined,
  SafetyCertificateOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu, Select, Space, Typography, message } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { create } from 'zustand';

import { adminApi } from './api/admin';
import { fetchAdminSession, logoutAdmin, readSelectedAdminTenantId, writeSelectedAdminTenantId } from './api/client';

const { Content, Sider } = Layout;
const { Text } = Typography;

interface AdminUiState {
  collapsed: boolean;
  setCollapsed: (collapsed: boolean) => void;
}

interface RouteItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  title: string;
  group?: string;
  superAdminOnly?: boolean;
}

const useAdminUi = create<AdminUiState>((set) => ({
  collapsed: false,
  setCollapsed: (collapsed) => set({ collapsed }),
}));

const routeItems: RouteItem[] = [
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
    group: 'AI Config',
  },
  {
    key: '/models',
    icon: <AppstoreOutlined />,
    label: 'Models',
    title: 'Model Management',
    group: 'AI Config',
  },
  {
    key: '/chatbots',
    icon: <RobotOutlined />,
    label: 'Chatbots',
    title: 'Chatbot Management',
    group: 'Text Chat',
  },
  {
    key: '/evals',
    icon: <ExperimentOutlined />,
    label: 'Evaluations',
    title: 'Evaluations',
    group: 'Text Chat',
  },
  {
    key: '/tenants',
    icon: <ApartmentOutlined />,
    label: 'Tenants',
    title: 'Tenant Management',
    group: 'System',
    superAdminOnly: true,
  },
  {
    key: '/identity',
    icon: <SafetyCertificateOutlined />,
    label: 'Users & Roles',
    title: 'Users & Roles',
    group: 'System',
  },
  {
    key: '/audit-logs',
    icon: <AuditOutlined />,
    label: 'Audit Logs',
    title: 'Audit Logs',
    group: 'System',
  },
];

function createMenuItems(visibleRouteItems: RouteItem[]) {
  return [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: <Link to="/dashboard">Dashboard</Link>,
    },
    {
      key: 'ai-configuration',
      icon: <AppstoreOutlined />,
      label: 'AI Config',
      children: visibleRouteItems
        .filter((item) => item.group === 'AI Config')
        .map((item) => ({
          key: item.key,
          icon: item.icon,
          label: <Link to={item.key}>{item.label}</Link>,
        })),
    },
    {
      key: 'text-chat',
      icon: <MessageOutlined />,
      label: 'Text Chat',
      children: visibleRouteItems
        .filter((item) => item.group === 'Text Chat')
        .map((item) => ({
          key: item.key,
          icon: item.icon,
          label: <Link to={item.key}>{item.label}</Link>,
        })),
    },
    {
      key: 'system',
      icon: <SafetyCertificateOutlined />,
      label: 'System',
      children: visibleRouteItems
        .filter((item) => item.group === 'System')
        .map((item) => ({
          key: item.key,
          icon: item.icon,
          label: <Link to={item.key}>{item.label}</Link>,
        })),
    },
  ];
}

const sectionOpenKeysByRoute: Record<string, string> = {
  '/providers': 'ai-configuration',
  '/models': 'ai-configuration',
  '/chatbots': 'text-chat',
  '/evals': 'text-chat',
  '/tenants': 'system',
  '/identity': 'system',
  '/audit-logs': 'system',
};

export function App() {
  const { collapsed, setCollapsed } = useAdminUi();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const [loggingOut, setLoggingOut] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState(readSelectedAdminTenantId());

  const sessionQuery = useQuery({ queryKey: ['admin-session'], queryFn: fetchAdminSession, retry: false });
  const tenantsQuery = useQuery({
    queryKey: ['tenants'],
    queryFn: adminApi.listTenants,
    enabled: Boolean(sessionQuery.data?.superAdmin),
  });

  const visibleRouteItems = routeItems.filter((item) => !item.superAdminOnly || sessionQuery.data?.superAdmin);
  const menuItems = createMenuItems(visibleRouteItems);
  const currentRoute = routeItems.find((item) => location.pathname.startsWith(item.key)) ?? routeItems[0];

  useEffect(() => {
    if (collapsed) {
      setOpenKeys([]);
      return;
    }

    const sectionKey = sectionOpenKeysByRoute[currentRoute.key];
    setOpenKeys(sectionKey ? [sectionKey] : []);
  }, [collapsed, currentRoute.key]);

  useEffect(() => {
    if (!sessionQuery.data?.superAdmin || selectedTenantId || !sessionQuery.data.tenantId) {
      return;
    }
    writeSelectedAdminTenantId(sessionQuery.data.tenantId);
    setSelectedTenantId(sessionQuery.data.tenantId);
  }, [selectedTenantId, sessionQuery.data]);

  useEffect(() => {
    if (!sessionQuery.data?.superAdmin || !tenantsQuery.data?.length) {
      return;
    }
    const tenantExists = tenantsQuery.data.some((tenant) => tenant.tenantId === selectedTenantId);
    if (!tenantExists) {
      const nextTenantId = sessionQuery.data.tenantId ?? tenantsQuery.data[0].tenantId;
      writeSelectedAdminTenantId(nextTenantId);
      setSelectedTenantId(nextTenantId);
    }
  }, [selectedTenantId, sessionQuery.data, tenantsQuery.data]);

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

  function handleTenantChange(tenantId: string) {
    writeSelectedAdminTenantId(tenantId);
    setSelectedTenantId(tenantId);
    void queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'admin-session' });
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
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[currentRoute.key]}
          openKeys={openKeys}
          onOpenChange={(keys) => setOpenKeys(keys as string[])}
          items={menuItems}
        />
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
        {sessionQuery.data?.superAdmin && (
          <div className="admin-topbar">
            <Space size="small">
              <Text type="secondary">Tenant</Text>
              <Select
                className="tenant-select"
                value={selectedTenantId ?? sessionQuery.data.tenantId}
                loading={tenantsQuery.isLoading}
                options={(tenantsQuery.data ?? []).map((tenant) => ({
                  label: `${tenant.name} (${tenant.tenantId})`,
                  value: tenant.tenantId,
                }))}
                onChange={handleTenantChange}
              />
            </Space>
          </div>
        )}
        <Content className="admin-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
