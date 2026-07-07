import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Descriptions, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { adminApi, type AuditLog } from '../api/admin';
import { ErrorAlert, PageSectionHeader } from './shared';
import { formatDate, renderEmpty, stringifyJson } from './pageUtils';

const { Paragraph, Text } = Typography;

export function AuditLogsPage() {
  const auditLogsQuery = useQuery({ queryKey: ['audit-logs'], queryFn: adminApi.listAuditLogs });
  const auditLogs = auditLogsQuery.data ?? [];

  const columns: ColumnsType<AuditLog> = [
    {
      title: 'Time',
      dataIndex: 'occurredAt',
      width: 190,
      render: (value: string) => formatDate(value),
    },
    {
      title: 'Event',
      dataIndex: 'eventType',
      width: 240,
      render: (value: string) => <Tag color={value.includes('FAILED') ? 'error' : 'processing'}>{value}</Tag>,
    },
    {
      title: 'Resource',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.resourceType}</Text>
          <Text type="secondary">{record.resourceId ?? '-'}</Text>
        </Space>
      ),
    },
    {
      title: 'Actor',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.actorType}</Text>
          <Text type="secondary">{record.actorId ?? '-'}</Text>
          <Text type="secondary">tenant: {record.tenantId}</Text>
        </Space>
      ),
    },
    {
      title: 'Trace',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text copyable={Boolean(record.requestId)}>{record.requestId ?? '-'}</Text>
          <Text type="secondary" copyable={Boolean(record.traceId)}>{record.traceId ?? '-'}</Text>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <ErrorAlert error={auditLogsQuery.error} />

      <Card className="admin-data-card">
        <PageSectionHeader
          title="Recent Events"
          actions={
            <Button icon={<ReloadOutlined />} loading={auditLogsQuery.isFetching} onClick={() => void auditLogsQuery.refetch()}>
              Refresh
            </Button>
          }
        />
        <Table
          size="middle"
          rowKey="id"
          columns={columns}
          dataSource={auditLogs}
          loading={auditLogsQuery.isLoading}
          locale={{ emptyText: renderEmpty('No audit logs yet') }}
          pagination={{ pageSize: 20 }}
          expandable={{
            expandedRowRender: (record) => (
              <Descriptions bordered size="small" column={1}>
                <Descriptions.Item label="Remote address">{record.remoteAddress ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="Metadata">
                  <Paragraph code style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                    {stringifyJson(record.metadata)}
                  </Paragraph>
                </Descriptions.Item>
              </Descriptions>
            ),
          }}
        />
      </Card>
    </div>
  );
}