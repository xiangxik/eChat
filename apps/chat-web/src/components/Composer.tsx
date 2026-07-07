import { ReloadOutlined, SendOutlined, StopOutlined } from '@ant-design/icons';
import type { FormEvent, KeyboardEvent } from 'react';

import type { ComposerCopy } from '../i18n';

interface ComposerProps {
  value: string;
  disabled: boolean;
  isStreaming: boolean;
  error: string | null;
  canRetry: boolean;
  copy: ComposerCopy;
  onChange: (value: string) => void;
  onSubmit: () => void;
  onStop: () => void;
  onRetry: () => void;
}

export function Composer({
  value,
  disabled,
  isStreaming,
  error,
  canRetry,
  copy,
  onChange,
  onSubmit,
  onStop,
  onRetry,
}: ComposerProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      onSubmit();
    }
  }

  return (
    <div className="composer-wrap">
      {error && (
        <div className="error-banner" role="alert" aria-label={copy.errorLabel}>
          <span>{error}</span>
          {canRetry && (
            <button type="button" className="ghost-button" onClick={onRetry} disabled={disabled} aria-label={copy.retry} title={copy.retry}>
              <ReloadOutlined />
            </button>
          )}
        </div>
      )}
      <form className="composer" onSubmit={handleSubmit}>
        <textarea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={handleKeyDown}
          aria-label={copy.inputLabel}
          placeholder={copy.placeholder}
          rows={1}
          disabled={disabled && !isStreaming}
        />
        <div className="composer-actions">
          {isStreaming ? (
            <button type="button" className="primary-button composer-stop-button" onClick={onStop} aria-label={copy.stop} title={copy.stop}>
              <StopOutlined />
            </button>
          ) : (
            <button
              type="submit"
              className="primary-button"
              disabled={disabled || value.trim().length === 0}
              aria-label={disabled ? copy.sending : copy.send}
              title={disabled ? copy.sending : copy.send}
            >
              <SendOutlined />
            </button>
          )}
        </div>
      </form>
    </div>
  );
}