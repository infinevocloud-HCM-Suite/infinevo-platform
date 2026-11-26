import { useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import { theme as antdTheme } from 'antd';
import { Link } from 'react-router-dom';
import { ActionCard } from './ActionCard.jsx';
import { splitAnswer } from './answerLinks.js';

/** The row shown while an answer is on its way. */
export const THINKING_TEXT = 'Thinking…';

const EMPTY = new Set();

/**
 * One message as a row (W-114.5). The content goes on the page as a React text child, so the browser
 * reads it as text and never as markup: an answer comes from an AI model and must not be able to inject
 * HTML. No `dangerouslySetInnerHTML`, no markdown library, no general linkifying. Line breaks are kept by the
 * `white-space: pre-wrap` in `assistant.css`.
 *
 * The places a message turns into elements: a helper message carrying `proposedAction` gets a confirm
 * card under its text (W-114.9). The card is given the `intentId` only; it reads its words and state from
 * the server, never from this message (W-114.8 C1). And from W-114.13 a helper answer's
 * `[label](app:/path)` becomes a router `<Link>` when `allowedPaths` holds that exact path
 * (`answerLinks.js`); every other character stays text. A user's own message is never linked.
 */
export function renderMessage(message, token, allowedPaths = EMPTY, onNavigate = undefined) {
  const mine = message.role === 'USER';
  const content = String(message.content ?? '');
  const parts = mine || allowedPaths.size === 0 ? [{ text: content }] : splitAnswer(content, allowedPaths);
  const intentId = !mine && message.proposedAction?.intentId ? String(message.proposedAction.intentId) : null;
  return (
    <div
      key={message.id}
      className={`assistant-row ${mine ? 'assistant-row-user' : 'assistant-row-helper'}${
        intentId ? ' assistant-row-action' : ''
      }`}
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
        {parts.map((part, i) =>
          part.path ? (
            <Link key={i} to={part.path} onClick={onNavigate} data-testid="assistant-answer-link">
              {part.label}
            </Link>
          ) : (
            part.text
          ),
        )}
      </div>
      {intentId && <ActionCard intentId={intentId} />}
    </div>
  );
}

/**
 * The chat's messages, oldest first, then a "Thinking…" row while an answer is pending. A `log` live
 * region, so a screen reader reads a new answer; it scrolls to the newest row.
 */
export function MessageList({ messages, pending, allowedPaths = EMPTY, onNavigate }) {
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
      {messages.map((message) => renderMessage(message, token, allowedPaths, onNavigate))}
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
  /** The paths an answer may link to: the caller's menu paths and their link targets (W-114.13). */
  allowedPaths: PropTypes.instanceOf(Set),
  /** Called when a link in an answer is clicked; the drawer closes itself in phone mode. */
  onNavigate: PropTypes.func,
};
