export type Locale = 'zh' | 'en';

export interface PromptSuggestionCopy {
  label: string;
  value: string;
}

export interface ComposerCopy {
  errorLabel: string;
  inputLabel: string;
  placeholder: string;
  retry: string;
  send: string;
  sending: string;
  stop: string;
}

export interface MessageListCopy {
  emptyDescription: string;
  emptyTitle: string;
  loading: string;
  suggestionsLabel: string;
  suggestions: PromptSuggestionCopy[];
}

export interface MessageBubbleCopy {
  assistantLabel: string;
  error: string;
  streaming: string;
  userLabel: string;
}

export interface ChatCopy {
  apiOnline: string;
  apiUnavailable: string;
  languageGroupLabel: string;
  composer: ComposerCopy;
  messageBubble: MessageBubbleCopy;
  messageList: MessageListCopy;
}

export const localeStorageKey = 'echat.chat.locale';

export const chatCopy: Record<Locale, ChatCopy> = {
  zh: {
    apiOnline: 'API 在线',
    apiUnavailable: 'API 不可用',
    languageGroupLabel: '切换界面语言',
    composer: {
      errorLabel: '消息发送失败',
      inputLabel: '输入消息',
      placeholder: '输入问题，按 Enter 发送',
      retry: '重试',
      send: '发送',
      sending: '发送中',
      stop: '停止生成',
    },
    messageBubble: {
      assistantLabel: '助手',
      error: '错误',
      streaming: '生成中',
      userLabel: '用户',
    },
    messageList: {
      emptyDescription: '开始一次清晰的对话，提出问题、整理上下文，或让助手帮你生成下一步方案。',
      emptyTitle: '今天想聊点什么？',
      loading: '正在加载对话...',
      suggestionsLabel: '建议问题',
      suggestions: [
        { label: '总结这段内容', value: '请帮我总结下面这段内容，并列出关键要点：' },
        { label: '制定执行计划', value: '请基于下面的目标，帮我制定一个可执行计划：' },
        { label: '优化表达', value: '请帮我把下面这段话改得更简洁、专业：' },
      ],
    },
  },
  en: {
    apiOnline: 'API online',
    apiUnavailable: 'API unavailable',
    languageGroupLabel: 'Switch interface language',
    composer: {
      errorLabel: 'Message failed',
      inputLabel: 'Message input',
      placeholder: 'Ask anything, press Enter to send',
      retry: 'Retry',
      send: 'Send',
      sending: 'Sending',
      stop: 'Stop',
    },
    messageBubble: {
      assistantLabel: 'Assistant',
      error: 'Error',
      streaming: 'Streaming',
      userLabel: 'User',
    },
    messageList: {
      emptyDescription: 'Start with a focused question, organize context, or ask the assistant to shape your next step.',
      emptyTitle: 'What would you like to work on?',
      loading: 'Loading conversation...',
      suggestionsLabel: 'Suggested prompts',
      suggestions: [
        { label: 'Summarize content', value: 'Please summarize the following content and list the key points:' },
        { label: 'Create an action plan', value: 'Based on the goal below, please create a practical action plan:' },
        { label: 'Polish writing', value: 'Please make the following text more concise and professional:' },
      ],
    },
  },
};

export function isLocale(value: string | null): value is Locale {
  return value === 'zh' || value === 'en';
}

export function getInitialLocale(): Locale {
  if (typeof window === 'undefined') {
    return 'en';
  }

  try {
    const storedLocale = window.localStorage.getItem(localeStorageKey);
    if (isLocale(storedLocale)) {
      return storedLocale;
    }
  } catch {
    return 'en';
  }

  return 'en';
}

export function persistLocale(locale: Locale) {
  if (typeof window === 'undefined') {
    return;
  }

  try {
    window.localStorage.setItem(localeStorageKey, locale);
  } catch {
    // Ignore storage failures; the in-memory selection still updates for this session.
  }
}