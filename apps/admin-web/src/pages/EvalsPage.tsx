import { EyeOutlined, PlayCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Tabs,
  Typography,
} from 'antd';
import { useEffect, useState } from 'react';
import type { ColumnsType } from 'antd/es/table';

import {
  adminApi,
  type ChatbotConfig,
  type EvalCase,
  type EvalCaseRequest,
  type EvalDataset,
  type EvalDatasetRequest,
  type EvalResult,
  type EvalRun,
  type EvalRunRequest,
} from '../api/admin';
import { formatDate, parseJsonObject, renderEmpty, stringifyJson } from './pageUtils';
import { ErrorAlert } from './shared';

const { Paragraph, Text } = Typography;

export function EvalsPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const [datasetForm] = Form.useForm<EvalDatasetRequest>();
  const [caseForm] = Form.useForm<EvalCaseFormValues>();
  const [runForm] = Form.useForm<EvalRunFormValues>();
  const [datasetDrawerOpen, setDatasetDrawerOpen] = useState(false);
  const [caseDrawerOpen, setCaseDrawerOpen] = useState(false);
  const [runDrawerOpen, setRunDrawerOpen] = useState(false);
  const [selectedDatasetId, setSelectedDatasetId] = useState<number>();
  const [activeRunId, setActiveRunId] = useState<number>();
  const [viewingResult, setViewingResult] = useState<EvalResult>();

  const datasetsQuery = useQuery({ queryKey: ['eval-datasets'], queryFn: adminApi.listEvalDatasets });
  const chatbotsQuery = useQuery({ queryKey: ['chatbots'], queryFn: adminApi.listChatbots });
  const casesQuery = useQuery({
    queryKey: ['eval-cases', selectedDatasetId],
    queryFn: () => adminApi.listEvalCases(selectedDatasetId!),
    enabled: selectedDatasetId !== undefined,
  });
  const runQuery = useQuery({
    queryKey: ['eval-run', activeRunId],
    queryFn: () => adminApi.getEvalRun(activeRunId!),
    enabled: activeRunId !== undefined,
    refetchInterval: (query) => (isRunActive(query.state.data) ? 1200 : false),
  });
  const resultsQuery = useQuery({
    queryKey: ['eval-results', activeRunId],
    queryFn: () => adminApi.listEvalResults(activeRunId!),
    enabled: activeRunId !== undefined && runQuery.data?.status !== 'PENDING',
    refetchInterval: () => (isRunActive(runQuery.data) ? 1200 : false),
  });

  useEffect(() => {
    if (!selectedDatasetId && datasetsQuery.data?.length) {
      setSelectedDatasetId(datasetsQuery.data[0].id);
    }
  }, [datasetsQuery.data, selectedDatasetId]);

  const selectedDataset = (datasetsQuery.data ?? []).find((dataset) => dataset.id === selectedDatasetId);
  const chatbotNameById = new Map((chatbotsQuery.data ?? []).map((chatbot) => [chatbot.id, chatbot.name]));
  const invalidateDatasets = () => queryClient.invalidateQueries({ queryKey: ['eval-datasets'] });
  const invalidateCases = () => queryClient.invalidateQueries({ queryKey: ['eval-cases', selectedDatasetId] });

  const datasetMutation = useMutation({
    mutationFn: adminApi.createEvalDataset,
    onSuccess: (dataset) => {
      message.success('Eval dataset created');
      setDatasetDrawerOpen(false);
      setSelectedDatasetId(dataset.id);
      void invalidateDatasets();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Create dataset failed'),
  });

  const caseMutation = useMutation({
    mutationFn: (values: EvalCaseFormValues) => adminApi.createEvalCase(selectedDatasetId!, toCaseRequest(values)),
    onSuccess: () => {
      message.success('Eval case added');
      setCaseDrawerOpen(false);
      void invalidateCases();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Create case failed'),
  });

  const runMutation = useMutation({
    mutationFn: (values: EvalRunFormValues) => adminApi.createEvalRun(toRunRequest(values, selectedDatasetId!)),
    onSuccess: (run) => {
      message.success('Eval run started');
      setRunDrawerOpen(false);
      setActiveRunId(run.id);
      void queryClient.invalidateQueries({ queryKey: ['eval-run', run.id] });
      void queryClient.invalidateQueries({ queryKey: ['eval-results', run.id] });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : 'Start eval failed'),
  });

  const openDatasetDrawer = () => {
    datasetForm.resetFields();
    datasetForm.setFieldsValue({ chatbotId: enabledChatbots(chatbotsQuery.data).at(0)?.id });
    setDatasetDrawerOpen(true);
  };

  const openCaseDrawer = () => {
    caseForm.resetFields();
    caseForm.setFieldsValue({ metadataJson: '{}' });
    setCaseDrawerOpen(true);
  };

  const openRunDrawer = (dataset: EvalDataset) => {
    setSelectedDatasetId(dataset.id);
    runForm.resetFields();
    runForm.setFieldsValue({ chatbotId: dataset.chatbotId, maxEstimatedTokens: 12000, metadataJson: '{}' });
    setRunDrawerOpen(true);
  };

  const datasetColumns: ColumnsType<EvalDataset> = [
    { title: 'Name', dataIndex: 'name', width: 240 },
    { title: 'Description', dataIndex: 'description', ellipsis: true, render: (value) => value || '-' },
    { title: 'Chatbot', dataIndex: 'chatbotId', width: 220, render: (id: number) => chatbotNameById.get(id) ?? `#${id}` },
    { title: 'Updated', dataIndex: 'updatedAt', width: 190, render: formatDate },
    {
      title: 'Actions',
      width: 220,
      fixed: 'right',
      render: (_, dataset) => (
        <Space>
          <Button size="small" onClick={() => setSelectedDatasetId(dataset.id)}>
            Cases
          </Button>
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => openRunDrawer(dataset)}>
            Run
          </Button>
        </Space>
      ),
    },
  ];

  const caseColumns: ColumnsType<EvalCase> = [
    { title: 'Input', dataIndex: 'input', ellipsis: true },
    { title: 'Expected Behavior', dataIndex: 'expectedBehavior', ellipsis: true, render: (value) => value || '-' },
    {
      title: 'Keywords',
      dataIndex: 'expectedKeywords',
      width: 260,
      render: (keywords: string[]) => keywords?.length ? keywords.map((keyword) => <Tag key={keyword}>{keyword}</Tag>) : '-',
    },
  ];

  const resultColumns: ColumnsType<EvalResult> = [
    { title: 'Case', dataIndex: 'caseId', width: 90, render: (id: number) => `#${id}` },
    { title: 'Result', dataIndex: 'passed', width: 120, render: (passed: boolean) => <PassTag passed={passed} /> },
    { title: 'Output', dataIndex: 'output', ellipsis: true, render: (value?: string) => value || '-' },
    { title: 'Error', dataIndex: 'error', ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: 'Actions',
      width: 120,
      fixed: 'right',
      render: (_, result) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => setViewingResult(result)}>
          View
        </Button>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <ErrorAlert error={datasetsQuery.error ?? chatbotsQuery.error ?? casesQuery.error ?? runQuery.error ?? resultsQuery.error} />
      <Card
        title="Eval Dataset Management"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openDatasetDrawer} disabled={!enabledChatbots(chatbotsQuery.data).length}>
            New Dataset
          </Button>
        }
      >
        <Table
          rowKey="id"
          loading={datasetsQuery.isLoading || chatbotsQuery.isLoading}
          dataSource={datasetsQuery.data ?? []}
          columns={datasetColumns}
          rowClassName={(dataset) => (dataset.id === selectedDatasetId ? 'selected-table-row' : '')}
          locale={{ emptyText: renderEmpty(enabledChatbots(chatbotsQuery.data).length ? 'No eval datasets configured' : 'Enable a chatbot first') }}
          scroll={{ x: 1040 }}
        />
      </Card>

      <Card
        title={selectedDataset ? `Cases: ${selectedDataset.name}` : 'Eval Cases'}
        extra={
          <Space>
            <Button icon={<PlusOutlined />} disabled={!selectedDatasetId} onClick={openCaseDrawer}>
              New Case
            </Button>
            <Button type="primary" icon={<PlayCircleOutlined />} disabled={!selectedDataset} onClick={() => selectedDataset && openRunDrawer(selectedDataset)}>
              Run Dataset
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={casesQuery.isLoading}
          dataSource={casesQuery.data ?? []}
          columns={caseColumns}
          locale={{ emptyText: renderEmpty(selectedDatasetId ? 'No cases in this dataset' : 'Select a dataset') }}
          scroll={{ x: 900 }}
        />
      </Card>

      <Card title="Current Eval Run" extra={runQuery.data ? <RunStatusTag status={runQuery.data.status} /> : null}>
        {runQuery.data ? <RunSummary run={runQuery.data} /> : <Text type="secondary">Start a dataset run to inspect status and results.</Text>}
        <Table
          className="eval-results-table"
          rowKey="id"
          loading={resultsQuery.isLoading || isRunActive(runQuery.data)}
          dataSource={resultsQuery.data ?? []}
          columns={resultColumns}
          locale={{ emptyText: renderEmpty(activeRunId ? 'No results yet' : 'No active eval run') }}
          scroll={{ x: 980 }}
        />
      </Card>

      <Drawer
        title="New Eval Dataset"
        open={datasetDrawerOpen}
        width={560}
        destroyOnClose
        onClose={() => setDatasetDrawerOpen(false)}
        extra={
          <Button type="primary" loading={datasetMutation.isPending} onClick={() => datasetForm.submit()}>
            Save
          </Button>
        }
      >
        <Form form={datasetForm} layout="vertical" onFinish={datasetMutation.mutate}>
          <Form.Item name="name" label="Dataset Name" rules={[{ required: true, max: 180 }]}>
            <Input placeholder="Support regression suite" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="chatbotId" label="Chatbot" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={enabledChatbots(chatbotsQuery.data).map(toChatbotOption)} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="New Eval Case"
        open={caseDrawerOpen}
        width={680}
        destroyOnClose
        onClose={() => setCaseDrawerOpen(false)}
        extra={
          <Button type="primary" loading={caseMutation.isPending} onClick={() => caseForm.submit()}>
            Save
          </Button>
        }
      >
        <Form form={caseForm} layout="vertical" onFinish={caseMutation.mutate}>
          <Form.Item name="input" label="Input" rules={[{ required: true, max: 8000 }]}>
            <Input.TextArea rows={5} />
          </Form.Item>
          <Form.Item name="expectedBehavior" label="Expected Behavior">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="expectedKeywordsText" label="Expected Keywords">
            <Input placeholder="VPN, reset, MFA" />
          </Form.Item>
          <Form.Item name="metadataJson" label="Metadata JSON">
            <Input.TextArea rows={8} className="json-editor" spellCheck={false} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title="Start Eval Run"
        open={runDrawerOpen}
        width={620}
        destroyOnClose
        onClose={() => setRunDrawerOpen(false)}
        extra={
          <Button type="primary" icon={<PlayCircleOutlined />} loading={runMutation.isPending} onClick={() => runForm.submit()}>
            Start
          </Button>
        }
      >
        <Form form={runForm} layout="vertical" onFinish={runMutation.mutate}>
          <Form.Item name="chatbotId" label="Chatbot Override">
            <Select allowClear showSearch optionFilterProp="label" options={enabledChatbots(chatbotsQuery.data).map(toChatbotOption)} />
          </Form.Item>
          <Form.Item name="maxEstimatedTokens" label="Max Estimated Context Tokens" rules={[{ type: 'number', min: 1 }]}>
            <InputNumber min={1} precision={0} className="full-width-control" />
          </Form.Item>
          <Form.Item name="forbiddenPhrasesText" label="Forbidden Phrases">
            <Input placeholder="internal only, do not know" />
          </Form.Item>
          <Form.Item name="metadataJson" label="Run Metadata JSON">
            <Input.TextArea rows={8} className="json-editor" spellCheck={false} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer title="Eval Result" open={!!viewingResult} width="min(980px, 94vw)" destroyOnClose onClose={() => setViewingResult(undefined)}>
        {viewingResult && <ResultDetail result={viewingResult} />}
      </Drawer>
    </div>
  );
}

