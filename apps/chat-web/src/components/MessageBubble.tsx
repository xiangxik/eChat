import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import type { ChatMessageView } from '../services/chat';

interface MessageBubbleProps {
  message: ChatMessageView;
  chatbotName: string;
}

export function MessageBubble({ message, chatbotName }: MessageBubbleProps) {
  const isAssistant = message.role === 'ASSISTANT';
  const label = isAssistant ? chatbotName : 'You';

  return (
    <article className={`message-bubble ${isAssistant ? 'message-bubble-assistant' : 'message-bubble-user'}`}>
      <div className="message-meta">
        <span>{label}</span>
        {message.status === 'streaming' && <span className="message-state">Streaming</span>}
        {message.status === 'error' && <span className="message-state message-state-error">Error</span>}
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
            <span className="typing-caret" aria-label="Assistant is responding" />
          )
        ) : (
          <p>{message.content}</p>
        )}
      </div>
      {message.error && <div className="message-error">{message.error}</div>}
    </article>
  );
}