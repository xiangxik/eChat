import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Descriptions, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { adminApi, type AuditLog } from '../api/admin';
import { fetchAdminSession } from '../api/client';
import { AdminSearchPanel, buildListQuery, ErrorAlert, PageSectionHeader, tableSort, type AdminTableSort } from './shared';
import { formatDate, renderEmpty, stringifyJson } from './pageUtils';
import { useState } from 'react';

const { Paragraph, Text } = Typography;

export function AuditLogsPage() {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [sort, setSort] = useState<AdminTableSort>({ sortField: 'occurredAt', sortOrder: 'descend' });
  const listQuery = buildListQuery(filters, sort);
  const auditLogsQuery = useQuery({ queryKey: ['audit-logs', listQuery], queryFn: () => adminApi.listAuditLogs(listQuery) });
  const sessionQuery = useQuery({ queryKey: ['admin-session'], queryFn: fetchAdminSession, retry: false });
  const auditLogs = auditLogsQuery.data ?? [];
  const showTenant = Boolean(sessionQuery.data?.superAdmin);

  const columns: ColumnsType<AuditLog> = [
    {
      title: 'Time',
      dataIndex: 'occurredAt',
      width: 155,
      sorter: true,
      render: (value: string) => formatDate(value),
    },
    {
      title: 'Event',
      dataIndex: 'eventType',
      width: 200,
      sorter: true,
      render: (value: string) => <Tag color={value.includes('FAILED') ? 'error' : 'processing'}>{value}</Tag>,
    },
    {
      title: 'Resource',
      dataIndex: 'resourceType',
      sorter: true,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.resourceType}</Text>
          <Text type="secondary">{record.resourceId ?? '-'}</Text>
        </Space>
      ),
    },
    {
      title: 'Actor',
      dataIndex: 'actorId',
      sorter: true,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>{record.actorType}</Text>
          <Text type="secondary">{record.actorId ?? '-'}</Text>
          {showTenant && <Text type="secondary">tenant: {record.tenantId}</Text>}
        </Space>
      ),
    },
    {
      title: 'Trace',
      dataIndex: 'requestId',
      sorter: true,
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
      <AdminSearchPanel
        fields={[
          { name: 'search', label: 'Keyword' },
          ...(showTenant ? [{ name: 'tenantId', label: 'Tenant' }] : []),
          { name: 'eventType', label: 'Event' },
          { name: 'resourceType', label: 'Resource' },
          { name: 'actorType', label: 'Actor Type', type: 'select', options: [{ label: 'Admin', value: 'ADMIN' }, { label: 'Runtime', value: 'RUNTIME' }] },
          { name: 'actorId', label: 'Actor' },
        ]}
        initialValues={filters}
        onSearch={(values) => setFilters(values as Record<string, string>)}
      />

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
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={auditLogs}
          loading={auditLogsQuery.isLoading}
          locale={{ emptyText: renderEmpty('No audit logs yet') }}
          pagination={{ pageSize: 20, size: 'small' }}
          onChange={(_, __, sorter) => setSort(tableSort(sorter))}
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