import { useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import { theme as antdTheme } from 'antd';

/** The row shown while an answer is on its way. */
export const THINKING_TEXT = 'Thinking…';

/**
 * One message as a row (W-114.5). The content goes on the page as a React text child, so the browser
 * reads it as text and never as markup: an answer comes from an AI model and must not be able to inject
 * HTML. No `dangerouslySetInnerHTML`, no markdown library, no linkifying. Line breaks are kept by the
 * `white-space: pre-wrap` in `assistant.css`.
 *
 * The one place a message turns into elements: W-114.9 renders a message carrying `proposedAction` as a
 * confirm card here. Until then such a message is shown as plain text like any other.
 */
export function renderMessage(message, token) {
  const mine = message.role === 'USER';
  return (
    <div
      key={message.id}
      className={`assistant-row ${mine ? 'assistant-row-user' : 'assistant-row-helper'}`}
      data-testid={mine ? 'assistant-user-message' : 'assistant-helper-message'}
    >
      <span className="assistant-sr-only">{mine ? 'You: ' : 'AI helper: '}</span>
      <div
        className="assistant-bubble"
        style={{
          background: mine ? token.colorPrimaryBg : token.colorFillTertiary,
          color: token.colorText,
          borderRadius: token.borderRadiusLG,
          padding: `${token.paddingXS}px ${token.paddingSM}px`,
        }}
      >
        {String(message.content ?? '')}
      </div>
    </div>
  );
}

/**
 * The chat's messages, oldest first, then a "Thinking…" row while an answer is pending. A `log` live
 * region, so a screen reader reads a new answer; it scrolls to the newest row.
 */
export function MessageList({ messages, pending }) {
  const { token } = antdTheme.useToken();
  const listRef = useRef(null);

  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages.length, pending]);

  return (
    <div
      ref={listRef}
      className="assistant-messages"
      role="log"
      aria-live="polite"
      aria-label="Chat messages"
      data-testid="assistant-messages"
    >
      {messages.map((message) => renderMessage(message, token))}
      {pending && (
        <div className="assistant-row assistant-row-helper" data-testid="assistant-thinking">
          <div
            className="assistant-bubble"
            style={{
              color: token.colorTextSecondary,
              padding: `${token.paddingXS}px ${token.paddingSM}px`,
            }}
          >
            {THINKING_TEXT}
          </div>
        </div>
      )}
    </div>
  );
}

MessageList.propTypes = {
  messages: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      role: PropTypes.string.isRequired,
      content: PropTypes.string,
      proposedAction: PropTypes.object,
    }),
  ).isRequired,
  pending: PropTypes.bool,
};