interface EvalCaseFormValues extends EvalCaseRequest {
  expectedKeywordsText?: string;
  metadataJson?: string;
}

interface EvalRunFormValues extends EvalRunRequest {
  forbiddenPhrasesText?: string;
  metadataJson?: string;
}

function toCaseRequest(values: EvalCaseFormValues): EvalCaseRequest {
  return {
    input: values.input,
    expectedBehavior: values.expectedBehavior || undefined,
    expectedKeywords: splitList(values.expectedKeywordsText),
    metadata: parseJsonObject(values.metadataJson ?? '{}', 'Metadata'),
  };
}

function toRunRequest(values: EvalRunFormValues, datasetId: number): EvalRunRequest {
  return {
    datasetId,
    chatbotId: values.chatbotId,
    maxEstimatedTokens: values.maxEstimatedTokens,
    forbiddenPhrases: splitList(values.forbiddenPhrasesText),
    metadata: parseJsonObject(values.metadataJson ?? '{}', 'Run metadata'),
  };
}

function splitList(value?: string) {
  return (value ?? '')
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function enabledChatbots(chatbots?: ChatbotConfig[]) {
  return (chatbots ?? []).filter((chatbot) => chatbot.enabled);
}

function toChatbotOption(chatbot: ChatbotConfig) {
  return { label: chatbot.name, value: chatbot.id };
}

function isRunActive(run?: EvalRun) {
  return run?.status === 'PENDING' || run?.status === 'RUNNING';
}

function RunStatusTag({ status }: { status: EvalRun['status'] }) {
  const colorByStatus: Record<EvalRun['status'], string> = {
    PENDING: 'processing',
    RUNNING: 'processing',
    COMPLETED: 'success',
    FAILED: 'error',
  };
  return <Tag color={colorByStatus[status]}>{status}</Tag>;
}

function PassTag({ passed }: { passed: boolean }) {
  return <Tag color={passed ? 'success' : 'error'}>{passed ? 'Passed' : 'Failed'}</Tag>;
}

function RunSummary({ run }: { run: EvalRun }) {
  return (
    <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }} className="eval-run-summary">
      <Descriptions.Item label="Run">#{run.id}</Descriptions.Item>
      <Descriptions.Item label="Dataset">#{run.datasetId}</Descriptions.Item>
      <Descriptions.Item label="Started">{formatDate(run.startedAt)}</Descriptions.Item>
      <Descriptions.Item label="Finished">{formatDate(run.finishedAt)}</Descriptions.Item>
      <Descriptions.Item label="Total Cases">{String(run.summary?.totalCases ?? '-')}</Descriptions.Item>
      <Descriptions.Item label="Passed">{String(run.summary?.passedCases ?? '-')}</Descriptions.Item>
      <Descriptions.Item label="Failed">{String(run.summary?.failedCases ?? '-')}</Descriptions.Item>
      <Descriptions.Item label="Pass Rate">{formatRate(run.summary?.passRate)}</Descriptions.Item>
    </Descriptions>
  );
}

function ResultDetail({ result }: { result: EvalResult }) {
  return (
    <Tabs
      items={[
        {
          key: 'output',
          label: 'Output',
          children: (
            <Space direction="vertical" className="full-width-control" size="middle">
              <PassTag passed={result.passed} />
              {result.error && <Text type="danger">{result.error}</Text>}
              <Paragraph className="message-preview">{result.output || '-'}</Paragraph>
            </Space>
          ),
        },
        { key: 'context', label: 'Context Snapshot', children: <pre className="json-block">{stringifyJson(result.contextSnapshot)}</pre> },
        { key: 'tokens', label: 'Token Budget', children: <pre className="json-block">{stringifyJson(result.tokenBudgetReport)}</pre> },
        { key: 'scores', label: 'Scores', children: <pre className="json-block">{stringifyJson(result.scores)}</pre> },
      ]}
    />
  );
}

function formatRate(value: unknown) {
  return typeof value === 'number' ? `${Math.round(value * 100)}%` : '-';
}
