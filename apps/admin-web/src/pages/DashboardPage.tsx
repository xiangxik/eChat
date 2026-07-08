import { ApiOutlined, AppstoreOutlined, MessageOutlined, RobotOutlined } from '@ant-design/icons';
import { ProCard } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, Progress, Space, Statistic, Table, Typography } from 'antd';
import type { ReactNode } from 'react';

import { adminApi } from '../api/admin';
import { fetchHealth } from '../api/client';
import { PageSectionHeader, ReadinessTag } from './shared';

const { Text } = Typography;

export function DashboardPage() {
  const healthQuery = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false });
  const providersQuery = useQuery({ queryKey: ['providers'], queryFn: adminApi.listProviders });
  const modelsQuery = useQuery({ queryKey: ['models'], queryFn: adminApi.listModels });
  const chatbotsQuery = useQuery({ queryKey: ['chatbots'], queryFn: adminApi.listChatbots });
  const readinessRows = [
    { key: 'providers', item: 'LLM providers', count: providersQuery.data?.length ?? 0, description: 'Connection endpoints and credentials' },
    { key: 'models', item: 'Model configs', count: modelsQuery.data?.length ?? 0, description: 'Chat, embedding, and generation presets' },
    { key: 'chatbots', item: 'Chatbot configs', count: chatbotsQuery.data?.length ?? 0, description: 'Runtime assistants exposed to users' },
  ];
  const configuredCount = readinessRows.filter((item) => item.count > 0).length;
  const readinessPercent = Math.round((configuredCount / readinessRows.length) * 100);

  return (
    <SpaceStack className="dashboard-page">
      {healthQuery.isError && <Alert type="warning" showIcon message="Backend health check is unavailable" />}
      <div className="dashboard-metric-grid">
        <ProCard bordered className="metric-card">
          <Space direction="vertical" size={10}>
            <Statistic
              title="Backend"
              value={healthQuery.data?.status ?? 'Unknown'}
              prefix={<MessageOutlined />}
              loading={healthQuery.isLoading}
            />
            <Text type="secondary">{healthQuery.data?.service ?? 'chatbot-service'}</Text>
          </Space>
        </ProCard>
        <MetricCard title="Providers" value={providersQuery.data?.length ?? 0} icon={<ApiOutlined />} />
        <MetricCard title="Models" value={modelsQuery.data?.length ?? 0} icon={<AppstoreOutlined />} />
        <MetricCard title="Chatbots" value={chatbotsQuery.data?.length ?? 0} icon={<RobotOutlined />} />
      </div>
      <ProCard bordered className="readiness-card">
        <PageSectionHeader
          title="Configuration Readiness"
          actions={
            <Space size={12}>
              <Progress type="circle" size={54} percent={readinessPercent} />
              <Text type="secondary">{configuredCount} of {readinessRows.length} areas configured</Text>
            </Space>
          }
        />
        <Table
          size="small"
          rowKey="key"
          pagination={false}
          dataSource={readinessRows}
          columns={[
            {
              title: 'Area',
              dataIndex: 'item',
              render: (item: string, record) => (
                <Space direction="vertical" size={0}>
                  <Text strong>{item}</Text>
                  <Text type="secondary">{record.description}</Text>
                </Space>
              ),
            },
            { title: 'Count', dataIndex: 'count', width: 160 },
            {
              title: 'State',
              dataIndex: 'count',
              width: 180,
              render: (count: number) => <ReadinessTag ready={count > 0} />,
            },
          ]}
        />
      </ProCard>
    </SpaceStack>
  );
}

function MetricCard({ title, value, icon }: { title: string; value: number; icon: ReactNode }) {
  return (
    <ProCard bordered className="metric-card">
      <Statistic title={title} value={value} prefix={icon} />
      <Text type="secondary">Configured resources</Text>
    </ProCard>
  );
}

function SpaceStack({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={["page-stack", className].filter(Boolean).join(' ')}>{children}</div>;
}