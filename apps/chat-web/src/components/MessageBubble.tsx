import { RobotOutlined, UserOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import type { Locale, MessageBubbleCopy } from '../i18n';
import type { ChatMessageView } from '../services/chat';

interface MessageBubbleProps {
  message: ChatMessageView;
  locale: Locale;
  copy: MessageBubbleCopy;
}

export function MessageBubble({ message, locale, copy }: MessageBubbleProps) {
  const isAssistant = message.role === 'ASSISTANT';
  const messageTime = formatMessageTime(message.createdAt);

  return (
    <article className={`message-row ${isAssistant ? 'message-row-assistant' : 'message-row-user'}`}>
      <div className="message-avatar" role="img" aria-label={isAssistant ? copy.assistantLabel : copy.userLabel}>
        {isAssistant ? <RobotOutlined /> : <UserOutlined />}
      </div>
      <div className={`message-bubble ${isAssistant ? 'message-bubble-assistant' : 'message-bubble-user'}`}>
        <div className="message-meta">
          {messageTime && <time dateTime={message.createdAt}>{messageTime}</time>}
          {message.status === 'streaming' && <span className="message-state">{copy.streaming}</span>}
          {message.status === 'error' && <span className="message-state message-state-error">{copy.error}</span>}
        </div>
        <div className="message-content">
          {isAssistant ? (
            message.content ? (
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  pre: ({ children }) => <pre className="code-block">{children}</pre>,
                  code: ({ className, children, ...props }) => (
                    <code className={className ? `code ${className}` : 'code'} {...props}>
                      {children}
                    </code>
                  ),
                }}
              >
                {message.content}
              </ReactMarkdown>
            ) : (
              <span className="typing-caret" aria-label={copy.streaming} />
            )
          ) : (
            <p>{message.content}</p>
          )}
        </div>
        {message.error && <div className="message-error">{message.error}</div>}
      </div>
    </article>
  );
}

function padDatePart(value: number) {
  return String(value).padStart(2, '0');
}

function formatMessageTime(createdAt: string | undefined) {
  if (!createdAt) {
    return '';
  }

  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return '';
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