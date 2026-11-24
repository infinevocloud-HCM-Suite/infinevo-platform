import { Typography } from 'antd';

/** The text of a chat with no messages yet. */
export const EMPTY_STATE_TEXT = 'Ask a question about your leave, attendance or payslip.';

/**
 * A chat with no messages yet (W-114.5). Its own component so W-114.22 can add starter questions
 * here without touching the drawer.
 */
export function AssistantEmptyState() {
  return (
    <div className="assistant-empty" data-testid="assistant-empty">
      <Typography.Text type="secondary">{EMPTY_STATE_TEXT}</Typography.Text>
    </div>
  );
}
