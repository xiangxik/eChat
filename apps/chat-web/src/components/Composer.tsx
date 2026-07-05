import type { FormEvent, KeyboardEvent } from 'react';

interface ComposerProps {
  value: string;
  disabled: boolean;
  isStreaming: boolean;
  error: string | null;
  canRetry: boolean;
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
        <div className="error-banner" role="alert">
          <span>{error}</span>
          {canRetry && (
            <button type="button" className="ghost-button" onClick={onRetry} disabled={disabled}>
              Retry
            </button>
          )}
        </div>
      )}
      <form className="composer" onSubmit={handleSubmit}>
        <textarea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Message the chatbot"
          rows={3}
          disabled={disabled && !isStreaming}
        />
        <div className="composer-actions">
          <button type="button" className="secondary-button" onClick={onStop} disabled={!isStreaming}>
            Stop
          </button>
          <button type="submit" className="primary-button" disabled={disabled || value.trim().length === 0}>
            {disabled ? 'Sending' : 'Send'}
          </button>
        </div>
      </form>
    </div>
  );
}