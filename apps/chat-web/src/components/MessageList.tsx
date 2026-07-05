import { useEffect, useRef } from 'react';

import type { ChatMessageView } from '../services/chat';
import { MessageBubble } from './MessageBubble';

interface MessageListProps {
  messages: ChatMessageView[];
  chatbotName: string;
  isLoading: boolean;
  isStreaming: boolean;
}

export function MessageList({ messages, chatbotName, isLoading, isStreaming }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [isStreaming, messages]);

  if (isLoading) {
    return <div className="message-list-state">Loading conversation...</div>;
  }

  return (
    <div className="message-list" aria-live="polite">
      {messages.length === 0 ? (
        <div className="empty-state">
          <span>No messages yet</span>
        </div>
      ) : (
        messages.map((message) => <MessageBubble key={message.clientId} message={message} chatbotName={chatbotName} />)
      )}
      <div ref={bottomRef} />
    </div>
  );
}