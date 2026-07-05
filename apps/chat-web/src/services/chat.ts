import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import {
  ApiClientError,
  type ChatConversation,
  type ChatConversationCreateRequest,
  type ChatMessage,
  createConversation,
  listConversationMessages,
  streamChatMessage,
} from '../api/client';

export type ChatMessageRole = 'USER' | 'ASSISTANT';
export type ChatMessageStatus = 'sent' | 'streaming' | 'error';

export interface ChatMessageView {
  clientId: string;
  id?: number;
  conversationId?: number;
  role: ChatMessageRole;
  content: string;
  createdAt?: string;
  status: ChatMessageStatus;
  error?: string;
}

export interface ChatSessionOptions {
  chatbotId: number;
  chatbotName: string;
}

export function useCreateConversation() {
  return useMutation({
    mutationFn: (request: ChatConversationCreateRequest) => createConversation(request),
  });
}

export function useConversationMessages(conversationId?: number) {
  return useQuery({
    queryKey: ['chatMessages', conversationId],
    queryFn: () => listConversationMessages(conversationId!),
    enabled: typeof conversationId === 'number',
    staleTime: 10_000,
  });
}

export function useChatSession({ chatbotId, chatbotName }: ChatSessionOptions) {
  const queryClient = useQueryClient();
  const createConversationMutation = useCreateConversation();
  const [conversation, setConversation] = useState<ChatConversation | null>(null);
  const [messages, setMessages] = useState<ChatMessageView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [activeRequestId, setActiveRequestId] = useState<string | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const lastUserMessageRef = useRef<string | null>(null);
  const messagesQuery = useConversationMessages(conversation?.id);

  useEffect(() => {
    if (messagesQuery.data && messages.length === 0) {
      setMessages(messagesQuery.data.map(toMessageView));
    }
  }, [messages.length, messagesQuery.data]);

  async function submitMessage(content: string) {
    const message = content.trim();
    if (!message || isStreaming || createConversationMutation.isPending) {
      return;
    }

    let activeConversation = conversation;
    let assistantClientId: string | null = null;
    setError(null);
    setIsStreaming(true);
    lastUserMessageRef.current = message;

    try {
      if (!activeConversation) {
        const createdConversation = await createConversationMutation.mutateAsync({
          chatbotId,
          title: createConversationTitle(message),
          metadata: { source: 'chat-web' },
        });
        activeConversation = toConversation(createdConversation);
        setConversation(activeConversation);
      }

      const userClientId = createClientId('user');
      assistantClientId = createClientId('assistant');
      setMessages((currentMessages) => [
        ...currentMessages,
        {
          clientId: userClientId,
          role: 'USER',
          content: message,
          status: 'sent',
          createdAt: new Date().toISOString(),
        },
        {
          clientId: assistantClientId!,
          role: 'ASSISTANT',
          content: '',
          status: 'streaming',
          createdAt: new Date().toISOString(),
        },
      ]);

      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      await streamChatMessage(
        activeConversation.id,
        {
          message,
          metadata: {
            source: 'chat-web',
            chatbotName,
          },
        },
        {
          signal: abortController.signal,
          onEvent: (event) => {
            if (event.requestId) {
              setActiveRequestId(event.requestId);
            }

            if (event.type === 'error') {
              throw new Error(readStreamError(event.metadata));
            }

            if (event.type === 'token' && event.token) {
              appendAssistantContent(assistantClientId!, event.token);
            }

            if (event.type === 'message_delta' && typeof event.messageDelta === 'string') {
              replaceAssistantContent(assistantClientId!, event.messageDelta, 'streaming');
            }

            if (event.type === 'message_end') {
              const finalContent = event.message?.content ?? event.messageDelta ?? '';
              setMessages((currentMessages) =>
                currentMessages.map((currentMessage) =>
                  currentMessage.clientId === assistantClientId
                    ? {
                        ...currentMessage,
                        id: event.message?.id ?? currentMessage.id,
                        conversationId: event.conversationId,
                        content: finalContent || currentMessage.content,
                        createdAt: event.message?.createdAt ?? currentMessage.createdAt,
                        status: 'sent',
                      }
                    : currentMessage,
                ),
              );
            }
          },
        },
      );

      await queryClient.invalidateQueries({ queryKey: ['chatMessages', activeConversation.id] });
    } catch (unknownError) {
      const messageText = toErrorMessage(unknownError);
      setError(messageText);
      if (assistantClientId) {
        setMessages((currentMessages) =>
          currentMessages.map((currentMessage) =>
            currentMessage.clientId === assistantClientId
              ? { ...currentMessage, status: 'error', error: messageText, content: currentMessage.content }
              : currentMessage,
          ),
        );
      }
    } finally {
      abortControllerRef.current = null;
      setActiveRequestId(null);
      setIsStreaming(false);
    }
  }

  function startNewConversation() {
    abortControllerRef.current?.abort();
    setConversation(null);
    setMessages([]);
    setError(null);
    setActiveRequestId(null);
    lastUserMessageRef.current = null;
  }

  function stopStreaming() {
    abortControllerRef.current?.abort();
  }

  function retryLastMessage() {
    if (lastUserMessageRef.current) {
      void submitMessage(lastUserMessageRef.current);
    }
  }

  function appendAssistantContent(clientId: string, token: string) {
    setMessages((currentMessages) =>
      currentMessages.map((currentMessage) =>
        currentMessage.clientId === clientId
          ? { ...currentMessage, content: `${currentMessage.content}${token}`, status: 'streaming' }
          : currentMessage,
      ),
    );
  }

  function replaceAssistantContent(clientId: string, content: string, status: ChatMessageStatus) {
    setMessages((currentMessages) =>
      currentMessages.map((currentMessage) =>
        currentMessage.clientId === clientId ? { ...currentMessage, content, status } : currentMessage,
      ),
    );
  }

  return {
    conversation,
    messages,
    error,
    isLoadingMessages: messagesQuery.isLoading,
    isSending: isStreaming || createConversationMutation.isPending,
    isStreaming,
    activeRequestId,
    lastUserMessage: lastUserMessageRef.current,
    submitMessage,
    startNewConversation,
    stopStreaming,
    retryLastMessage,
  };
}

function toConversation(response: ChatConversation): ChatConversation {
  return {
    id: response.id,
    chatbotId: response.chatbotId,
    title: response.title,
    status: response.status,
    createdAt: response.createdAt,
    updatedAt: response.updatedAt,
  };
}

function toMessageView(message: ChatMessage): ChatMessageView {
  return {
    clientId: `message-${message.id}`,
    id: message.id,
    conversationId: message.conversationId,
    role: message.role === 'USER' ? 'USER' : 'ASSISTANT',
    content: message.content,
    createdAt: message.createdAt,
    status: 'sent',
  };
}

function createConversationTitle(message: string): string {
  const normalized = message.replace(/\s+/g, ' ').trim();
  return normalized.length > 64 ? `${normalized.slice(0, 61)}...` : normalized;
}

function createClientId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function readStreamError(metadata: Record<string, unknown>): string {
  return typeof metadata.message === 'string' ? metadata.message : 'Assistant response failed.';
}

function toErrorMessage(error: unknown): string {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return 'Generation stopped.';
  }

  if (error instanceof ApiClientError) {
    return error.code ? `${error.message} (${error.code})` : error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'Chat request failed.';
}