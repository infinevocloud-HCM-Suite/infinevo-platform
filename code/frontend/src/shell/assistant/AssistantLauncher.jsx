import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { FloatButton } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { assistantService } from './assistantService.js';
import { AssistantDrawer } from './AssistantDrawer.jsx';

/**
 * The AI helper's floating button and its drawer (W-114.5). It asks `GET /api/v1/ai/status` once, when
 * the shell mounts - not on every route change - and renders nothing when the helper is off or the call
 * fails. A switch turned off mid-session takes the button away at the next load; until then the drawer
 * shows the refusal.
 */
export function AssistantLauncher({ phone = false }) {
  const [enabled, setEnabled] = useState(false);
  const [status, setStatus] = useState(null);
  const [open, setOpen] = useState(false);
  const buttonRef = useRef(null);

  useEffect(() => {
    let live = true;
    assistantService
      .getStatus()
      .then((s) => {
        if (!live) return;
        setStatus(s || null);
        setEnabled(s?.enabled === true);
      })
      // No button is the whole answer to a failed status call.
      .catch(() => {});
    return () => {
      live = false;
    };
  }, []);

  // The usage line after each answer; a failed refresh keeps the last line.
  const refreshStatus = useCallback(() => {
    assistantService
      .getStatus()
      .then((s) => {
        if (s) setStatus(s);
      })
      .catch(() => {});
  }, []);

  const focusButton = useCallback(() => {
    const el = buttonRef.current || document.getElementById('btn-ai-helper');
    el?.focus?.();
  }, []);

  if (!enabled) return null;

  return (
    <>
      <FloatButton
        ref={buttonRef}
        id="btn-ai-helper"
        type="primary"
        icon={<RobotOutlined />}
        aria-label="Ask the AI helper"
        tooltip="Ask the AI helper"
        onClick={() => setOpen(true)}
      />
      <AssistantDrawer
        open={open}
        onClose={() => setOpen(false)}
        phone={phone}
        status={status}
        onAnswered={refreshStatus}
        onClosed={focusButton}
      />
    </>
  );
}

AssistantLauncher.propTypes = {
  phone: PropTypes.bool,
};
