import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Button, Typography } from 'antd';
import { assistantService } from './assistantService.js';

/** The text of a chat with no messages yet. */
export const EMPTY_STATE_TEXT = 'Ask a question about your leave, attendance or payslip.';

/** The small heading above the task chips. */
export const TASKS_HEADING = 'Do it for me';

/**
 * A chat with no messages yet (W-114.5). From W-114.22 it also shows up to six starter chips from
 * `GET /api/v1/ai/starters` - only what this user can really ask - questions first, then tasks under
 * "Do it for me". A click sends the starter's prompt as a normal message. While loading, on an empty list
 * or a failed call it shows the one line of text and no error.
 */
export function AssistantEmptyState({ onPick }) {
  const [starters, setStarters] = useState([]);

  useEffect(() => {
    let live = true;
    assistantService
      .listStarters()
      .then((list) => {
        if (live) setStarters(list);
      })
      .catch(() => {
        if (live) setStarters([]);
      });
    return () => {
      live = false;
    };
  }, []);

  const questions = starters.filter((s) => s.kind === 'QUESTION');
  const tasks = starters.filter((s) => s.kind === 'TASK');
  const chip = (s) => (
    <Button
      key={s.id}
      size="small"
      className="assistant-starter"
      aria-label={s.label}
      disabled={!onPick}
      onClick={() => onPick?.(s.prompt)}
    >
      {s.label}
    </Button>
  );

  return (
    <div className="assistant-empty" data-testid="assistant-empty">
      <Typography.Text type="secondary">{EMPTY_STATE_TEXT}</Typography.Text>
      {questions.length > 0 && (
        <div className="assistant-starters" data-testid="assistant-starters-questions">
          {questions.map(chip)}
        </div>
      )}
      {tasks.length > 0 && (
        <>
          <Typography.Text type="secondary" className="assistant-starters-heading">
            {TASKS_HEADING}
          </Typography.Text>
          <div className="assistant-starters" data-testid="assistant-starters-tasks">
            {tasks.map(chip)}
          </div>
        </>
      )}
    </div>
  );
}

AssistantEmptyState.propTypes = {
  /** Sends a prompt as the user's message; left out while the helper cannot take a question. */
  onPick: PropTypes.func,
};
