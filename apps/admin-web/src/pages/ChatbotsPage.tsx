import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntApp, Button, Card, Drawer, Form, Input, Popconfirm, Select, Space, Switch, Table } from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';

import { adminApi, type ChatbotConfig, type ChatbotConfigRequest } from '../api/admin';
import { formatDate, renderEmpty } from './pageUtils';
import { EnabledTag, ErrorAlert } from './shared';

export function ChatbotsPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ChatbotConfigRequest>();
  const [editingChatbot, setEditingChatbot] = useState<ChatbotConfig>();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const chatbotsQuery = useQuery({ queryKey: ['chatbots'], queryFn: adminApi.listChatbots });
  const modelsQuery = useQuery({ queryKey: ['models'], queryFn: adminApi.listModels });
  const policiesQuery = useQuery({ queryKey: ['context-policies'], queryFn: adminApi.listContextPolicies });
  const invalidateChatbots = () => queryClient.invalidateQueries({ queryKey: ['chatbots'] });

  const saveMutation = useMutation({
    mutationFn: (values: ChatbotConfigRequest) =>
      editingChatbot ? adminApi.updateChatbot(editingChatbot.id, normalizeChatbot(values)) : adminApi.createChatbot(normalizeChatbot(values)),
    onSuccess: () => {
      message.success('Chatbot saved');
      setDrawerOpen(false);
      void invalidateChatbots();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteChatbot,
    onSuccess: () => {
      message.success('Chatbot deleted');
      void invalidateChatbots();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Delete failed'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ chatbot, enabled }: { chatbot: ChatbotConfig; enabled: boolean }) =>
      adminApi.updateChatbot(chatbot.id, normalizeChatbot({ ...chatbot, enabled })),
    onSuccess: () => void invalidateChatbots(),
    onError: (error) => message.error(error instanceof Error ? error.message : 'Update failed'),
  });

  const modelNameById = new Map((modelsQuery.data ?? []).map((model) => [model.id, model.displayName]));
  const policyNameById = new Map((policiesQuery.data ?? []).map((policy) => [policy.id, policy.name]));

  const openCreate = () => {
    setEditingChatbot(undefined);
    form.resetFields();
    form.setFieldsValue({ enabled: true, defaultModelId: undefined, contextPolicyId: undefined });
    setDrawerOpen(true);
  };

  const openEdit = (chatbot: ChatbotConfig) => {
    setEditingChatbot(chatbot);
    form.resetFields();
    form.setFieldsValue(chatbot);
    setDrawerOpen(true);
  };

  const columns: ColumnsType<ChatbotConfig> = [
    { title: 'Name', dataIndex: 'name', width: 220 },
    { title: 'Description', dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: 'Default Model',
      dataIndex: 'defaultModelId',
      width: 220,
      render: (id?: number) => (id ? modelNameById.get(id) ?? `#${id}` : '-'),
    },
    {
      title: 'Context Policy',
      dataIndex: 'contextPolicyId',
      width: 220,
      render: (id?: number) => (id ? policyNameById.get(id) ?? `#${id}` : '-'),
    },
    { title: 'Status', dataIndex: 'enabled', width: 130, render: (enabled) => <EnabledTag enabled={enabled} /> },
    {
      title: 'Enabled',
      dataIndex: 'enabled',
      width: 110,
      render: (enabled: boolean, chatbot) => (
        <Switch checked={enabled} onChange={(checked) => updateMutation.mutate({ chatbot, enabled: checked })} />
      ),
    },
    { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
    {
      title: 'Actions',
      width: 180,
      fixed: 'right',
      render: (_, chatbot) => (
        <Space>
          <Button size="small" onClick={() => openEdit(chatbot)}>
            Edit
          </Button>
          <Popconfirm title="Delete this chatbot?" onConfirm={() => deleteMutation.mutate(chatbot.id)}>
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
      <ErrorAlert error={chatbotsQuery.error ?? modelsQuery.error ?? policiesQuery.error} />
      <Card
        title="Chatbot Management"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            New Chatbot
          </Button>
        }
      >
        <Table
          rowKey="id"
          loading={chatbotsQuery.isLoading || modelsQuery.isLoading || policiesQuery.isLoading}
          dataSource={chatbotsQuery.data ?? []}
          columns={columns}
          locale={{ emptyText: renderEmpty('No chatbots configured') }}
          scroll={{ x: 1260 }}
        />
      </Card>
      <Drawer
        title={editingChatbot ? 'Edit Chatbot' : 'New Chatbot'}
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
        <Form form={form} layout="vertical" initialValues={{ enabled: true }} onFinish={saveMutation.mutate}>
          <Form.Item name="name" label="Chatbot Name" rules={[{ required: true, max: 160 }]}>
            <Input placeholder="Enterprise support bot" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="defaultModelId" label="Default Model">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={(modelsQuery.data ?? []).map((model) => ({ label: `${model.displayName} (${model.modelName})`, value: model.id }))}
            />
          </Form.Item>
          <Form.Item name="contextPolicyId" label="Context Policy">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={(policiesQuery.data ?? []).map((policy) => ({ label: `${policy.name} v${policy.version}`, value: policy.id }))}
            />
          </Form.Item>
          <Form.Item name="enabled" label="Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

function normalizeChatbot(values: ChatbotConfigRequest): ChatbotConfigRequest {
  return {
    name: values.name,
    description: values.description || undefined,
    defaultModelId: values.defaultModelId,
    contextPolicyId: values.contextPolicyId,
    enabled: values.enabled,
  };
}