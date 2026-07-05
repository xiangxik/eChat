import { Empty } from 'antd';

export function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-';
}

export function renderEmpty(description = 'No data') {
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />;
}

export function parseJsonObject(value: string, fieldName: string): Record<string, unknown> | undefined {
  const trimmedValue = value.trim();
  if (!trimmedValue) {
    return undefined;
  }

  const parsedValue = JSON.parse(trimmedValue) as unknown;
  if (!parsedValue || Array.isArray(parsedValue) || typeof parsedValue !== 'object') {
    throw new Error(`${fieldName} must be a JSON object.`);
  }

  return parsedValue as Record<string, unknown>;
}

export function stringifyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}