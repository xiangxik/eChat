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
  const messageTime = formatMessageTime(message.createdAt, locale);

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

function formatMessageTime(createdAt: string | undefined, locale: Locale) {
  if (!createdAt) {
    return '';
  }

  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return '';
  }

  return new Intl.DateTimeFormat(locale === 'zh' ? 'zh-CN' : 'en', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}