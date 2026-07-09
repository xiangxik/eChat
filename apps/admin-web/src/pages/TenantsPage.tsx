import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntApp, Button, Card, Drawer, Form, Input, Result, Switch, Table, Tag } from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';

import { adminApi, type Tenant, type TenantRequest } from '../api/admin';
import { fetchAdminSession } from '../api/client';
import { formatDate, renderEmpty } from './pageUtils';
import { AdminSearchPanel, buildListQuery, EnabledTag, ErrorAlert, PageSectionHeader, tableSort, type AdminTableSort } from './shared';

export function TenantsPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [tenantForm] = Form.useForm<TenantFormValues>();
  const [tenantDrawerOpen, setTenantDrawerOpen] = useState(false);
  const [filters, setFilters] = useState<Record<string, string | boolean>>({});
  const [sort, setSort] = useState<AdminTableSort>({});
  const listQuery = buildListQuery(filters, sort);

  const sessionQuery = useQuery({ queryKey: ['admin-session'], queryFn: fetchAdminSession, retry: false });
  const isSuperAdmin = Boolean(sessionQuery.data?.superAdmin);
  const tenantsQuery = useQuery({ queryKey: ['tenants', listQuery], queryFn: () => adminApi.listTenants(listQuery), enabled: isSuperAdmin });

  const saveTenantMutation = useMutation({
    mutationFn: (values: TenantFormValues) => adminApi.createTenant(toTenantRequest(values)),
    onSuccess: () => {
      message.success('Tenant created');
      setTenantDrawerOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['tenants'] });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const openCreateTenant = () => {
    tenantForm.resetFields();
    tenantForm.setFieldsValue({ enabled: true });
    setTenantDrawerOpen(true);
  };

  const tenantColumns: ColumnsType<Tenant> = [
    { title: 'Tenant ID', dataIndex: 'tenantId', width: 180, sorter: true, render: (value) => <Tag>{value}</Tag> },
    { title: 'Name', dataIndex: 'name', sorter: true },
    {
      title: 'Status',
      dataIndex: 'enabled',
      width: 72,
      sorter: true,
      render: (enabled: boolean) => <EnabledTag enabled={enabled} />,
    },
    { title: 'Created', dataIndex: 'createdAt', width: 155, sorter: true, render: formatDate },
    { title: 'Updated', dataIndex: 'updatedAt', width: 155, sorter: true, render: formatDate },
  ];

  if (!sessionQuery.isLoading && !isSuperAdmin) {
    return (
      <Card className="admin-data-card">
        <Result status="403" title="Tenant management requires super administrator access" />
      </Card>
    );
  }

  return (
    <div className="page-stack">
      <ErrorAlert error={sessionQuery.error ?? tenantsQuery.error} />
      <AdminSearchPanel
        fields={[
          { name: 'search', label: 'Keyword' },
          { name: 'tenantId', label: 'Tenant ID' },
          { name: 'name', label: 'Name' },
          { name: 'enabled', label: 'Status', type: 'select', options: [{ label: 'Enabled', value: true }, { label: 'Disabled', value: false }] },
        ]}
        initialValues={filters}
        onSearch={(values) => setFilters(values as Record<string, string | boolean>)}
      />
      <Card className="admin-data-card">
        <PageSectionHeader
          title="Tenant Management"
          actions={
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateTenant} disabled={!isSuperAdmin}>
              New Tenant
            </Button>
          }
        />
        <Table
          size="small"
          rowKey="id"
          loading={sessionQuery.isLoading || tenantsQuery.isLoading}
          dataSource={tenantsQuery.data ?? []}
          columns={tenantColumns}
          locale={{ emptyText: renderEmpty('No tenants configured') }}
          pagination={{ size: 'small' }}
          onChange={(_, __, sorter) => setSort(tableSort(sorter))}
        />
      </Card>

      <Drawer
        title="New Tenant"
        open={tenantDrawerOpen}
        width={520}
        destroyOnClose
        onClose={() => setTenantDrawerOpen(false)}
        extra={
          <Button type="primary" loading={saveTenantMutation.isPending} onClick={() => tenantForm.submit()}>
            Save
          </Button>
        }
      >
        <Form form={tenantForm} layout="vertical" initialValues={{ enabled: true }} onFinish={saveTenantMutation.mutate}>
          <Form.Item name="tenantId" label="Tenant ID" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="customer-a" />
          </Form.Item>
          <Form.Item name="name" label="Tenant Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="Customer A" />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

type TenantFormValues = TenantRequest;

function toTenantRequest(values: TenantFormValues): TenantRequest {
  return {
    tenantId: values.tenantId,
    name: values.name,
    enabled: values.enabled,
  };
}