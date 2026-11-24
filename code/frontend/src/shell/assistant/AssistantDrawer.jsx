import { useCallback, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { Alert, Button, Drawer, Space, Spin, Typography } from 'antd';
import { HistoryOutlined, PlusOutlined } from '@ant-design/icons';
import { assistantService, AI_RATE_LIMITED } from './assistantService.js';
import { MessageList } from './MessageList.jsx';
import { AssistantInput } from './AssistantInput.jsx';
import { SessionList } from './SessionList.jsx';
import { AssistantEmptyState } from './AssistantEmptyState.jsx';
import './assistant.css';

/** The drawer's width on a desktop, in px; a phone gets the full width. */
export const DRAWER_WIDTH = 420;

export const TEXT = {
  disabled: 'The AI helper is switched off for your company.',
  capReached: 'Your company has used its AI helper limit for this month.',
  failed: 'The helper could not answer. Try again.',
  rateLimited: (seconds) => `You are sending messages too fast. Try again in ${seconds} ${seconds === 1 ? 'second' : 'seconds'}.`,
  rateLimitedLater: 'You are sending messages too fast. Try again in a moment.',
  chatNotLoaded: 'This chat could not be loaded.',
  notDeleted: 'The chat could not be deleted. Try again.',
};

/** A wait longer than this reads better as the server's own words (its daily limit says "tomorrow"). */
const MAX_SECONDS_SHOWN = 120;

/** "N of M this month", or "N this month" while no cap is set. */
export function usageLine(status) {
  if (!status) return null;
  const used = Number(status.usedThisMonth) || 0;
  const cap = status.monthlyRequestCap;
  return cap === null || cap === undefined ? `${used} this month` : `${used} of ${cap} this month`;
}

function rateLimitedText(err) {
  const seconds = err?.retryAfterSeconds;
  if (seconds && seconds <= MAX_SECONDS_SHOWN) return TEXT.rateLimited(seconds);
  return err?.message || TEXT.rateLimitedLater;
}

let localId = 0;
function localMessage(content) {
  localId += 1;
  return { id: `local-${localId}`, role: 'USER', content };
}

/**
 * The AI helper's slide-over (W-114.5): one chat at a time, past chats, and the month's usage. The state
 * is local - nothing else reads the chat - and lives as long as the shell, so closing and reopening the
 * drawer shows the same chat.
 */
export function AssistantDrawer({ open, onClose, phone, status, onAnswered, onClosed }) {
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [pending, setPending] = useState(false);
  // { text, question, retry } - the last send failed; `retry` offers to resend `question`.
  const [failure, setFailure] = useState(null);
  // 'disabled' or 'cap': the helper refuses every question until the next load.
  const [blocked, setBlocked] = useState(null);
  const [view, setView] = useState('chat');
  const [sessions, setSessions] = useState({ list: [], loading: false, error: false });
  const [chatLoading, setChatLoading] = useState(false);
  const [chatError, setChatError] = useState(false);
  const [deleteError, setDeleteError] = useState(false);

  const inputRef = useRef(null);
  const sessionRef = useRef(null);
  const pendingRef = useRef(false);

  const selectSession = (id) => {
    sessionRef.current = id;
    setSessionId(id);
  };

  const ask = useCallback(
    async (question) => {
      if (pendingRef.current) return;
      pendingRef.current = true;
      setPending(true);
      setFailure(null);
      try {
        let id = sessionRef.current;
        if (!id) {
          const created = await assistantService.createSession(question);
          id = created?.id;
          sessionRef.current = id;
          setSessionId(id);
        }
        const answer = await assistantService.sendMessage(id, question);
        if (answer?.message) {
          setMessages((list) => [...list, { ...answer.message, proposedAction: answer.proposedAction ?? null }]);
        }
        onAnswered?.();
      } catch (err) {
        if (err?.code === 'AI_DISABLED') {
          setBlocked('disabled');
        } else if (err?.code === 'AI_CAP_REACHED') {
          setBlocked('cap');
        } else {
          // A chat deleted elsewhere: the retry starts a new one.
          if (err?.status === 404) sessionRef.current = null;
          const text = err?.code === AI_RATE_LIMITED ? rateLimitedText(err) : TEXT.failed;
          setFailure({ text, question });
        }
      } finally {
        pendingRef.current = false;
        setPending(false);
      }
    },
    [onAnswered],
  );

  const onSend = (question) => {
    setMessages((list) => [...list, localMessage(question)]);
    ask(question);
  };

  const newChat = () => {
    selectSession(null);
    setMessages([]);
    setFailure(null);
    setChatError(false);
    setView('chat');
    inputRef.current?.focus?.();
  };

  const showSessions = async () => {
    if (view === 'sessions') {
      setView('chat');
      return;
    }
    setView('sessions');
    setDeleteError(false);
    setSessions((s) => ({ ...s, loading: true, error: false }));
    try {
      const list = await assistantService.listSessions();
      setSessions({ list, loading: false, error: false });
    } catch {
      setSessions({ list: [], loading: false, error: true });
    }
  };

  const openSession = async (session) => {
    setView('chat');
    setFailure(null);
    setChatError(false);
    setChatLoading(true);
    selectSession(session.id);
    setMessages([]);
    try {
      const list = await assistantService.listMessages(session.id);
      setMessages(list.filter((m) => m.role === 'USER' || m.role === 'ASSISTANT'));
    } catch {
      setChatError(true);
    } finally {
      setChatLoading(false);
    }
  };

  const deleteSession = async (session) => {
    setDeleteError(false);
    try {
      await assistantService.deleteSession(session.id);
      setSessions((s) => ({ ...s, list: s.list.filter((x) => x.id !== session.id) }));
      if (session.id === sessionRef.current) {
        selectSession(null);
        setMessages([]);
        setFailure(null);
      }
    } catch {
      setDeleteError(true);
    }
  };

  const afterOpenChange = (isOpen) => {
    if (isOpen) inputRef.current?.focus?.();
    else onClosed?.();
  };

  const blockedText = blocked === 'disabled' ? TEXT.disabled : blocked === 'cap' ? TEXT.capReached : null;
  const usage = usageLine(status);

  const footer = (
    <div className="assistant-footer">
      {view === 'chat' && (
        <AssistantInput ref={inputRef} disabled={pending || !!blocked || chatLoading} onSend={onSend} />
      )}
      {usage && (
        <Typography.Text type="secondary" className="assistant-usage" data-testid="assistant-usage">
          {usage}
        </Typography.Text>
      )}
    </div>
  );

  return (
    <Drawer
      title="AI helper"
      placement="right"
      open={open}
      onClose={onClose}
      width={phone ? '100%' : DRAWER_WIDTH}
      rootClassName="assistant-drawer"
      keyboard
      afterOpenChange={afterOpenChange}
      extra={
        <Space size="small">
          <Button id="btn-ai-new-chat" icon={<PlusOutlined />} onClick={newChat} disabled={pending}>
            New chat
          </Button>
          <Button
            id="btn-ai-past-chats"
            icon={<HistoryOutlined />}
            onClick={showSessions}
            disabled={pending}
            aria-pressed={view === 'sessions'}
          >
            Past chats
          </Button>
        </Space>
      }
      footer={footer}
      styles={{ body: { display: 'flex', flexDirection: 'column' } }}
    >
      {view === 'sessions' ? (
        <>
          {deleteError && <Alert type="error" showIcon message={TEXT.notDeleted} className="assistant-alert" />}
          <SessionList
            sessions={sessions.list}
            loading={sessions.loading}
            error={sessions.error}
            currentId={sessionId}
            onOpen={openSession}
            onDelete={deleteSession}
          />
        </>
      ) : (
        <>
          {chatLoading ? (
            <Spin className="assistant-loading" />
          ) : chatError ? (
            <Alert type="error" showIcon message={TEXT.chatNotLoaded} className="assistant-alert" />
          ) : messages.length === 0 && !pending ? (
            <AssistantEmptyState />
          ) : (
            <MessageList messages={messages} pending={pending} />
          )}
          {failure && (
            <Alert
              type="error"
              showIcon
              className="assistant-alert"
              message={failure.text}
              data-testid="assistant-failure"
              action={
                <Button size="small" id="btn-ai-retry" onClick={() => ask(failure.question)}>
                  Retry
                </Button>
              }
            />
          )}
          {blockedText && (
            <Alert type="warning" showIcon className="assistant-alert" message={blockedText} data-testid="assistant-blocked" />
          )}
        </>
      )}
    </Drawer>
  );
}

AssistantDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  phone: PropTypes.bool,
  status: PropTypes.shape({
    enabled: PropTypes.bool,
    monthlyRequestCap: PropTypes.number,
    usedThisMonth: PropTypes.number,
    month: PropTypes.string,
  }),
  onAnswered: PropTypes.func,
  onClosed: PropTypes.func,
};
