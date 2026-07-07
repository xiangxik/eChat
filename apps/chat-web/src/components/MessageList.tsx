import { useEffect, useRef } from 'react';

import type { Locale, MessageBubbleCopy, MessageListCopy } from '../i18n';
import type { ChatMessageView } from '../services/chat';
import { MessageBubble } from './MessageBubble';

interface MessageListProps {
  messages: ChatMessageView[];
  locale: Locale;
  isLoading: boolean;
  isStreaming: boolean;
  copy: MessageListCopy;
  messageBubbleCopy: MessageBubbleCopy;
  onSuggestionSelect: (value: string) => void;
}

export function MessageList({
  messages,
  locale,
  isLoading,
  isStreaming,
  copy,
  messageBubbleCopy,
  onSuggestionSelect,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [isStreaming, messages]);

  if (isLoading) {
    return (
      <div className="message-list-state">
        <span className="state-spinner" aria-hidden="true" />
        <span>{copy.loading}</span>
      </div>
    );
  }

  return (
    <div className="message-list" aria-live="polite">
      {messages.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-mark" aria-hidden="true">
            <img src="/brand-icon.svg" alt="" />
          </div>
          <div className="empty-state-copy">
            <h2>{copy.emptyTitle}</h2>
            <p>{copy.emptyDescription}</p>
          </div>
          <div className="suggestion-list" aria-label={copy.suggestionsLabel}>
            {copy.suggestions.map((suggestion) => (
              <button
                key={suggestion.value}
                type="button"
                className="suggestion-chip"
                onClick={() => onSuggestionSelect(suggestion.value)}
              >
                {suggestion.label}
              </button>
            ))}
          </div>
        </div>
      ) : (
        messages.map((message) => (
          <MessageBubble key={message.clientId} message={message} locale={locale} copy={messageBubbleCopy} />
        ))
      )}
      <div ref={bottomRef} />
    </div>
  );
}