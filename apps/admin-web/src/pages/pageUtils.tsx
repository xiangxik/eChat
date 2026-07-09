import { Empty } from 'antd';

function padDatePart(value: number) {
  return String(value).padStart(2, '0');
}

export function formatDate(value?: string | number | Date) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  return [
    date.getFullYear(),
    padDatePart(date.getMonth() + 1),
    padDatePart(date.getDate()),
  ].join('-') + ` ${[
    padDatePart(date.getHours()),
    padDatePart(date.getMinutes()),
    padDatePart(date.getSeconds()),
  ].join(':')}`;
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