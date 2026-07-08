import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntApp,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
} from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';

import {
  adminApi,
  type AdminPermission,
  type AdminPermissionRequest,
  type AdminRole,
  type AdminRoleRequest,
  type AdminUser,
  type AdminUserRequest,
} from '../api/admin';
import { formatDate, renderEmpty } from './pageUtils';
import { ADMIN_TABLE_SCROLL_Y, EnabledControl, ErrorAlert, PageSectionHeader } from './shared';

export function IdentityPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [userForm] = Form.useForm<UserFormValues>();
  const [roleForm] = Form.useForm<RoleFormValues>();
  const [permissionForm] = Form.useForm<PermissionFormValues>();
  const [editingUser, setEditingUser] = useState<AdminUser>();
  const [editingRole, setEditingRole] = useState<AdminRole>();
  const [editingPermission, setEditingPermission] = useState<AdminPermission>();
  const [userDrawerOpen, setUserDrawerOpen] = useState(false);
  const [roleDrawerOpen, setRoleDrawerOpen] = useState(false);
  const [permissionDrawerOpen, setPermissionDrawerOpen] = useState(false);

  const usersQuery = useQuery({ queryKey: ['admin-users'], queryFn: adminApi.listAdminUsers });
  const rolesQuery = useQuery({ queryKey: ['admin-roles'], queryFn: adminApi.listAdminRoles });
  const permissionsQuery = useQuery({ queryKey: ['admin-permissions'], queryFn: adminApi.listAdminPermissions });

  const invalidateIdentity = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    void queryClient.invalidateQueries({ queryKey: ['admin-roles'] });
    void queryClient.invalidateQueries({ queryKey: ['admin-permissions'] });
  };

  const saveUserMutation = useMutation({
    mutationFn: (values: UserFormValues) => {
      const request = toUserRequest(values);
      return editingUser ? adminApi.updateAdminUser(editingUser.id, request) : adminApi.createAdminUser(request);
    },
    onSuccess: () => {
      message.success('User saved');
      setUserDrawerOpen(false);
      invalidateIdentity();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const deleteUserMutation = useMutation({
    mutationFn: adminApi.deleteAdminUser,
    onSuccess: () => {
      message.success('User deleted');
      invalidateIdentity();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Delete failed'),
  });

  const updateUserMutation = useMutation({
    mutationFn: ({ user, enabled }: { user: AdminUser; enabled: boolean }) =>
      adminApi.updateAdminUser(user.id, { ...userToRequest(user), enabled }),
    onSuccess: invalidateIdentity,
    onError: (error) => message.error(error instanceof Error ? error.message : 'Update failed'),
  });

  const saveRoleMutation = useMutation({
    mutationFn: (values: RoleFormValues) => {
      const request = toRoleRequest(values);
      return editingRole ? adminApi.updateAdminRole(editingRole.id, request) : adminApi.createAdminRole(request);
    },
    onSuccess: () => {
      message.success('Role saved');
      setRoleDrawerOpen(false);
      invalidateIdentity();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const deleteRoleMutation = useMutation({
    mutationFn: adminApi.deleteAdminRole,
    onSuccess: () => {
      message.success('Role deleted');
      invalidateIdentity();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Delete failed'),
  });

  const savePermissionMutation = useMutation({
    mutationFn: (values: PermissionFormValues) => {
      const request = toPermissionRequest(values);
      return editingPermission
        ? adminApi.updateAdminPermission(editingPermission.id, request)
        : adminApi.createAdminPermission(request);
    },
    onSuccess: () => {
      message.success('Permission saved');
      setPermissionDrawerOpen(false);
      invalidateIdentity();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const deletePermissionMutation = useMutation({
    mutationFn: adminApi.deleteAdminPermission,
    onSuccess: () => {
      message.success('Permission deleted');
      invalidateIdentity();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Delete failed'),
  });

  const openCreateUser = () => {
    setEditingUser(undefined);
    userForm.resetFields();
    userForm.setFieldsValue({ tenantId: 'default', enabled: true, roleIds: [] });
    setUserDrawerOpen(true);
  };

  const openEditUser = (user: AdminUser) => {
    setEditingUser(user);
    userForm.resetFields();
    userForm.setFieldsValue({ ...user, password: undefined });
    setUserDrawerOpen(true);
  };

  const openCreateRole = () => {
    setEditingRole(undefined);
    roleForm.resetFields();
    roleForm.setFieldsValue({ permissionIds: [] });
    setRoleDrawerOpen(true);
  };

  const openEditRole = (role: AdminRole) => {
    setEditingRole(role);
    roleForm.resetFields();
    roleForm.setFieldsValue(role);
    setRoleDrawerOpen(true);
  };

  const openCreatePermission = () => {
    setEditingPermission(undefined);
    permissionForm.resetFields();
    setPermissionDrawerOpen(true);
  };

  const openEditPermission = (permission: AdminPermission) => {
    setEditingPermission(permission);
    permissionForm.resetFields();
    permissionForm.setFieldsValue(permission);
    setPermissionDrawerOpen(true);
  };

  const roleOptions = (rolesQuery.data ?? []).map((role) => ({ label: `${role.name} (${role.code})`, value: role.id }));
  const permissionOptions = (permissionsQuery.data ?? []).map((permission) => ({
    label: `${permission.name} (${permission.code})`,
    value: permission.id,
  }));

  const userColumns: ColumnsType<AdminUser> = [
    { title: 'Username', dataIndex: 'username', width: 190 },
    { title: 'Display Name', dataIndex: 'displayName', width: 220 },
    {
      title: 'Type',
      dataIndex: 'systemUser',
      width: 110,
      render: (value) => (value ? <Tag color="gold">System</Tag> : <Tag>Custom</Tag>),
    },
    { title: 'Tenant', dataIndex: 'tenantId', width: 160 },
    {
      title: 'Roles',
      dataIndex: 'roles',
      render: (roles: AdminRole[]) => roles.map((role) => <Tag key={role.id}>{role.code}</Tag>),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      width: 170,
      render: (enabled: boolean, user) => (
        user.systemUser ? (
          <Tag color="success">Enabled</Tag>
        ) : (
          <EnabledControl
            enabled={enabled}
            loading={updateUserMutation.isPending}
            onChange={(checked) => updateUserMutation.mutate({ user, enabled: checked })}
          />
        )
      ),
    },
    { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
    {
      title: 'Actions',
      width: 170,
      fixed: 'right',
      render: (_, user) => (
        <Space>
          <Button size="small" onClick={() => openEditUser(user)}>
            Edit
          </Button>
          <Popconfirm
            title="Delete this admin user?"
            onConfirm={() => deleteUserMutation.mutate(user.id)}
            disabled={user.systemUser}
          >
            <Button size="small" danger disabled={user.systemUser}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const roleColumns: ColumnsType<AdminRole> = [
    { title: 'Code', dataIndex: 'code', width: 180, render: (value) => <Tag>{value}</Tag> },
    { title: 'Name', dataIndex: 'name', width: 220 },
    { title: 'Description', dataIndex: 'description', ellipsis: true, render: (value) => value || '-' },
    { title: 'System', dataIndex: 'systemRole', width: 100, render: (value) => (value ? 'Yes' : 'No') },
    {
      title: 'Permissions',
      dataIndex: 'permissions',
      width: 320,
      render: (permissions: AdminPermission[]) =>
        permissions.map((permission) => <Tag key={permission.id}>{permission.code}</Tag>),
    },
    { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
    {
      title: 'Actions',
      width: 170,
      fixed: 'right',
      render: (_, role) => (
        <Space>
          <Button size="small" onClick={() => openEditRole(role)}>
            Edit
          </Button>
          <Popconfirm title="Delete this admin role?" onConfirm={() => deleteRoleMutation.mutate(role.id)}>
            <Button size="small" danger disabled={role.systemRole}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const permissionColumns: ColumnsType<AdminPermission> = [
    { title: 'Code', dataIndex: 'code', width: 220, render: (value) => <Tag>{value}</Tag> },
    { title: 'Name', dataIndex: 'name', width: 240 },
    { title: 'Description', dataIndex: 'description', ellipsis: true, render: (value) => value || '-' },
    { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
    {
      title: 'Actions',
      width: 170,
      fixed: 'right',
      render: (_, permission) => (
        <Space>
          <Button size="small" onClick={() => openEditPermission(permission)}>
            Edit
          </Button>
          <Popconfirm title="Delete this permission?" onConfirm={() => deletePermissionMutation.mutate(permission.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <ErrorAlert error={usersQuery.error ?? rolesQuery.error ?? permissionsQuery.error} />
      <Card className="admin-data-card">
        <Tabs
          items={[
            {
              key: 'users',
              label: 'Users',
              children: (
                <>
                  <PageSectionHeader
                    title="User Management"
                    actions={
                      <Button type="primary" icon={<PlusOutlined />} onClick={openCreateUser}>
                        New User
                      </Button>
                    }
                  />
                  <Table
                    size="small"
                    rowKey="id"
                    loading={usersQuery.isLoading}
                    dataSource={usersQuery.data ?? []}
                    columns={userColumns}
                    locale={{ emptyText: renderEmpty('No admin users configured') }}
                    pagination={{ size: 'small' }}
                    scroll={{ x: 1230, y: ADMIN_TABLE_SCROLL_Y }}
                  />
                </>
              ),
            },
            {
              key: 'roles',
              label: 'Roles',
              children: (
                <>
                  <PageSectionHeader
                    title="Role Management"
                    actions={
                      <Button type="primary" icon={<PlusOutlined />} onClick={openCreateRole}>
                        New Role
                      </Button>
                    }
                  />
                  <Table
                    size="small"
                    rowKey="id"
                    loading={rolesQuery.isLoading}
                    dataSource={rolesQuery.data ?? []}
                    columns={roleColumns}
                    locale={{ emptyText: renderEmpty('No admin roles configured') }}
                    pagination={{ size: 'small' }}
                    scroll={{ x: 1220, y: ADMIN_TABLE_SCROLL_Y }}
                  />
                </>
              ),
            },
            {
              key: 'permissions',
              label: 'Permissions',
              children: (
                <>
                  <PageSectionHeader
                    title="Permission Management"
                    actions={
                      <Button type="primary" icon={<PlusOutlined />} onClick={openCreatePermission}>
                        New Permission
                      </Button>
                    }
                  />
                  <Table
                    size="small"
                    rowKey="id"
                    loading={permissionsQuery.isLoading}
                    dataSource={permissionsQuery.data ?? []}
                    columns={permissionColumns}
                    locale={{ emptyText: renderEmpty('No admin permissions configured') }}
                    pagination={{ size: 'small' }}
                    scroll={{ x: 920, y: ADMIN_TABLE_SCROLL_Y }}
                  />
                </>
              ),
            },
          ]}
        />
      </Card>

      <Drawer
        title={editingUser ? 'Edit User' : 'New User'}
        open={userDrawerOpen}
        width={560}
        destroyOnClose
        onClose={() => setUserDrawerOpen(false)}
        extra={
          <Button type="primary" loading={saveUserMutation.isPending} onClick={() => userForm.submit()}>
            Save
          </Button>
        }
      >
        <Form
          form={userForm}
          layout="vertical"
          initialValues={{ tenantId: 'default', enabled: true, roleIds: [] }}
          onFinish={saveUserMutation.mutate}
        >
          <Form.Item name="username" label="Username" rules={[{ required: true, max: 128 }]}>
            <Input placeholder="admin@example.com" disabled={editingUser?.systemUser} />
          </Form.Item>
          <Form.Item name="displayName" label="Display Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="Platform Admin" />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: !editingUser, max: 255 }]}>
            <Input.Password
              placeholder={editingUser ? 'Leave blank to keep existing password' : 'Set an initial password'}
            />
          </Form.Item>
          <Form.Item name="tenantId" label="Tenant ID" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="default" />
          </Form.Item>
          <Form.Item name="roleIds" label="Roles">
            <Select
              mode="multiple"
              options={roleOptions}
              loading={rolesQuery.isLoading}
              disabled={editingUser?.systemUser}
            />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch disabled={editingUser?.systemUser} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={editingRole ? 'Edit Role' : 'New Role'}
        open={roleDrawerOpen}
        width={560}
        destroyOnClose
        onClose={() => setRoleDrawerOpen(false)}
        extra={
          <Button type="primary" loading={saveRoleMutation.isPending} onClick={() => roleForm.submit()}>
            Save
          </Button>
        }
      >
        <Form
          form={roleForm}
          layout="vertical"
          initialValues={{ permissionIds: [] }}
          onFinish={saveRoleMutation.mutate}
        >
          <Form.Item name="code" label="Role Code" rules={[{ required: true, max: 64 }]}>
            <Input placeholder="SUPPORT_ADMIN" />
          </Form.Item>
          <Form.Item name="name" label="Role Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="Support Admin" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} />
          </Form.Item>
          <Form.Item name="permissionIds" label="Permissions">
            <Select mode="multiple" options={permissionOptions} loading={permissionsQuery.isLoading} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={editingPermission ? 'Edit Permission' : 'New Permission'}
        open={permissionDrawerOpen}
        width={520}
        destroyOnClose
        onClose={() => setPermissionDrawerOpen(false)}
        extra={
          <Button type="primary" loading={savePermissionMutation.isPending} onClick={() => permissionForm.submit()}>
            Save
          </Button>
        }
      >
        <Form form={permissionForm} layout="vertical" onFinish={savePermissionMutation.mutate}>
          <Form.Item name="code" label="Permission Code" rules={[{ required: true, max: 128 }]}>
            <Input placeholder="REPORTS_READ" />
          </Form.Item>
          <Form.Item name="name" label="Permission Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="Reports Read" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

type UserFormValues = AdminUserRequest;
type RoleFormValues = AdminRoleRequest;
type PermissionFormValues = AdminPermissionRequest;

function userToRequest(user: AdminUser): AdminUserRequest {
  return {
    username: user.username,
    displayName: user.displayName,
    tenantId: user.tenantId,
    enabled: user.enabled,
    roleIds: user.roleIds,
  };
}

function toUserRequest(values: UserFormValues): AdminUserRequest {
  return {
    username: values.username,
    displayName: values.displayName,
    password: values.password || undefined,
    tenantId: values.tenantId,
    enabled: values.enabled,
    roleIds: values.roleIds ?? [],
  };
}

function toRoleRequest(values: RoleFormValues): AdminRoleRequest {
  return {
    code: values.code,
    name: values.name,
    description: values.description || undefined,
    permissionIds: values.permissionIds ?? [],
  };
}

function toPermissionRequest(values: PermissionFormValues): AdminPermissionRequest {
  return {
    code: values.code,
    name: values.name,
    description: values.description || undefined,
  };
}
