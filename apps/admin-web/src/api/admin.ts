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
  defaultModelId?: number;
  contextPolicyId?: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChatbotConfigRequest {
  name: string;
  description?: string;
  defaultModelId?: number;
  contextPolicyId?: number;
  enabled?: boolean;
}

export interface ContextPolicy {
  id: number;
  name: string;
  description?: string;
  dslContent: string;
  version: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ContextPolicyRequest {
  name: string;
  description?: string;
  dslContent: string;
  version?: number;
  enabled?: boolean;
}

export interface ContextPolicyDslError {
  line: number;
  tag: string;
  reason: string;
}

export interface ContextPolicyValidationResult {
  valid: boolean;
  errors: ContextPolicyDslError[];
  warnings: string[];
  policyName?: string;
  maxTokens: number;
}

export interface ContextMessage {
  role: string;
  content: string;
  metadata?: Record<string, unknown>;
}

export interface ContextMemoryItem {
  content: string;
  score?: number;
  metadata?: Record<string, unknown>;
}

export interface ContextAssemblyRequest {
  chatbotId?: number;
  conversationId?: number;
  userId?: string;
  latestUserMessage?: string;
  metadata?: Record<string, unknown>;
  conversation?: ContextMessage[];
  shortTermMemory?: ContextMemoryItem[];
  longTermMemory?: ContextMemoryItem[];
  userProfile?: Record<string, unknown>;
  retrievalResults?: ContextMemoryItem[];
  toolResults?: ContextMemoryItem[];
  runtime?: Record<string, unknown>;
}

export interface ContextSection {
  name: string;
  role: string;
  content: string;
  estimatedTokens: number;
  included: boolean;
}

export interface TokenBudgetReport {
  maxTokens: number;
  reservedBySection: Record<string, number>;
  actualTokensBySection: Record<string, number>;
  totalEstimatedTokens: number;
  truncatedSections: string[];
}

export interface ContextAssemblyResult {
  messages: ContextMessage[];
  sections: ContextSection[];
  tokenBudgetReport: TokenBudgetReport;
  warnings: string[];
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
  createChatbot: (request: ChatbotConfigRequest) =>
    apiRequest<ChatbotConfig>('/api/admin/chatbots', { method: 'POST', body: jsonBody(request) }),
  updateChatbot: (id: number, request: ChatbotConfigRequest) =>
    apiRequest<ChatbotConfig>(`/api/admin/chatbots/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteChatbot: (id: number) => apiRequest<void>(`/api/admin/chatbots/${id}`, { method: 'DELETE' }),

  listContextPolicies: () => apiRequest<ContextPolicy[]>('/api/admin/context-policies'),
  createContextPolicy: (request: ContextPolicyRequest) =>
    apiRequest<ContextPolicy>('/api/admin/context-policies', { method: 'POST', body: jsonBody(request) }),
  updateContextPolicy: (id: number, request: ContextPolicyRequest) =>
    apiRequest<ContextPolicy>(`/api/admin/context-policies/${id}`, { method: 'PUT', body: jsonBody(request) }),
  deleteContextPolicy: (id: number) => apiRequest<void>(`/api/admin/context-policies/${id}`, { method: 'DELETE' }),
  validateContextPolicy: (dslContent: string) =>
    apiRequest<ContextPolicyValidationResult>('/api/admin/context-policies/validate', {
      method: 'POST',
      body: jsonBody({ dslContent }),
    }),
  previewContextPolicy: (id: number, request: ContextAssemblyRequest) =>
    apiRequest<ContextAssemblyResult>(`/api/admin/context-policies/${id}/preview`, {
      method: 'POST',
      body: jsonBody(request),
    }),
};