import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
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
  Tag,
} from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';

import { adminApi, providerTypes, type ProviderConfig, type ProviderConfigRequest } from '../api/admin';
import { formatDate, renderEmpty } from './pageUtils';
import { EnabledTag, ErrorAlert } from './shared';

export function ProvidersPage() {
  const { message, modal } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ProviderFormValues>();
  const [editingProvider, setEditingProvider] = useState<ProviderConfig>();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const providersQuery = useQuery({ queryKey: ['providers'], queryFn: adminApi.listProviders });
  const invalidateRelatedConfigs = () => {
    void queryClient.invalidateQueries({ queryKey: ['providers'] });
    void queryClient.invalidateQueries({ queryKey: ['models'] });
  };

  const saveMutation = useMutation({
    mutationFn: (values: ProviderFormValues) => {
      const request = toProviderRequest(values);
      return editingProvider
        ? adminApi.updateProvider(editingProvider.id, request)
        : adminApi.createProvider(request);
    },
    onSuccess: () => {
      message.success('Provider saved');
      setDrawerOpen(false);
      invalidateRelatedConfigs();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteProvider,
    onSuccess: () => {
      message.success('Provider deleted');
      invalidateRelatedConfigs();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Delete failed'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ provider, enabled }: { provider: ProviderConfig; enabled: boolean }) =>
      adminApi.updateProvider(provider.id, { ...providerToRequest(provider), enabled }),
    onSuccess: invalidateRelatedConfigs,
    onError: (error) => message.error(error instanceof Error ? error.message : 'Update failed'),
  });

  const testMutation = useMutation({
    mutationFn: adminApi.testProviderConnection,
    onSuccess: (result) => {
      modal.info({
        title: result.success ? 'Connection succeeded' : 'Connection failed',
        content: `${result.message}${result.statusCode ? ` (HTTP ${result.statusCode})` : ''}`,
      });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Connection test failed'),
  });

  const openCreate = () => {
    setEditingProvider(undefined);
    form.resetFields();
    form.setFieldsValue({ type: 'OPENAI_COMPATIBLE', enabled: true });
    setDrawerOpen(true);
  };

  const openEdit = (provider: ProviderConfig) => {
    setEditingProvider(provider);
    form.resetFields();
    form.setFieldsValue({ ...provider, apiKey: undefined });
    setDrawerOpen(true);
  };

  const columns: ColumnsType<ProviderConfig> = [
    { title: 'Name', dataIndex: 'name', width: 220 },
    { title: 'Type', dataIndex: 'type', width: 180, render: (value) => <Tag>{value}</Tag> },
    { title: 'Base URL', dataIndex: 'baseUrl', ellipsis: true, render: (value) => value || '-' },
    {
      title: 'API Key',
      dataIndex: 'hasApiKey',
      width: 130,
      render: (hasApiKey: boolean) => <Tag color={hasApiKey ? 'processing' : 'default'}>{hasApiKey ? 'Stored' : 'Not set'}</Tag>,
    },
    { title: 'Status', dataIndex: 'enabled', width: 130, render: (enabled) => <EnabledTag enabled={enabled} /> },
    {
      title: 'Enabled',
      dataIndex: 'enabled',
      width: 110,
      render: (enabled: boolean, provider) => (
        <Switch checked={enabled} onChange={(checked) => updateMutation.mutate({ provider, enabled: checked })} />
      ),
    },
    { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
    {
      title: 'Actions',
      width: 260,
      fixed: 'right',
      render: (_, provider) => (
        <Space>
          <Button size="small" onClick={() => openEdit(provider)}>
            Edit
          </Button>
          <Button size="small" icon={<ReloadOutlined />} loading={testMutation.isPending} onClick={() => testMutation.mutate(provider.id)}>
            Test
          </Button>
          <Popconfirm title="Delete this provider?" onConfirm={() => deleteMutation.mutate(provider.id)}>
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
      <ErrorAlert error={providersQuery.error} />
      <Card
        title="Provider Management"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            New Provider
          </Button>
        }
      >
        <Table
          rowKey="id"
          loading={providersQuery.isLoading}
          dataSource={providersQuery.data ?? []}
          columns={columns}
          locale={{ emptyText: renderEmpty('No providers configured') }}
          scroll={{ x: 1120 }}
        />
      </Card>
      <Drawer
        title={editingProvider ? 'Edit Provider' : 'New Provider'}
        open={drawerOpen}
        width={560}
        destroyOnClose
        onClose={() => setDrawerOpen(false)}
        extra={
          <Button type="primary" loading={saveMutation.isPending} onClick={() => form.submit()}>
            Save
          </Button>
        }
      >
        <Form form={form} layout="vertical" initialValues={{ type: 'OPENAI_COMPATIBLE', enabled: true }} onFinish={saveMutation.mutate}>
          <Form.Item name="name" label="Provider Name" rules={[{ required: true, max: 128 }]}>
            <Input placeholder="OpenAI compatible gateway" />
          </Form.Item>
          <Form.Item name="type" label="Provider Type" rules={[{ required: true }]}>
            <Select options={providerTypes.map((type) => ({ label: type, value: type }))} />
          </Form.Item>
          <Form.Item name="baseUrl" label="Base URL" rules={[{ type: 'url', warningOnly: true }, { max: 1024 }]}>
            <Input placeholder="https://api.example.com/v1" />
          </Form.Item>
          <Form.Item name="apiKey" label="API Key" rules={[{ max: 8192 }]}>
            <Input.Password placeholder={editingProvider?.hasApiKey ? 'Leave blank to keep existing API key' : 'sk-...'} />
          </Form.Item>
          {editingProvider?.hasApiKey && <Tag color="processing">Existing API key is stored and never shown in plaintext.</Tag>}
          <Form.Item name="apiKeySecretRef" label="API Key Secret Ref" rules={[{ max: 512 }]}>
            <Input placeholder="vault://secret/path" />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

type ProviderFormValues = ProviderConfigRequest;

function providerToRequest(provider: ProviderConfig): ProviderConfigRequest {
  return {
    name: provider.name,
    type: provider.type,
    baseUrl: provider.baseUrl,
    apiKeySecretRef: provider.apiKeySecretRef,
    enabled: provider.enabled,
  };
}

function toProviderRequest(values: ProviderFormValues): ProviderConfigRequest {
  return {
    name: values.name,
    type: values.type,
    baseUrl: values.baseUrl || undefined,
    apiKeySecretRef: values.apiKeySecretRef || undefined,
    apiKey: values.apiKey || undefined,
    enabled: values.enabled,
  };
}