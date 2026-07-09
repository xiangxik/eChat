import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntApp, Button, Card, Drawer, Form, Input, Popconfirm, Space, Switch, Table } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';

import { adminApi, type ChatbotConfig, type ChatbotConfigRequest } from '../api/admin';
import { fetchAdminSession } from '../api/client';
import { formatDate, renderEmpty } from './pageUtils';
import { AdminSearchPanel, buildListQuery, EnabledTag, ErrorAlert, PageSectionHeader, tableSort, type AdminTableSort } from './shared';

export function ChatbotsPage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ChatbotConfigRequest>();
  const [editingChatbot, setEditingChatbot] = useState<ChatbotConfig>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [filters, setFilters] = useState<Record<string, string | boolean>>({});
  const [sort, setSort] = useState<AdminTableSort>({});
  const listQuery = buildListQuery(filters, sort);

  const chatbotsQuery = useQuery({ queryKey: ['chatbots', listQuery], queryFn: () => adminApi.listChatbots(listQuery) });
  const sessionQuery = useQuery({ queryKey: ['admin-session'], queryFn: fetchAdminSession, retry: false });
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

  const openCreate = () => {
    setEditingChatbot(undefined);
    form.resetFields();
    form.setFieldsValue({ enabled: true });
    setDrawerOpen(true);
  };

  const openEdit = (chatbot: ChatbotConfig) => {
    setEditingChatbot(chatbot);
    form.resetFields();
    form.setFieldsValue(chatbot);
    setDrawerOpen(true);
  };

  const columns: ColumnsType<ChatbotConfig> = [
    ...(sessionQuery.data?.superAdmin ? [{ title: 'Tenant', dataIndex: 'tenantId', width: 120, sorter: true }] : []),
    { title: 'Name', dataIndex: 'name', width: 180, sorter: true },
    { title: 'Description', dataIndex: 'description', ellipsis: true, sorter: true, render: (value?: string) => value || '-' },
    {
      title: 'Status',
      dataIndex: 'enabled',
      width: 72,
      sorter: true,
      render: (enabled: boolean) => <EnabledTag enabled={enabled} />,
    },
    { title: 'Updated', dataIndex: 'updatedAt', width: 155, sorter: true, render: formatDate },
    {
      title: 'Actions',
      width: 205,
      render: (_, chatbot) => (
        <Space>
          <Button size="small" onClick={() => navigate(`/chatbots/${chatbot.id}/workflow`)}>
            Workflow
          </Button>
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
      <ErrorAlert error={chatbotsQuery.error} />
      <AdminSearchPanel
        fields={[
          { name: 'search', label: 'Keyword' },
          ...(sessionQuery.data?.superAdmin ? [{ name: 'tenantId', label: 'Tenant' }] : []),
          { name: 'name', label: 'Name' },
          { name: 'description', label: 'Description' },
          { name: 'enabled', label: 'Status', type: 'select', options: [{ label: 'Enabled', value: true }, { label: 'Disabled', value: false }] },
        ]}
        initialValues={filters}
        onSearch={(values) => setFilters(values as Record<string, string | boolean>)}
      />
      <Card className="admin-data-card">
        <PageSectionHeader
          title="Chatbot Management"
          actions={
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              New Chatbot
            </Button>
          }
        />
        <Table
          size="small"
          rowKey="id"
          loading={chatbotsQuery.isLoading}
          dataSource={chatbotsQuery.data ?? []}
          columns={columns}
          locale={{ emptyText: renderEmpty('No chatbots configured') }}
          pagination={{ size: 'small' }}
          onChange={(_, __, sorter) => setSort(tableSort(sorter))}
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
    enabled: values.enabled,
  };
}