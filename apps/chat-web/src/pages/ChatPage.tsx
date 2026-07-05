import { useQuery } from '@tanstack/react-query';
import { create } from 'zustand';

import { fetchHealth } from '../api/client';
import { Composer } from '../components/Composer';
import { MessageList } from '../components/MessageList';
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

const chatbotId = Number.parseInt(import.meta.env.VITE_CHATBOT_ID ?? '1', 10);
const chatbotName = import.meta.env.VITE_CHATBOT_NAME ?? 'eChat Assistant';

export function ChatPage() {
  const { draft, setDraft, clearDraft } = useChatDraft();
  const healthQuery = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false });
  const chatSession = useChatSession({ chatbotId: Number.isNaN(chatbotId) ? 1 : chatbotId, chatbotName });
  const conversationTitle = chatSession.conversation?.title || 'New conversation';
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
            <span className="chatbot-name">{chatbotName}</span>
            <h1>{conversationTitle}</h1>
          </div>
          <div className="header-actions">
            <span className={apiOnline ? 'status-pill status-pill-up' : 'status-pill'}>
              {apiOnline ? 'API online' : 'API unavailable'}
            </span>
            <button type="button" className="secondary-button" onClick={chatSession.startNewConversation}>
              New chat
            </button>
          </div>
        </header>

        <MessageList
          messages={chatSession.messages}
          chatbotName={chatbotName}
          isLoading={chatSession.isLoadingMessages}
          isStreaming={chatSession.isStreaming}
        />

        <Composer
          value={draft}
          disabled={chatSession.isSending}
          isStreaming={chatSession.isStreaming}
          error={chatSession.error}
          canRetry={Boolean(chatSession.lastUserMessage)}
          onChange={setDraft}
          onSubmit={handleSubmit}
          onStop={chatSession.stopStreaming}
          onRetry={chatSession.retryLastMessage}
        />
      </section>
    </main>
  );
}