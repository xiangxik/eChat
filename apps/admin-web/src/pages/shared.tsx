import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, Select, Space, Tag, Tooltip, Typography } from 'antd';
import type { ReactNode } from 'react';

import type { AdminListQuery, AdminListValue } from '../api/admin';

const { Text } = Typography;

export interface AdminSearchField {
  name: string;
  label: string;
  type?: 'text' | 'select';
  options?: { label: string; value: string | number | boolean }[];
}

export interface AdminTableSort {
  sortField?: string;
  sortOrder?: 'ascend' | 'descend' | null;
}

export function AdminSearchPanel({
  fields,
  initialValues,
  onSearch,
}: {
  fields: AdminSearchField[];
  initialValues?: Record<string, AdminListValue>;
  onSearch: (values: Record<string, AdminListValue>) => void;
}) {
  const [form] = Form.useForm<Record<string, AdminListValue>>();

  return (
    <div className="admin-search-card">
      <Form form={form} layout="inline" initialValues={initialValues} onFinish={onSearch}>
        {fields.map((field) => (
          <Form.Item
            key={field.name}
            className={field.type === 'select' ? 'admin-search-item admin-search-item-select' : 'admin-search-item'}
            name={field.name}
            label={field.label}
          >
            {field.type === 'select' ? (
              <Select allowClear className="admin-search-select" options={field.options} />
            ) : (
              <Input allowClear className="admin-search-input" />
            )}
          </Form.Item>
        ))}
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              Search
            </Button>
            <Button
              onClick={() => {
                form.resetFields();
                onSearch({});
              }}
            >
              Reset
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </div>
  );
}

export function buildListQuery(filters: Record<string, AdminListValue>, sort: AdminTableSort): AdminListQuery {
  return { ...filters, sortField: sort.sortField, sortOrder: sort.sortOrder };
}

export function tableSort(sorter: unknown): AdminTableSort {
  const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
  if (!activeSorter || typeof activeSorter !== 'object') {
    return {};
  }
  const record = activeSorter as { field?: unknown; columnKey?: unknown; order?: unknown };
  const field = typeof record.field === 'string' ? record.field : typeof record.columnKey === 'string' ? record.columnKey : undefined;
  const order = record.order === 'ascend' || record.order === 'descend' ? record.order : null;
  return { sortField: order ? field : undefined, sortOrder: order };
}

export function EnabledTag({ enabled }: { enabled: boolean }) {
  const label = enabled ? 'Enabled' : 'Disabled';
  return (
    <Tooltip title={label}>
      <Tag
        aria-label={label}
        className="status-icon-tag"
        color={enabled ? 'success' : 'default'}
        icon={enabled ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
      />
    </Tooltip>
  );
}

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }

  return <Alert type="error" showIcon message={error instanceof Error ? error.message : 'Request failed'} />;
}

export function PageSectionHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="section-header">
      <Text className="section-title" type="secondary">{title}</Text>
      {actions && <Space className="section-actions">{actions}</Space>}
    </div>
  );
}

export function ReadinessTag({ ready }: { ready: boolean }) {
  return ready ? <Tag color="success">Ready</Tag> : <Tag color="default">Needs setup</Tag>;
}
