import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Alert, Tag } from 'antd';

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
