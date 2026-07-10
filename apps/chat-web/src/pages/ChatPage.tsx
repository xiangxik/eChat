import { useQuery } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'react-router-dom';
import { create } from 'zustand';

import { fetchHealth } from '../api/client';
import { Composer } from '../components/Composer';
import { MessageList } from '../components/MessageList';
import { chatCopy, getInitialLocale, persistLocale } from '../i18n';
import type { Locale } from '../i18n';
import { readRuntimeEnv } from '../runtimeEnv';
import { useChatSession } from '../services/chat';

interface ChatDraftState {
  draft: string;
  setDraft: (draft: string) => void;
  clearDraft: () => void;
}

const useChatDraft = create<ChatDraftState>((set) => ({
  draft: '',
  setDraft: (draft) => set({ draft }),
  clearDraft: () => set({ draft: '' }),
}));

interface ChatLocaleState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
}

const useChatLocale = create<ChatLocaleState>((set) => ({
  locale: getInitialLocale(),
  setLocale: (locale) => {
    persistLocale(locale);
    set({ locale });
  },
}));

const fallbackChatbotId = Number.parseInt(readRuntimeEnv('VITE_CHATBOT_ID') ?? '1', 10);
const fallbackChatbotName = readRuntimeEnv('VITE_CHATBOT_NAME') ?? 'eChat Assistant';

export function ChatPage() {
  const params = useParams();
  const [searchParams] = useSearchParams();
  const { draft, setDraft, clearDraft } = useChatDraft();
  const { locale, setLocale } = useChatLocale();
  const copy = chatCopy[locale];
  const chatbotId = readChatbotId(params.chatbotId ?? searchParams.get('chatbotId'));
  const chatbotName = searchParams.get('chatbotName')?.trim() || fallbackChatbotName;
  const healthQuery = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false });
  const chatSession = useChatSession({ chatbotId, chatbotName });
  const apiOnline = healthQuery.data?.status === 'UP';

  async function handleSubmit() {
    const message = draft.trim();
    if (!message) {
      return;
    }

    clearDraft();
    await chatSession.submitMessage(message);
  }

  return (
    <main className="chat-shell">
      <section className="chat-workspace" aria-label="Chatbot conversation">
        <header className="chat-header">
          <div className="chat-identity">
            <span className="assistant-mark" aria-hidden="true">
              <img src="/brand-icon.svg" alt="" />
            </span>
            <div className="chat-identity-copy">
              <span className="chatbot-name">{chatbotName}</span>
            </div>
          </div>
          <div className="header-actions">
            <span
              className={apiOnline ? 'status-pill status-pill-up' : 'status-pill'}
              title={apiOnline ? copy.apiOnline : copy.apiUnavailable}
              aria-label={apiOnline ? copy.apiOnline : copy.apiUnavailable}
            >
              <span className="status-dot" aria-hidden="true" />
            </span>
            <div className="language-toggle" role="group" aria-label={copy.languageGroupLabel}>
              <button
                type="button"
                className={locale === 'en' ? 'language-option language-option-active' : 'language-option'}
                onClick={() => setLocale('en')}
                aria-pressed={locale === 'en'}
              >
                EN
              </button>
              <span className="language-separator" aria-hidden="true">
                |
              </span>
              <button
                type="button"
                className={locale === 'zh' ? 'language-option language-option-active' : 'language-option'}
                onClick={() => setLocale('zh')}
                aria-pressed={locale === 'zh'}
              >
                中文
              </button>
            </div>
          </div>
        </header>

        <MessageList
          messages={chatSession.messages}
          locale={locale}
          isLoading={chatSession.isLoadingMessages}
          isStreaming={chatSession.isStreaming}
          copy={copy.messageList}
          messageBubbleCopy={copy.messageBubble}
          onSuggestionSelect={setDraft}
        />

        <Composer
          value={draft}
          disabled={chatSession.isSending}
          isStreaming={chatSession.isStreaming}
          error={chatSession.error}
          canRetry={Boolean(chatSession.lastUserMessage)}
          copy={copy.composer}
          onChange={setDraft}
          onSubmit={handleSubmit}
          onStop={chatSession.stopStreaming}
          onRetry={chatSession.retryLastMessage}
        />
      </section>
    </main>
  );
}

function readChatbotId(value: string | null | undefined) {
  const urlChatbotId = Number.parseInt(value ?? '', 10);
  if (Number.isFinite(urlChatbotId) && urlChatbotId > 0) {
    return urlChatbotId;
  }
  return Number.isNaN(fallbackChatbotId) ? 1 : fallbackChatbotId;
}