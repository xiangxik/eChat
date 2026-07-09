import { EyeOutlined, PlayCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntApp,
  Button,
  Card,
  Checkbox,
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
import { AdminSearchPanel, buildListQuery, ErrorAlert, tableSort, type AdminTableSort } from './shared';

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
  const [datasetFilters, setDatasetFilters] = useState<Record<string, string>>({});
  const [caseFilters, setCaseFilters] = useState<Record<string, string>>({});
  const [resultFilters, setResultFilters] = useState<Record<string, string | boolean>>({});
  const [datasetSort, setDatasetSort] = useState<AdminTableSort>({ sortField: 'updatedAt', sortOrder: 'descend' });
  const [caseSort, setCaseSort] = useState<AdminTableSort>({});
  const [resultSort, setResultSort] = useState<AdminTableSort>({});
  const datasetListQuery = buildListQuery(datasetFilters, datasetSort);
  const caseListQuery = buildListQuery(caseFilters, caseSort);
  const resultListQuery = buildListQuery(resultFilters, resultSort);

  const datasetsQuery = useQuery({ queryKey: ['eval-datasets', datasetListQuery], queryFn: () => adminApi.listEvalDatasets(datasetListQuery) });
  const chatbotsQuery = useQuery({ queryKey: ['chatbots'], queryFn: adminApi.listChatbots });
  const casesQuery = useQuery({
    queryKey: ['eval-cases', selectedDatasetId, caseListQuery],
    queryFn: () => adminApi.listEvalCases(selectedDatasetId!, caseListQuery),
    enabled: selectedDatasetId !== undefined,
  });
  const runQuery = useQuery({
    queryKey: ['eval-run', activeRunId],
    queryFn: () => adminApi.getEvalRun(activeRunId!),
    enabled: activeRunId !== undefined,
    refetchInterval: (query) => (isRunActive(query.state.data) ? 1200 : false),
  });
  const resultsQuery = useQuery({
    queryKey: ['eval-results', activeRunId, resultListQuery],
    queryFn: () => adminApi.listEvalResults(activeRunId!, resultListQuery),
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
    { title: 'Name', dataIndex: 'name', width: 180, sorter: true },
    { title: 'Description', dataIndex: 'description', ellipsis: true, sorter: true, render: (value) => value || '-' },
    { title: 'Chatbot', dataIndex: 'chatbotId', width: 160, sorter: true, render: (id: number) => chatbotNameById.get(id) ?? `#${id}` },
    { title: 'Updated', dataIndex: 'updatedAt', width: 150, sorter: true, render: formatDate },
    {
      title: 'Actions',
      width: 135,
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
    { title: 'Input', dataIndex: 'input', ellipsis: true, sorter: true },
    { title: 'Expected Behavior', dataIndex: 'expectedBehavior', ellipsis: true, sorter: true, render: (value) => value || '-' },
    {
      title: 'Keywords',
      dataIndex: 'expectedKeywords',
      width: 180,
      render: (keywords: string[]) => keywords?.length ? keywords.map((keyword) => <Tag key={keyword}>{keyword}</Tag>) : '-',
    },
  ];

  const resultColumns: ColumnsType<EvalResult> = [
    { title: 'Case', dataIndex: 'caseId', width: 90, sorter: true, render: (id: number) => `#${id}` },
    { title: 'Result', dataIndex: 'passed', width: 120, sorter: true, render: (passed: boolean) => <PassTag passed={passed} /> },
    { title: 'Output', dataIndex: 'output', ellipsis: true, sorter: true, render: (value?: string) => value || '-' },
    { title: 'Error', dataIndex: 'error', ellipsis: true, sorter: true, render: (value?: string) => value || '-' },
    {
      title: 'Actions',
      width: 120,
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
      <div className="eval-workspace">
        <Card
          size="small"
          className="eval-card eval-dataset-card"
          title="Datasets"
          extra={
            <Button type="primary" icon={<PlusOutlined />} onClick={openDatasetDrawer} disabled={!enabledChatbots(chatbotsQuery.data).length}>
              New Dataset
            </Button>
          }
        >
          <AdminSearchPanel
            fields={[
              { name: 'search', label: 'Keyword' },
              { name: 'name', label: 'Name' },
              { name: 'description', label: 'Description' },
              { name: 'chatbotId', label: 'Chatbot ID' },
            ]}
            initialValues={datasetFilters}
            onSearch={(values) => setDatasetFilters(values as Record<string, string>)}
          />
          <Table
            size="small"
            rowKey="id"
            loading={datasetsQuery.isLoading || chatbotsQuery.isLoading}
            dataSource={datasetsQuery.data ?? []}
            columns={datasetColumns}
            rowClassName={(dataset) => (dataset.id === selectedDatasetId ? 'selected-table-row' : '')}
            locale={{ emptyText: renderEmpty(enabledChatbots(chatbotsQuery.data).length ? 'No eval datasets configured' : 'Enable a chatbot first') }}
            pagination={{ size: 'small' }}
            onChange={(_, __, sorter) => setDatasetSort(tableSort(sorter))}
          />
        </Card>

        <div className="eval-main-panels">
          <Card
            size="small"
            className="eval-card"
            title={selectedDataset ? `Cases: ${selectedDataset.name}` : 'Cases'}
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
            <AdminSearchPanel
              fields={[
                { name: 'search', label: 'Keyword' },
                { name: 'input', label: 'Input' },
                { name: 'expectedBehavior', label: 'Expected' },
                { name: 'keyword', label: 'Keyword Tag' },
              ]}
              initialValues={caseFilters}
              onSearch={(values) => setCaseFilters(values as Record<string, string>)}
            />
            <Table
              size="small"
              rowKey="id"
              loading={casesQuery.isLoading}
              dataSource={casesQuery.data ?? []}
              columns={caseColumns}
              locale={{ emptyText: renderEmpty(selectedDatasetId ? 'No cases in this dataset' : 'Select a dataset') }}
              pagination={{ size: 'small' }}
              onChange={(_, __, sorter) => setCaseSort(tableSort(sorter))}
            />
          </Card>

          <Card size="small" className="eval-card" title="Current Run" extra={runQuery.data ? <RunStatusTag status={runQuery.data.status} /> : null}>
            {runQuery.data ? <RunSummary run={runQuery.data} /> : <Text type="secondary">Start a dataset run to inspect status and results.</Text>}
            <AdminSearchPanel
              fields={[
                { name: 'search', label: 'Keyword' },
                { name: 'output', label: 'Output' },
                { name: 'error', label: 'Error' },
                { name: 'passed', label: 'Result', type: 'select', options: [{ label: 'Passed', value: true }, { label: 'Failed', value: false }] },
              ]}
              initialValues={resultFilters}
              onSearch={(values) => setResultFilters(values as Record<string, string | boolean>)}
            />
            <Table
              size="small"
              className="eval-results-table"
              rowKey="id"
              loading={resultsQuery.isLoading || isRunActive(runQuery.data)}
              dataSource={resultsQuery.data ?? []}
              columns={resultColumns}
              locale={{ emptyText: renderEmpty(activeRunId ? 'No results yet' : 'No active eval run') }}
              pagination={{ size: 'small' }}
              onChange={(_, __, sorter) => setResultSort(tableSort(sorter))}
            />
          </Card>
        </div>
      </div>

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
          <Form.Item name="maxLatencyMillis" label="Max Latency Millis" rules={[{ type: 'number', min: 1 }]}>
            <InputNumber min={1} precision={0} className="full-width-control" />
          </Form.Item>
          <Form.Item name="maxEstimatedCostUsd" label="Max Estimated Cost USD" rules={[{ type: 'number', min: 0 }]}>
            <InputNumber min={0} precision={6} className="full-width-control" />
          </Form.Item>
          <Form.Item name="costPer1kTokensUsd" label="Cost Per 1K Tokens USD" rules={[{ type: 'number', min: 0 }]}>
            <InputNumber min={0} precision={6} className="full-width-control" />
          </Form.Item>
          <Form.Item name="goldenReplay" valuePropName="checked" initialValue>
            <Checkbox>Golden conversation replay</Checkbox>
          </Form.Item>
          <Form.Item name="forbiddenPhrasesText" label="Forbidden Phrases">
            <Input placeholder="internal only, do not know" />
          </Form.Item>
          <Form.Item name="rubricJson" label="Rubric JSON">
            <Input.TextArea rows={5} className="json-editor" spellCheck={false} />
          </Form.Item>
          <Form.Item name="releaseGateJson" label="Release Gate JSON">
            <Input.TextArea rows={5} className="json-editor" spellCheck={false} />
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
  rubricJson?: string;
  releaseGateJson?: string;
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
    maxLatencyMillis: values.maxLatencyMillis,
    maxEstimatedCostUsd: values.maxEstimatedCostUsd,
    costPer1kTokensUsd: values.costPer1kTokensUsd,
    goldenReplay: values.goldenReplay,
    forbiddenPhrases: splitList(values.forbiddenPhrasesText),
    rubric: parseJsonObject(values.rubricJson ?? '{}', 'Rubric'),
    releaseGate: parseJsonObject(values.releaseGateJson ?? '{}', 'Release gate'),
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
      <Descriptions.Item label="Release Gate">{formatBoolean(run.summary?.releaseGatePassed)}</Descriptions.Item>
      <Descriptions.Item label="Avg Latency">{formatMillis(readMetric(run, 'averageLatencyMillis'))}</Descriptions.Item>
      <Descriptions.Item label="Total Cost">{formatUsd(readMetric(run, 'totalEstimatedCostUsd'))}</Descriptions.Item>
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

function formatBoolean(value: unknown) {
  return typeof value === 'boolean' ? (value ? 'Passed' : 'Failed') : '-';
}

function readMetric(run: EvalRun, name: string) {
  const metrics = run.summary?.metrics;
  return metrics && typeof metrics === 'object' && name in metrics ? (metrics as Record<string, unknown>)[name] : undefined;
}

function formatMillis(value: unknown) {
  return typeof value === 'number' ? `${Math.round(value)} ms` : '-';
}

function formatUsd(value: unknown) {
  return typeof value === 'number' ? `$${value.toFixed(6)}` : '-';
}
