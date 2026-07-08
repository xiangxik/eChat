import { apiRequest } from './client';

export type ProviderType = 'OPENAI_COMPATIBLE' | 'ANTHROPIC' | 'AZURE_OPENAI' | 'GEMINI' | 'OLLAMA' | 'CUSTOM';
export type ModelType = 'CHAT' | 'EMBEDDING' | 'RERANKER';

export interface ProviderConfig {
  id: number;
  name: string;
  type: ProviderType;
  baseUrl?: string;
  apiKeySecretRef?: string;
  hasApiKey: boolean;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProviderConfigRequest {
  name: string;
  type: ProviderType;
  baseUrl?: string;
  apiKeySecretRef?: string;
  apiKey?: string;
  enabled?: boolean;
}

export interface ProviderConnectionTestResult {
  success: boolean;
  providerType: string;
  message: string;
  statusCode?: number;
}

export interface ModelConfig {
  id: number;
  providerId: number;
  providerName: string;
  displayName: string;
  modelName: string;
  modelType: ModelType;
  maxContextTokens?: number;
  defaultTemperature?: number;
  defaultTopP?: number;
  supportsStreaming: boolean;
  enabled: boolean;
  metadata?: Record<string, unknown>;
}

export interface ModelConfigRequest {
  providerId: number;
  displayName: string;
  modelName: string;
  modelType: ModelType;
  maxContextTokens?: number;
  defaultTemperature?: number;
  defaultTopP?: number;
  supportsStreaming?: boolean;
  enabled?: boolean;
  metadata?: Record<string, unknown>;
}

export interface ModelGenerationTestResult {
  success: boolean;
  providerType: string;
  model: string;
  message: string;
  statusCode?: number;
  sampleText?: string;
}

export interface ModelOption {
  displayName: string;
  modelName: string;
  modelType: ModelType;
  maxContextTokens?: number;
  defaultTemperature?: number;
  defaultTopP?: number;
  supportsStreaming: boolean;
}

export interface ChatbotConfig {
  id: number;
  name: string;
  description?: string;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChatbotConfigRequest {
  name: string;
  description?: string;
  enabled?: boolean;
}

export interface ChatbotWorkflowNode {
  id?: number;
  nodeKey: string;
  name: string;
  description?: string;
  dslContent: string;
  version: number;
  modelId: number | null;
  enabled: boolean;
  start: boolean;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChatbotWorkflowTransition {
  id?: number;
  name: string;
  fromNodeKey: string;
  toNodeKey: string;
  priority: number;
  enabled: boolean;
  conditionExpression: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChatbotWorkflow {
  chatbotId: number;
  nodes: ChatbotWorkflowNode[];
  transitions: ChatbotWorkflowTransition[];
}

export interface ChatbotWorkflowRequest {
  nodes: ChatbotWorkflowNode[];
  transitions: ChatbotWorkflowTransition[];
}

export interface ChatbotWorkflowValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

export interface ChatConversation {
  id: number;
  chatbotId: number;
  title?: string | null;
  status: string;
  currentWorkflowNodeId?: number | null;
  currentWorkflowNodeKey?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  role: 'USER' | 'ASSISTANT' | string;
  content: string;
  tokenCount?: number | null;
  metadata?: Record<string, unknown>;
  createdAt?: string;
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
  tokenBudgetReport?: Record<string, unknown> | null;
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
  tokenBudgetReport?: Record<string, unknown> | null;
  contextWarnings: string[];
}

export interface EvalDataset {
  id: number;
  name: string;
  description?: string;
  chatbotId: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface EvalDatasetRequest {
  name: string;
  description?: string;
  chatbotId: number;
}

export interface EvalCase {
  id: number;
  datasetId: number;
  input: string;
  expectedBehavior?: string;
  expectedKeywords: string[];
  metadata?: Record<string, unknown>;
}

export interface EvalCaseRequest {
  input: string;
  expectedBehavior?: string;
  expectedKeywords?: string[];
  metadata?: Record<string, unknown>;
}

export interface EvalRunRequest {
  datasetId: number;
  chatbotId?: number;
  modelId?: number;
  maxEstimatedTokens?: number;
  maxLatencyMillis?: number;
  maxEstimatedCostUsd?: number;
  costPer1kTokensUsd?: number;
  goldenReplay?: boolean;
  forbiddenPhrases?: string[];
  rubric?: Record<string, unknown>;
  releaseGate?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

export interface EvalRun {
  id: number;
  datasetId: number;
  chatbotId: number;
  modelId?: number;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  startedAt?: string;
  finishedAt?: string;
  summary?: Record<string, unknown>;
}

export interface EvalResult {
  id: number;
  runId: number;
  caseId: number;
  output?: string;
  contextSnapshot: Record<string, unknown>;
  tokenBudgetReport: Record<string, unknown>;
  scores: Record<string, unknown>;
  passed: boolean;
  error?: string;
}

export interface AuditLog {
  id: number;
  occurredAt: string;
  actorType: string;
  actorId?: string;
  tenantId: string;
  eventType: string;
  resourceType: string;
  resourceId?: string;
  requestId?: string;
  traceId?: string;
  remoteAddress?: string;
  metadata?: Record<string, unknown>;
}

export interface AdminPermission {
  id: number;
  code: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminPermissionRequest {
  code: string;
  name: string;
  description?: string;
}

export interface AdminRole {
  id: number;
  code: string;
  name: string;
  description?: string;
  systemRole: boolean;
  permissionIds: number[];
  permissions: AdminPermission[];
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminRoleRequest {
  code: string;
  name: string;
  description?: string;
  permissionIds?: number[];
}

export interface AdminUser {
  id: number;
  username: string;
  displayName: string;
  tenantId: string;
  enabled: boolean;
  systemUser: boolean;
  roleIds: number[];
  roles: AdminRole[];
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminUserRequest {
  username: string;
  displayName: string;
  password?: string;
  tenantId: string;
  enabled?: boolean;
  roleIds?: number[];
}

export const providerTypes: ProviderType[] = [
  'OPENAI_COMPATIBLE',
  'ANTHROPIC',
  'AZURE_OPENAI',
  'GEMINI',
  'OLLAMA',
  'CUSTOM',
];

export const modelTypes: ModelType[] = ['CHAT', 'EMBEDDING', 'RERANKER'];

function jsonBody(value: unknown) {
  return JSON.stringify(value);
}

export const adminApi = {
  listProviders: () => apiRequest<ProviderConfig[]>('/api/admin/providers'),
  createProvider: (request: ProviderConfigRequest) =>
    apiRequest<ProviderConfig>('/api/admin/providers', { method: 'POST', body: jsonBody(request) }),
  updateProvider: (id: number, request: ProviderConfigRequest) =>
    apiRequest<ProviderConfig>(`/api/admin/providers/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteProvider: (id: number) => apiRequest<void>(`/api/admin/providers/${id}`, { method: 'DELETE' }),
  testProviderConnection: (id: number) =>
    apiRequest<ProviderConnectionTestResult>(`/api/admin/providers/${id}/test-connection`, { method: 'POST' }),

  listModels: () => apiRequest<ModelConfig[]>('/api/admin/models'),
  listModelOptions: (providerId: number) => apiRequest<ModelOption[]>(`/api/admin/models/options?providerId=${providerId}`),
  createModel: (request: ModelConfigRequest) =>
    apiRequest<ModelConfig>('/api/admin/models', { method: 'POST', body: jsonBody(request) }),
  updateModel: (id: number, request: ModelConfigRequest) =>
    apiRequest<ModelConfig>(`/api/admin/models/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteModel: (id: number) => apiRequest<void>(`/api/admin/models/${id}`, { method: 'DELETE' }),
  testModelGeneration: (id: number) =>
    apiRequest<ModelGenerationTestResult>(`/api/admin/models/${id}/test-generation`, { method: 'POST' }),

  listChatbots: () => apiRequest<ChatbotConfig[]>('/api/admin/chatbots'),
  getChatbot: (id: number) => apiRequest<ChatbotConfig>(`/api/admin/chatbots/${id}`),
  createChatbot: (request: ChatbotConfigRequest) =>
    apiRequest<ChatbotConfig>('/api/admin/chatbots', { method: 'POST', body: jsonBody(request) }),
  updateChatbot: (id: number, request: ChatbotConfigRequest) =>
    apiRequest<ChatbotConfig>(`/api/admin/chatbots/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteChatbot: (id: number) => apiRequest<void>(`/api/admin/chatbots/${id}`, { method: 'DELETE' }),
  getChatbotWorkflow: (chatbotId: number) => apiRequest<ChatbotWorkflow>(`/api/admin/chatbots/${chatbotId}/workflow`),
  saveChatbotWorkflow: (chatbotId: number, request: ChatbotWorkflowRequest) =>
    apiRequest<ChatbotWorkflow>(`/api/admin/chatbots/${chatbotId}/workflow`, { method: 'PUT', body: jsonBody(request) }),
  validateChatbotWorkflow: (chatbotId: number, request: ChatbotWorkflowRequest) =>
    apiRequest<ChatbotWorkflowValidationResult>(`/api/admin/chatbots/${chatbotId}/workflow/validate`, {
      method: 'POST',
      body: jsonBody(request),
    }),
  createChatConversation: (request: ChatConversationCreateRequest) =>
    apiRequest<ChatConversationCreateResponse>('/api/chat/conversations', { method: 'POST', body: jsonBody(request) }),
  sendChatMessage: (conversationId: number, request: ChatMessageRequest) =>
    apiRequest<ChatRuntimeResponse>(`/api/chat/conversations/${conversationId}/messages`, { method: 'POST', body: jsonBody(request) }),

  listEvalDatasets: () => apiRequest<EvalDataset[]>('/api/admin/eval-datasets'),
  createEvalDataset: (request: EvalDatasetRequest) =>
    apiRequest<EvalDataset>('/api/admin/eval-datasets', { method: 'POST', body: jsonBody(request) }),
  listEvalCases: (datasetId: number) => apiRequest<EvalCase[]>(`/api/admin/eval-datasets/${datasetId}/cases`),
  createEvalCase: (datasetId: number, request: EvalCaseRequest) =>
    apiRequest<EvalCase>(`/api/admin/eval-datasets/${datasetId}/cases`, { method: 'POST', body: jsonBody(request) }),
  createEvalRun: (request: EvalRunRequest) =>
    apiRequest<EvalRun>('/api/admin/eval-runs', { method: 'POST', body: jsonBody(request) }),
  getEvalRun: (id: number) => apiRequest<EvalRun>(`/api/admin/eval-runs/${id}`),
  listEvalResults: (id: number) => apiRequest<EvalResult[]>(`/api/admin/eval-runs/${id}/results`),

  listAuditLogs: () => apiRequest<AuditLog[]>('/api/admin/audit-logs'),

  listAdminUsers: () => apiRequest<AdminUser[]>('/api/admin/identity/users'),
  createAdminUser: (request: AdminUserRequest) =>
    apiRequest<AdminUser>('/api/admin/identity/users', { method: 'POST', body: jsonBody(request) }),
  updateAdminUser: (id: number, request: AdminUserRequest) =>
    apiRequest<AdminUser>(`/api/admin/identity/users/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteAdminUser: (id: number) => apiRequest<void>(`/api/admin/identity/users/${id}`, { method: 'DELETE' }),

  listAdminRoles: () => apiRequest<AdminRole[]>('/api/admin/identity/roles'),
  createAdminRole: (request: AdminRoleRequest) =>
    apiRequest<AdminRole>('/api/admin/identity/roles', { method: 'POST', body: jsonBody(request) }),
  updateAdminRole: (id: number, request: AdminRoleRequest) =>
    apiRequest<AdminRole>(`/api/admin/identity/roles/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteAdminRole: (id: number) => apiRequest<void>(`/api/admin/identity/roles/${id}`, { method: 'DELETE' }),

  listAdminPermissions: () => apiRequest<AdminPermission[]>('/api/admin/identity/permissions'),
  createAdminPermission: (request: AdminPermissionRequest) =>
    apiRequest<AdminPermission>('/api/admin/identity/permissions', { method: 'POST', body: jsonBody(request) }),
  updateAdminPermission: (id: number, request: AdminPermissionRequest) =>
    apiRequest<AdminPermission>(`/api/admin/identity/permissions/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteAdminPermission: (id: number) => apiRequest<void>(`/api/admin/identity/permissions/${id}`, { method: 'DELETE' }),
};
