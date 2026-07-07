import { CheckCircleOutlined, CodeOutlined, PlusOutlined, WarningOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  List,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';
import type { ColumnsType } from 'antd/es/table';

import {
  adminApi,
  type ContextAssemblyRequest,
  type ContextAssemblyResult,
  type ContextPolicy,
  type ContextPolicyRequest,
  type ContextPolicyValidationResult,
} from '../api/admin';
import { formatDate, parseJsonObject, renderEmpty, stringifyJson } from './pageUtils';
import { EnabledControl, ErrorAlert, PageSectionHeader } from './shared';

const { Paragraph, Text } = Typography;

const defaultDsl = `<contextPolicy name="support-bot-v1" maxTokens="12000">
  <system priority="100">
    You are a helpful enterprise support assistant.
  </system>
  <variables>
    <var name="conversation" source="conversation.messages" maxMessages="20" />
    <var name="shortTermMemory" source="memory.shortTerm" limit="10" />
    <var name="longTermMemory" source="memory.longTerm" topK="8" minScore="0.72" />
    <var name="userProfile" source="user.profile" optional="true" />
    <var name="retrievalResults" source="retrieval.vector" topK="6" />
  </variables>
  <budget>
    <reserve target="system" tokens="1200" />
    <reserve target="conversation" tokens="5000" />
    <reserve target="longTermMemory" tokens="2500" />
    <reserve target="retrievalResults" tokens="2500" />
  </budget>
  <rules>
    <include when="conversation.latestUserMessage.exists" target="conversation" />
    <include when="longTermMemory.score &gt; 0.72" target="longTermMemory" />
    <exclude when="metadata.sensitive == true" target="retrievalResults" />
    <truncate target="conversation" strategy="oldest-first" />
  </rules>
  <output>
    <section name="system" />
    <section name="userProfile" optional="true" />
    <section name="shortTermMemory" optional="true" />
    <section name="longTermMemory" optional="true" />
    <section name="conversation" />
    <section name="retrievalResults" optional="true" />
  </output>
</contextPolicy>`;

const defaultPreviewRequest: ContextAssemblyRequest = {
  chatbotId: 1,
  conversationId: 1,
  userId: 'admin-preview-user',
  latestUserMessage: 'Need VPN help',
  metadata: {},
  conversation: [
    { role: 'USER', content: 'Hello', metadata: {} },
    { role: 'ASSISTANT', content: 'How can I help?', metadata: {} },
    { role: 'USER', content: 'Need VPN help', metadata: {} },
  ],
  shortTermMemory: [{ content: 'Prefers concise answers', score: 0, metadata: {} }],
  longTermMemory: [{ content: 'Had VPN issue last quarter', score: 0.9, metadata: {} }],
  userProfile: { department: 'IT', locale: 'en-US' },
  retrievalResults: [{ content: 'VPN reset knowledge base article', score: 0.88, metadata: { source: 'kb' } }],
  toolResults: [],
  runtime: { locale: 'en-US', channel: 'admin-preview' },
};

export function ContextPoliciesPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ContextPolicyFormValues>();
  const [editingPolicy, setEditingPolicy] = useState<ContextPolicy>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [validationResult, setValidationResult] = useState<ContextPolicyValidationResult>();
  const [previewResult, setPreviewResult] = useState<ContextAssemblyResult>();

  const policiesQuery = useQuery({ queryKey: ['context-policies'], queryFn: adminApi.listContextPolicies });
  const modelsQuery = useQuery({ queryKey: ['models'], queryFn: adminApi.listModels });
  const enabledModels = (modelsQuery.data ?? []).filter((model) => model.enabled);
  const modelNameById = new Map((modelsQuery.data ?? []).map((model) => [model.id, `${model.displayName} (${model.modelName})`]));
  const invalidatePolicies = () => queryClient.invalidateQueries({ queryKey: ['context-policies'] });

  const saveMutation = useMutation({
    mutationFn: (values: ContextPolicyFormValues) =>
      editingPolicy ? adminApi.updateContextPolicy(editingPolicy.id, toPolicyRequest(values)) : adminApi.createContextPolicy(toPolicyRequest(values)),
    onSuccess: () => {
      message.success('Context policy saved');
      setDrawerOpen(false);
      void invalidatePolicies();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Save failed'),
  });

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteContextPolicy,
    onSuccess: () => {
      message.success('Context policy deleted');
      void invalidatePolicies();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Delete failed'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ policy, enabled }: { policy: ContextPolicy; enabled: boolean }) =>
      adminApi.updateContextPolicy(policy.id, { ...policyToRequest(policy), enabled }),
    onSuccess: () => void invalidatePolicies(),
    onError: (error) => message.error(error instanceof Error ? error.message : 'Update failed'),
  });

  const validateMutation = useMutation({
    mutationFn: (dslContent: string) => adminApi.validateContextPolicy(dslContent),
    onSuccess: (result) => setValidationResult(result),
    onError: (error) => message.error(error instanceof Error ? error.message : 'Validation failed'),
  });

  const previewMutation = useMutation({
    mutationFn: ({ id, request }: { id: number; request: ContextAssemblyRequest }) => adminApi.previewContextPolicy(id, request),
    onSuccess: (result) => setPreviewResult(result),
    onError: (error) => message.error(error instanceof Error ? error.message : 'Preview failed'),
  });

  const openCreate = () => {
    setEditingPolicy(undefined);
    setValidationResult(undefined);
    setPreviewResult(undefined);
    form.resetFields();
    form.setFieldsValue({
      dslContent: defaultDsl,
      modelId: enabledModels[0]?.id,
      previewJson: stringifyJson(defaultPreviewRequest),
      version: 1,
      enabled: true,
    });
    setDrawerOpen(true);
  };

  const openEdit = (policy: ContextPolicy) => {
    setEditingPolicy(policy);
    setValidationResult(undefined);
    setPreviewResult(undefined);
    form.resetFields();
    form.setFieldsValue({ ...policy, previewJson: stringifyJson(defaultPreviewRequest) });
    setDrawerOpen(true);
  };

  const runValidate = () => {
    const dslContent = form.getFieldValue('dslContent');
    if (!dslContent) {
      message.warning('Enter DSL content first');
      return;
    }
    validateMutation.mutate(dslContent);
  };

  const runPreview = () => {
    if (!editingPolicy) {
      message.warning('Save the policy before previewing it');
      return;
    }
    try {
      const request = parseJsonObject(form.getFieldValue('previewJson') ?? '{}', 'Preview input') as ContextAssemblyRequest;
      previewMutation.mutate({ id: editingPolicy.id, request });
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Preview input must be valid JSON');
    }
  };

  const columns: ColumnsType<ContextPolicy> = [
    { title: 'Name', dataIndex: 'name', width: 240 },
    { title: 'Description', dataIndex: 'description', ellipsis: true, render: (value) => value || '-' },
    { title: 'Model', dataIndex: 'modelId', width: 220, render: (id?: number) => (id ? modelNameById.get(id) ?? `#${id}` : '-') },
    { title: 'Version', dataIndex: 'version', width: 100, render: (value) => <Tag>v{value}</Tag> },
    {
      title: 'Status',
      dataIndex: 'enabled',
      width: 170,
      render: (enabled: boolean, policy) => (
        <EnabledControl enabled={enabled} loading={updateMutation.isPending} onChange={(checked) => updateMutation.mutate({ policy, enabled: checked })} />
      ),
    },
    { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
    {
      title: 'Actions',
      width: 220,
      fixed: 'right',
      render: (_, policy) => (
        <Space>
          <Button size="small" onClick={() => openEdit(policy)}>
            Edit
          </Button>
          <Popconfirm title="Delete this context policy?" onConfirm={() => deleteMutation.mutate(policy.id)}>
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
      <ErrorAlert error={policiesQuery.error ?? modelsQuery.error} />
      <Card className="admin-data-card">
        <PageSectionHeader
          title="Context Policy Management"
          actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!enabledModels.length}>
            New Policy
          </Button>
          }
        />
        <Table
          size="middle"
          rowKey="id"
          loading={policiesQuery.isLoading || modelsQuery.isLoading}
          dataSource={policiesQuery.data ?? []}
          columns={columns}
          locale={{ emptyText: renderEmpty(enabledModels.length ? 'No context policies configured' : 'Enable a model first') }}
          scroll={{ x: 1260 }}
        />
      </Card>
      <Drawer
        title={editingPolicy ? 'Edit Context Policy' : 'New Context Policy'}
        open={drawerOpen}
        width="min(1120px, 94vw)"
        destroyOnClose
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button icon={<CodeOutlined />} loading={validateMutation.isPending} onClick={runValidate}>
              Validate
            </Button>
            <Button loading={previewMutation.isPending} disabled={!editingPolicy} onClick={runPreview}>
              Preview
            </Button>
            <Button type="primary" loading={saveMutation.isPending} onClick={() => form.submit()}>
              Save
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" initialValues={{ version: 1, enabled: true }} onFinish={saveMutation.mutate}>
          <Tabs
            items={[
              {
                key: 'editor',
                label: 'Policy DSL',
                children: (
                  <div className="policy-editor-grid">
                    <div>
                      <Form.Item name="name" label="Policy Name" rules={[{ required: true, max: 160 }]}>
                        <Input placeholder="Support Bot Context" />
                      </Form.Item>
                      <Form.Item name="description" label="Description">
                        <Input.TextArea rows={2} />
                      </Form.Item>
                      <Form.Item name="modelId" label="Model" rules={[{ required: true }]}>
                        <Select
                          showSearch
                          optionFilterProp="label"
                          options={enabledModels.map((model) => ({ label: `${model.displayName} (${model.modelName})`, value: model.id }))}
                        />
                      </Form.Item>
                      <Space className="form-row" align="start">
                        <Form.Item name="version" label="Version" rules={[{ type: 'number', min: 1 }]}>
                          <InputNumber min={1} precision={0} className="full-width-control" />
                        </Form.Item>
                        <Form.Item name="enabled" label="Enabled" valuePropName="checked">
                          <Switch />
                        </Form.Item>
                      </Space>
                      <Form.Item name="dslContent" label="DSL Content" rules={[{ required: true }]}>
                        <Input.TextArea rows={26} className="dsl-editor" spellCheck={false} />
                      </Form.Item>
                    </div>
                    <ValidationPanel result={validationResult} />
                  </div>
                ),
              },
              {
                key: 'preview',
                label: 'Preview',
                children: (
                  <div className="policy-preview-grid">
                    <Card size="small" title="Preview Input">
                      <Form.Item name="previewJson" noStyle>
                        <Input.TextArea rows={24} className="json-editor" spellCheck={false} />
                      </Form.Item>
                    </Card>
                    <PreviewPanel result={previewResult} />
                  </div>
                ),
              },
            ]}
          />
        </Form>
      </Drawer>
    </div>
  );
}

interface ContextPolicyFormValues extends ContextPolicyRequest {
  previewJson?: string;
}

function policyToRequest(policy: ContextPolicy): ContextPolicyRequest {
  return {
    name: policy.name,
    description: policy.description,
    dslContent: policy.dslContent,
    version: policy.version,
    modelId: policy.modelId,
    enabled: policy.enabled,
  };
}

function toPolicyRequest(values: ContextPolicyFormValues): ContextPolicyRequest {
  return {
    name: values.name,
    description: values.description || undefined,
    dslContent: values.dslContent,
    version: values.version,
    modelId: values.modelId,
    enabled: values.enabled,
  };
}

function ValidationPanel({ result }: { result?: ContextPolicyValidationResult }) {
  if (!result) {
    return (
      <Card size="small" title="Validation Feedback" className="feedback-card">
        <Text type="secondary">Run validation to see parser errors, warnings, policy name, and token budget.</Text>
      </Card>
    );
  }

  return (
    <Card size="small" title="Validation Feedback" className="feedback-card">
      <Space direction="vertical" className="full-width-control" size="middle">
        <Alert
          type={result.valid ? 'success' : 'error'}
          showIcon
          icon={result.valid ? <CheckCircleOutlined /> : <WarningOutlined />}
          message={result.valid ? 'DSL is valid' : 'DSL has validation errors'}
          description={`Policy: ${result.policyName || '-'} / Max tokens: ${result.maxTokens || '-'}`}
        />
        {result.errors.length > 0 && (
          <List
            size="small"
            header="Errors"
            bordered
            dataSource={result.errors}
            renderItem={(error) => (
              <List.Item>
                <Text type="danger">Line {error.line}: {error.tag ? `<${error.tag}> ` : ''}{error.reason}</Text>
              </List.Item>
            )}
          />
        )}
        {result.warnings.length > 0 && (
          <List size="small" header="Warnings" bordered dataSource={result.warnings} renderItem={(warning) => <List.Item>{warning}</List.Item>} />
        )}
      </Space>
    </Card>
  );
}

function PreviewPanel({ result }: { result?: ContextAssemblyResult }) {
  if (!result) {
    return (
      <Card size="small" title="Preview Result" className="feedback-card">
        <Text type="secondary">Save the policy, then preview assembled messages with the sample runtime input.</Text>
      </Card>
    );
  }

  return (
    <Space direction="vertical" className="full-width-control" size="middle">
      <Card size="small" title="Token Budget Report">
        <Descriptions size="small" column={2} bordered>
          <Descriptions.Item label="Max Tokens">{result.tokenBudgetReport.maxTokens}</Descriptions.Item>
          <Descriptions.Item label="Estimated Total">{result.tokenBudgetReport.totalEstimatedTokens}</Descriptions.Item>
          <Descriptions.Item label="Truncated Sections" span={2}>
            {result.tokenBudgetReport.truncatedSections.length ? result.tokenBudgetReport.truncatedSections.join(', ') : '-'}
          </Descriptions.Item>
        </Descriptions>
        <Paragraph className="json-block">{stringifyJson(result.tokenBudgetReport)}</Paragraph>
      </Card>
      {result.warnings.length > 0 && <Alert type="warning" showIcon message="Preview warnings" description={result.warnings.join('\n')} />}
      <Card size="small" title="Assembled Messages">
        <List
          size="small"
          dataSource={result.messages}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta title={<Tag>{item.role}</Tag>} description={<Paragraph className="message-preview">{item.content}</Paragraph>} />
            </List.Item>
          )}
        />
      </Card>
      <Card size="small" title="Sections">
        <Table
          size="small"
          rowKey={(row) => row.name}
          pagination={false}
          dataSource={result.sections}
          columns={[
            { title: 'Name', dataIndex: 'name' },
            { title: 'Role', dataIndex: 'role', width: 100 },
            { title: 'Tokens', dataIndex: 'estimatedTokens', width: 100 },
            { title: 'Included', dataIndex: 'included', width: 110, render: (included: boolean) => (included ? 'Yes' : 'No') },
          ]}
        />
      </Card>
    </Space>
  );
}