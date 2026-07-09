import { PlusOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';

import { adminApi, modelTypes, type ModelConfig, type ModelConfigRequest, type ModelOption } from '../api/admin';
import { fetchAdminSession } from '../api/client';
import { parseJsonObject, renderEmpty, stringifyJson } from './pageUtils';
import { AdminSearchPanel, buildListQuery, EnabledTag, ErrorAlert, PageSectionHeader, tableSort, type AdminTableSort } from './shared';

export function ModelsPage() {
  const { message, modal } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ModelFormValues>();
  const [editingModel, setEditingModel] = useState<ModelConfig>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [filters, setFilters] = useState<Record<string, string | boolean>>({});
  const [sort, setSort] = useState<AdminTableSort>({});
  const selectedProviderId = Form.useWatch('providerId', form);
  const listQuery = buildListQuery(filters, sort);

  const providersQuery = useQuery({ queryKey: ['providers'], queryFn: adminApi.listProviders });
  const modelsQuery = useQuery({ queryKey: ['models', listQuery], queryFn: () => adminApi.listModels(listQuery) });
  const sessionQuery = useQuery({ queryKey: ['admin-session'], queryFn: fetchAdminSession, retry: false });
  const enabledProviders = (providersQuery.data ?? []).filter((provider) => provider.enabled);
  const modelOptionsQuery = useQuery({
    queryKey: ['model-options', selectedProviderId],
    queryFn: () => adminApi.listModelOptions(selectedProviderId!),
    enabled: drawerOpen && Boolean(selectedProviderId),
  });
  const invalidateModels = () => queryClient.invalidateQueries({ queryKey: ['models'] });

  const saveMutation = useMutation({
    mutationFn: (values: ModelFormValues) => {
      const request = toModelRequest(values);
      return editingModel ? adminApi.updateModel(editingModel.id, request) : adminApi.createModel(request);
    },
    onSuccess: () => {
      message.success('Model saved');
      setDrawerOpen(false);
      void invalidateModels();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteModel,
    onSuccess: () => {
      message.success('Model deleted');
      void invalidateModels();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Delete failed'),
  });

  const testMutation = useMutation({
    mutationFn: adminApi.testModelGeneration,
    onSuccess: (result) => {
      modal.info({
        title: result.success ? 'Generation succeeded' : 'Generation failed',
        content: (
          <Space direction="vertical">
            <span>{result.message}</span>
            {result.sampleText && <Input.TextArea readOnly autoSize value={result.sampleText} />}
          </Space>
        ),
      });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Generation test failed'),
  });

  const openCreate = () => {
    setEditingModel(undefined);
    form.resetFields();
    form.setFieldsValue({
      providerId: enabledProviders[0]?.id,
      modelType: 'CHAT',
      supportsStreaming: true,
      enabled: true,
      metadataJson: '{}',
    });
    setDrawerOpen(true);
  };

  const openEdit = (model: ModelConfig) => {
    setEditingModel(model);
    form.resetFields();
    form.setFieldsValue({ ...model, metadataJson: stringifyJson(model.metadata) });
    setDrawerOpen(true);
  };

  const resetModelSelection = () => {
    form.setFieldsValue({
      displayName: undefined,
      modelName: undefined,
      modelType: 'CHAT',
      maxContextTokens: undefined,
      defaultTemperature: undefined,
      defaultTopP: undefined,
      supportsStreaming: true,
      metadataJson: '{}',
    });
  };

  const applyModelOption = (modelName: string) => {
    const option = modelOptionsQuery.data?.find((item) => item.modelName === modelName);
    if (!option) {
      return;
    }

    form.setFieldsValue({
      displayName: option.displayName,
      modelName: option.modelName,
      modelType: option.modelType,
      maxContextTokens: option.maxContextTokens,
      defaultTemperature: option.defaultTemperature,
      defaultTopP: option.defaultTopP,
      supportsStreaming: option.supportsStreaming,
      metadataJson: stringifyJson({ preset: true }),
    });
  };

  const columns: ColumnsType<ModelConfig> = [
    ...(sessionQuery.data?.superAdmin ? [{ title: 'Tenant', dataIndex: 'tenantId', width: 110, sorter: true }] : []),
    { title: 'Display Name', dataIndex: 'displayName', sorter: true },
    { title: 'Provider', dataIndex: 'providerName', sorter: true },
    { title: 'Model Name', dataIndex: 'modelName', ellipsis: true, sorter: true },
    { title: 'Type', dataIndex: 'modelType', width: 105, sorter: true, render: (value) => <Tag>{value}</Tag> },
    { title: 'Context Tokens', dataIndex: 'maxContextTokens', width: 110, sorter: true, render: (value) => value ?? '-' },
    {
      title: 'Sampling',
      width: 120,
      sorter: true,
      dataIndex: 'defaultTemperature',
      render: (_, model) => `T ${model.defaultTemperature ?? '-'} / P ${model.defaultTopP ?? '-'}`,
    },
    { title: 'Streaming', dataIndex: 'supportsStreaming', width: 95, sorter: true, render: (value) => (value ? 'Yes' : 'No') },
    {
      title: 'Status',
      dataIndex: 'enabled',
      width: 72,
      sorter: true,
      render: (enabled: boolean) => <EnabledTag enabled={enabled} />,
    },
    {
      title: 'Actions',
      width: 190,
      render: (_, model) => (
        <Space>
          <Button size="small" onClick={() => openEdit(model)}>
            Edit
          </Button>
          <Button
            size="small"
            icon={<ThunderboltOutlined />}
            loading={testMutation.isPending}
            onClick={() => testMutation.mutate(model.id)}
          >
            Test
          </Button>
          <Popconfirm title="Delete this model?" onConfirm={() => deleteMutation.mutate(model.id)}>
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
      <ErrorAlert error={modelsQuery.error ?? providersQuery.error} />
      <AdminSearchPanel
        fields={[
          { name: 'search', label: 'Keyword' },
          ...(sessionQuery.data?.superAdmin ? [{ name: 'tenantId', label: 'Tenant' }] : []),
          { name: 'displayName', label: 'Display Name' },
          { name: 'providerName', label: 'Provider' },
          { name: 'modelName', label: 'Model Name' },
          { name: 'modelType', label: 'Type', type: 'select', options: modelTypes.map((type) => ({ label: type, value: type })) },
          { name: 'supportsStreaming', label: 'Streaming', type: 'select', options: [{ label: 'Yes', value: true }, { label: 'No', value: false }] },
          { name: 'enabled', label: 'Status', type: 'select', options: [{ label: 'Enabled', value: true }, { label: 'Disabled', value: false }] },
        ]}
        initialValues={filters}
        onSearch={(values) => setFilters(values as Record<string, string | boolean>)}
      />
      <Card className="admin-data-card">
        <PageSectionHeader
          title="Model Management"
          actions={
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!enabledProviders.length}>
              New Model
            </Button>
          }
        />
        <Table
          size="small"
          rowKey="id"
          loading={modelsQuery.isLoading || providersQuery.isLoading}
          dataSource={modelsQuery.data ?? []}
          columns={columns}
          locale={{ emptyText: renderEmpty(enabledProviders.length ? 'No models configured' : 'Enable a provider first') }}
          pagination={{ size: 'small' }}
          onChange={(_, __, sorter) => setSort(tableSort(sorter))}
        />
      </Card>
      <Drawer
        title={editingModel ? 'Edit Model' : 'New Model'}
        open={drawerOpen}
        width={620}
        destroyOnClose
        onClose={() => setDrawerOpen(false)}
        extra={
          <Button type="primary" loading={saveMutation.isPending} onClick={() => form.submit()}>
            Save
          </Button>
        }
      >
        <Form form={form} layout="vertical" onFinish={saveMutation.mutate} initialValues={{ modelType: 'CHAT', enabled: true }}>
          <Form.Item name="providerId" label="Provider" rules={[{ required: true }]}>
            <Select
              options={enabledProviders.map((provider) => ({ label: provider.name, value: provider.id }))}
              showSearch
              optionFilterProp="label"
              onChange={resetModelSelection}
            />
          </Form.Item>
          <Form.Item name="displayName" label="Display Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="GPT 4.1 Mini" />
          </Form.Item>
          <Form.Item name="modelName" label="Model Name" rules={[{ required: true, max: 200 }]}>
            <Select
              loading={modelOptionsQuery.isFetching}
              disabled={!selectedProviderId}
              options={(modelOptionsQuery.data ?? []).map(toModelOptionSelectItem)}
              showSearch
              optionFilterProp="label"
              placeholder="Select a model"
              notFoundContent={selectedProviderId ? 'No model options for this provider' : 'Select a provider first'}
              onChange={applyModelOption}
            />
          </Form.Item>
          {modelOptionsQuery.isError && (
            <Alert
              type="error"
              showIcon
              message="Unable to load models from provider API"
              description={modelOptionsQuery.error instanceof Error ? modelOptionsQuery.error.message : 'Provider model discovery failed'}
            />
          )}
          <Form.Item name="modelType" label="Model Type" rules={[{ required: true }]}>
            <Select options={modelTypes.map((type) => ({ label: type, value: type }))} />
          </Form.Item>
          <Form.Item name="maxContextTokens" label="Max Context Tokens" rules={[{ type: 'number', min: 1 }]}>
            <InputNumber min={1} precision={0} className="full-width-control" />
          </Form.Item>
          <Space className="form-row" align="start">
            <Form.Item name="defaultTemperature" label="Temperature" rules={[{ type: 'number', min: 0, max: 2 }]}>
              <InputNumber min={0} max={2} step={0.1} className="full-width-control" />
            </Form.Item>
            <Form.Item name="defaultTopP" label="Top P" rules={[{ type: 'number', min: 0, max: 1 }]}>
              <InputNumber min={0} max={1} step={0.05} className="full-width-control" />
            </Form.Item>
          </Space>
          <Form.Item name="supportsStreaming" label="Supports Streaming" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="metadataJson" label="Metadata JSON">
            <Input.TextArea rows={5} className="json-editor" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

interface ModelFormValues extends Omit<ModelConfigRequest, 'metadata'> {
  metadataJson?: string;
}

function toModelRequest(values: ModelFormValues): ModelConfigRequest {
  return {
    providerId: values.providerId,
    displayName: values.displayName,
    modelName: values.modelName,
    modelType: values.modelType,
    maxContextTokens: values.maxContextTokens,
    defaultTemperature: values.defaultTemperature,
    defaultTopP: values.defaultTopP,
    supportsStreaming: values.supportsStreaming,
    enabled: values.enabled,
    metadata: parseJsonObject(values.metadataJson ?? '{}', 'Metadata'),
  };
}

function toModelOptionSelectItem(option: ModelOption) {
  return {
    label: `${option.displayName} (${option.modelName})`,
    value: option.modelName,
  };
}