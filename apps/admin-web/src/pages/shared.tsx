import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Alert, Space, Switch, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Text } = Typography;

export const ADMIN_TABLE_SCROLL_Y = 'calc(100vh - 148px)';

export function EnabledTag({ enabled }: { enabled: boolean }) {
  return enabled ? (
    <Tag icon={<CheckCircleOutlined />} color="success">
      Enabled
    </Tag>
  ) : (
    <Tag icon={<CloseCircleOutlined />} color="default">
      Disabled
    </Tag>
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

export function EnabledControl({
  enabled,
  loading,
  onChange,
}: {
  enabled: boolean;
  loading?: boolean;
  onChange: (enabled: boolean) => void;
}) {
  return (
    <Space className="enabled-control" size={8}>
      <EnabledTag enabled={enabled} />
      <Switch size="small" checked={enabled} loading={loading} onChange={onChange} />
    </Space>
  );
}
