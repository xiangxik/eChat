import { readRuntimeEnv } from '../runtimeEnv';

const apiBaseUrl = (readRuntimeEnv('VITE_API_BASE_URL') ?? '').replace(/\/$/, '');

const jsonHeaders = {
  'Content-Type': 'application/json',
};

export interface HealthResponse {
  status: string;
  service: string;
  version: string;
}

export interface ChatConversation {
  id: number;
  chatbotId: number;
  title: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  role: 'USER' | 'ASSISTANT' | string;
  content: string;
  tokenCount: number | null;
  metadata: Record<string, unknown>;
  createdAt: string;
}

export interface TokenBudgetReport {
  totalEstimatedTokens?: number;
  maxTokens?: number;
  remainingTokens?: number;
  [key: string]: unknown;
}

export interface ChatConversationCreateRequest {
  chatbotId: number;
  message?: string;
  userId?: string;
  anonymousSessionId?: string;
  title?: string;
  metadata?: Record<string, unknown>;
}

export interface ChatConversationCreateResponse extends ChatConversation {
  requestId: string;
  traceId: string;
  userMessage: ChatMessage | null;
  assistantMessage: ChatMessage | null;
  tokenBudgetReport: TokenBudgetReport | null;
  contextWarnings: string[];
}

export interface ChatMessageRequest {
  message: string;
  metadata?: Record<string, unknown>;
}

export interface ChatRuntimeResponse {
  requestId: string;
  traceId: string;
  conversation: ChatConversation;
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
  tokenBudgetReport: TokenBudgetReport | null;
  contextWarnings: string[];
}

export type ChatStreamEventType = 'message_start' | 'token' | 'message_delta' | 'message_end' | 'error';

export interface ChatStreamEvent {
  type: ChatStreamEventType | string;
  requestId: string;
  traceId: string;
  conversationId: number;
  messageId: number | null;
  token: string | null;
  messageDelta: string | null;
  message: ChatMessage | null;
  tokenBudgetReport: TokenBudgetReport | null;
  metadata: Record<string, unknown>;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly details?: unknown;

  constructor(message: string, status: number, code?: string, details?: unknown) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export async function fetchHealth(): Promise<HealthResponse> {
  return requestJson<HealthResponse>('/api/health');
}

export async function createConversation(
  request: ChatConversationCreateRequest,
): Promise<ChatConversationCreateResponse> {
  return requestJson<ChatConversationCreateResponse>('/api/chat/conversations', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(request),
  });
}

export async function listConversationMessages(conversationId: number): Promise<ChatMessage[]> {
  return requestJson<ChatMessage[]>(`/api/chat/conversations/${conversationId}/messages`);
}

export async function sendMessage(conversationId: number, request: ChatMessageRequest): Promise<ChatRuntimeResponse> {
  return requestJson<ChatRuntimeResponse>(`/api/chat/conversations/${conversationId}/messages`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(request),
  });
}

export interface StreamChatMessageOptions {
  signal?: AbortSignal;
  requestId?: string;
  onEvent: (event: ChatStreamEvent) => void;
}

export async function streamChatMessage(
  conversationId: number,
  request: ChatMessageRequest,
  options: StreamChatMessageOptions,
): Promise<void> {
  const response = await fetch(`${apiBaseUrl}/api/chat/conversations/${conversationId}/stream`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      Accept: 'text/event-stream',
      'X-Request-Id': options.requestId ?? createRequestId(),
    },
    body: JSON.stringify(request),
    signal: options.signal,
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (!response.body) {
    throw new ApiClientError('Streaming is not available in this browser.', response.status);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done });
    buffer = dispatchSseEvents(buffer, done, options.onEvent);

    if (done) {
      break;
    }
  }
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, init);

  if (!response.ok) {
    throw await toApiError(response);
  }

  return response.json() as Promise<T>;
}

async function toApiError(response: Response): Promise<ApiClientError> {
  const fallback = `Request failed with status ${response.status}`;

  try {
    const body = (await response.json()) as Record<string, unknown>;
    const message = readString(body.message) ?? readString(body.error) ?? fallback;
    const code = readString(body.code);
    return new ApiClientError(message, response.status, code, body);
  } catch {
    return new ApiClientError(fallback, response.status);
  }
}

function dispatchSseEvents(
  rawBuffer: string,
  flush: boolean,
  onEvent: (event: ChatStreamEvent) => void,
): string {
  const normalizedBuffer = rawBuffer.replace(/\r\n/g, '\n');
  const blocks = normalizedBuffer.split('\n\n');
  const remainder = flush ? '' : (blocks.pop() ?? '');

  for (const block of blocks) {
    const event = parseSseBlock(block);
    if (event) {
      onEvent(event);
    }
  }

  return remainder;
}

function parseSseBlock(block: string): ChatStreamEvent | null {
  if (!block.trim()) {
    return null;
  }

  let eventName = 'message';
  const dataLines: string[] = [];

  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue;
    }

    const separatorIndex = line.indexOf(':');
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex);
    let value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1);

    if (value.startsWith(' ')) {
      value = value.slice(1);
    }

    if (field === 'event') {
      eventName = value;
    }

    if (field === 'data') {
      dataLines.push(value);
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  const payload = JSON.parse(dataLines.join('\n')) as ChatStreamEvent;
  return {
    ...payload,
    type: payload.type ?? eventName,
  };
}

function createRequestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `chat-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function readString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}