import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { Link, useInRouterContext } from 'react-router-dom';
import { Button, Card, Space, Spin, Typography, theme as antdTheme } from 'antd';
import {
  assistantService,
  AI_ACTION_CANCELLED,
  AI_ACTION_EXPIRED,
  AI_ACTION_FAILED,
  AI_DISABLED,
} from './assistantService.js';
import { linkFor, linkLabelFor, viewLinkFor } from './actionLinks.js';

/** Every sentence the card shows (W-114.9, States). */
export const ACTION_TEXT = {
  loading: 'Loading the action…',
  loadFailed: 'Could not load this action.',
  retry: 'Retry',
  confirm: 'Confirm',
  cancel: 'Cancel',
  done: 'Done.',
  openFirst: 'Open the request',
  cancelled: 'Cancelled.',
  expired: 'This expired. Ask again to try once more.',
  failed: 'This could not be done:',
  forbidden: 'You no longer have permission to do this.',
  disabled: 'The AI helper is switched off for your company.',
  network: 'Could not reach the server. Try again.',
  gone: 'This action no longer exists.',
};

/** How long to wait before asking again when the countdown is at 0 and the server still says PROPOSED. */
export const RECHECK_MS = 30000;

/** `m:ss` for a number of whole seconds. */
export function formatRemaining(seconds) {
  const s = Math.max(0, Math.floor(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function secondsLeft(expiresAt, now = Date.now()) {
  const at = Date.parse(expiresAt);
  return Number.isNaN(at) ? null : Math.max(0, Math.ceil((at - now) / 1000));
}

const text = (value) => String(value ?? '');

/** The "done" link: a router link inside the app, a plain link where no router is mounted. */
function DoneLink({ to, children }) {
  const inRouter = useInRouterContext();
  return inRouter ? <Link to={to}>{children}</Link> : <a href={to}>{children}</a>;
}

DoneLink.propTypes = { to: PropTypes.string.isRequired, children: PropTypes.node };

/**
 * One proposed action (W-114.9). The card is given the `intentId` and nothing else: its title, fields,
 * status and expiry come only from `GET /api/v1/ai/actions/{intentId}` (W-114.8 C1), never from the
 * helper's message text. It is the same card for every action; the only per-action lines are in the
 * link map, `actionLinks.js`.
 *
 * Every value is a React text child, shown whole: no markup, no cut text (W-114.8 C2). A change of state
 * is read out by the message list's live region, which the card sits inside.
 */
export function ActionCard({ intentId }) {
  const { token } = antdTheme.useToken();
  const [action, setAction] = useState(null);
  const [loadState, setLoadState] = useState('loading'); // loading | ready | failed
  const [busy, setBusy] = useState(null); // null | 'confirm' | 'cancel'
  const [local, setLocal] = useState(null); // FORBIDDEN | DISABLED | GONE - an end state the server sent as an error
  const [notice, setNotice] = useState(null); // a sentence shown while the buttons stay
  const [remaining, setRemaining] = useState(null);
  const busyRef = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  /** Ask the server for the true state. Resolves to the action, or null when the call failed. */
  const refresh = useCallback(async () => {
    try {
      const body = await assistantService.getAction(intentId);
      if (!mounted.current) return null;
      setAction(body);
      setLoadState('ready');
      return body;
    } catch {
      if (mounted.current) setLoadState((prev) => (prev === 'ready' ? prev : 'failed'));
      return null;
    }
  }, [intentId]);

  useEffect(() => {
    setLoadState('loading');
    setAction(null);
    refresh();
  }, [refresh]);

  const status = local ?? action?.status;
  const proposed = status === 'PROPOSED';
  const expiresAt = action?.expiresAt;

  // The countdown. The browser clock only draws it; at 0 the server decides (rule 7).
  useEffect(() => {
    if (!proposed || loadState !== 'ready') {
      setRemaining(null);
      return undefined;
    }
    let recheck = null;
    let asked = false;
    const tick = () => {
      const left = secondsLeft(expiresAt);
      setRemaining(left);
      if (left === 0 && !asked && !busyRef.current) {
        asked = true;
        refresh().then((body) => {
          // The server still says PROPOSED (the user's clock runs fast), or the call failed: ask again a
          // little later, so the card never rests at 0:00 with a live Confirm.
          if (mounted.current && (!body || body.status === 'PROPOSED')) {
            recheck = setTimeout(() => {
              asked = false;
            }, RECHECK_MS);
          }
        });
      }
    };
    tick();
    const timer = setInterval(tick, 1000);
    return () => {
      clearInterval(timer);
      if (recheck) clearTimeout(recheck);
    };
  }, [proposed, expiresAt, loadState, refresh]);

  /** Shows what an error from confirm or cancel means. */
  const handleError = async (err) => {
    const code = err?.code;
    if (err?.status === 410 || code === AI_ACTION_EXPIRED) {
      setAction((prev) => ({ ...prev, status: 'EXPIRED' }));
      await refresh();
      return;
    }
    if (code === AI_ACTION_FAILED) {
      // The reason is the server's `result.reason`, read back from the intent.
      const body = await refresh();
      if (!mounted.current) return;
      if (!body || body.status !== 'FAILED') {
        setAction((prev) => ({
          ...prev,
          status: 'FAILED',
          result: { ...(prev?.result ?? {}), reason: body?.result?.reason ?? err?.message },
        }));
      }
      return;
    }
    if (code === AI_ACTION_CANCELLED) {
      setAction((prev) => ({ ...prev, status: 'CANCELLED' }));
      return;
    }
    if (err?.status === 404) {
      // Not found or not the caller's - the chat was deleted (W-114.8 check 1). Nothing left to do.
      setLocal('GONE');
      return;
    }
    if (err?.status === 409) {
      // Cancelling what is already confirmed, or the like: the server's record says what it is.
      await refresh();
      return;
    }
    if (code === AI_DISABLED) {
      setLocal('DISABLED');
      return;
    }
    if (err?.status === 403) {
      setLocal('FORBIDDEN');
      return;
    }
    // No answer from the server, or one this card does not know: the buttons come back. Confirm is safe
    // to press again, the server carries an intent out once (W-114.8 C4).
    setNotice(err?.status ? text(err.message) || ACTION_TEXT.network : ACTION_TEXT.network);
  };

  const run = async (kind) => {
    if (busyRef.current) return; // a second click while the first is on its way sends nothing (rule 5)
    busyRef.current = true;
    setBusy(kind);
    setNotice(null);
    try {
      const body =
        kind === 'confirm'
          ? await assistantService.confirmAction(intentId)
          : await assistantService.cancelAction(intentId);
      if (mounted.current && body && typeof body === 'object') {
        setAction((prev) => ({ ...prev, ...body }));
      }
    } catch (err) {
      if (mounted.current) await handleError(err);
    } finally {
      busyRef.current = false;
      if (mounted.current) setBusy(null);
    }
  };

  const retry = () => {
    setLoadState('loading');
    refresh();
  };

  if (loadState !== 'ready' || !action) {
    return (
      <div className="assistant-action" data-testid="assistant-action-card">
        {loadState === 'failed' ? (
          <Space size={8} wrap>
            <Typography.Text type="secondary">{ACTION_TEXT.loadFailed}</Typography.Text>
            <Button size="small" onClick={retry}>
              {ACTION_TEXT.retry}
            </Button>
          </Space>
        ) : (
          <Space size={8} data-testid="assistant-action-loading">
            <Spin size="small" />
            <Typography.Text type="secondary">{ACTION_TEXT.loading}</Typography.Text>
          </Space>
        )}
      </div>
    );
  }

  const summary = text(action.summary);
  const fields = Array.isArray(action.fields) ? action.fields : [];
  const link = status === 'CONFIRMED' ? linkFor(action.actionCode, action.result) : null;
  const viewLink = proposed ? viewLinkFor(action.link) : null;

  let footer = null;
  if (proposed) {
    footer = (
      <div className="assistant-action-footer">
        {notice && (
          <Typography.Text type="danger" data-testid="assistant-action-notice">
            {notice}
          </Typography.Text>
        )}
        <Space size={8} wrap>
          <Button
            id="btn-ai-confirm"
            type="primary"
            size="small"
            loading={busy === 'confirm'}
            disabled={busy !== null}
            onClick={() => run('confirm')}
          >
            {ACTION_TEXT.confirm}
          </Button>
          <Button size="small" disabled={busy !== null} onClick={() => run('cancel')}>
            {ACTION_TEXT.cancel}
          </Button>
          {viewLink && <DoneLink to={viewLink}>{ACTION_TEXT.openFirst}</DoneLink>}
          {remaining !== null && (
            // Off the live region: the message list's log would otherwise read the time out every second.
            // Only a change of state is announced.
            <Typography.Text type="secondary" aria-live="off" data-testid="assistant-action-countdown">
              {`Expires in ${formatRemaining(remaining)}`}
            </Typography.Text>
          )}
        </Space>
      </div>
    );
  } else if (status === 'CONFIRMED') {
    footer = (
      <Space size={8} wrap>
        <Typography.Text>{ACTION_TEXT.done}</Typography.Text>
        {link && <DoneLink to={link}>{linkLabelFor(action.actionCode)}</DoneLink>}
      </Space>
    );
  } else if (status === 'CANCELLED') {
    footer = <Typography.Text>{ACTION_TEXT.cancelled}</Typography.Text>;
  } else if (status === 'EXPIRED') {
    footer = <Typography.Text>{ACTION_TEXT.expired}</Typography.Text>;
  } else if (status === 'FAILED') {
    footer = (
      <div className="assistant-action-footer">
        <Typography.Text>{ACTION_TEXT.failed}</Typography.Text>
        <Typography.Text type="danger" className="assistant-action-text" data-testid="assistant-action-reason">
          {text(action.result?.reason)}
        </Typography.Text>
      </div>
    );
  } else if (status === 'DISABLED') {
    footer = <Typography.Text>{ACTION_TEXT.disabled}</Typography.Text>;
  } else if (status === 'FORBIDDEN') {
    footer = <Typography.Text>{ACTION_TEXT.forbidden}</Typography.Text>;
  } else if (status === 'GONE') {
    footer = <Typography.Text>{ACTION_TEXT.gone}</Typography.Text>;
  }

  return (
    <Card
      size="small"
      className="assistant-action assistant-action-card"
      data-testid="assistant-action-card"
      data-status={status}
      role="group"
      aria-label={summary}
      title={<span className="assistant-action-text">{summary}</span>}
      style={{ borderColor: token.colorBorderSecondary }}
    >
      {fields.length > 0 && (
        <dl className="assistant-action-fields">
          {fields.map((field, index) => (
            // The server's order; a label may repeat, so the position is part of the key.
            <div className="assistant-action-field" key={`${index}-${text(field?.label)}`}>
              <dt className="assistant-action-text" style={{ color: token.colorTextSecondary }}>
                {text(field?.label)}
              </dt>
              <dd className="assistant-action-text">{text(field?.value)}</dd>
            </div>
          ))}
        </dl>
      )}
      {footer}
    </Card>
  );
}

ActionCard.propTypes = {
  intentId: PropTypes.string.isRequired,
};
