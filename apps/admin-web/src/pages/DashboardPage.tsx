import { ApiOutlined, AppstoreOutlined, MessageOutlined, RobotOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Row, Statistic, Table, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';

import { adminApi } from '../api/admin';
import { fetchHealth } from '../api/client';

const { Text } = Typography;

export function DashboardPage() {
  const healthQuery = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false });
  const providersQuery = useQuery({ queryKey: ['providers'], queryFn: adminApi.listProviders });
  const modelsQuery = useQuery({ queryKey: ['models'], queryFn: adminApi.listModels });
  const chatbotsQuery = useQuery({ queryKey: ['chatbots'], queryFn: adminApi.listChatbots });
  const policiesQuery = useQuery({ queryKey: ['context-policies'], queryFn: adminApi.listContextPolicies });

  return (
    <SpaceStack>
      {healthQuery.isError && <Alert type="warning" showIcon message="Backend health check is unavailable" />}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic
              title="Backend"
              value={healthQuery.data?.status ?? 'Unknown'}
              prefix={<MessageOutlined />}
              loading={healthQuery.isLoading}
            />
            <Text type="secondary">{healthQuery.data?.service ?? 'chatbot-service'}</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="Providers" value={providersQuery.data?.length ?? 0} prefix={<ApiOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="Models" value={modelsQuery.data?.length ?? 0} prefix={<AppstoreOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic title="Chatbots" value={chatbotsQuery.data?.length ?? 0} prefix={<RobotOutlined />} />
          </Card>
        </Col>
      </Row>
      <Card title="Configuration Readiness">
        <Table
          rowKey="key"
          pagination={false}
          dataSource={[
            { key: 'providers', item: 'LLM providers', count: providersQuery.data?.length ?? 0 },
            { key: 'models', item: 'Model configs', count: modelsQuery.data?.length ?? 0 },
            { key: 'chatbots', item: 'Chatbot configs', count: chatbotsQuery.data?.length ?? 0 },
            { key: 'policies', item: 'Context policies', count: policiesQuery.data?.length ?? 0 },
          ]}
          columns={[
            { title: 'Area', dataIndex: 'item' },
            { title: 'Count', dataIndex: 'count', width: 160 },
            {
              title: 'State',
              dataIndex: 'count',
              width: 180,
              render: (count: number) => <Tag color={count > 0 ? 'success' : 'default'}>{count > 0 ? 'Configured' : 'Empty'}</Tag>,
            },
          ]}
        />
      </Card>
    </SpaceStack>
  );
}

function SpaceStack({ children }: { children: ReactNode }) {
  return <div className="page-stack">{children}</div>;
}