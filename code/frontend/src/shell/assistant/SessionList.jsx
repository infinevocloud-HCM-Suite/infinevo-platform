import PropTypes from 'prop-types';
import { Alert, Button, Popconfirm, Skeleton, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { formatDateTime } from '../../shared/ui/format.js';

/** What a chat with no title is called in the list. */
export const UNTITLED = 'Untitled chat';

/**
 * Past chats (W-114.5): date and first question, newest first, as the server orders them. Delete asks
 * first and then calls `onDelete`.
 */
export function SessionList({ sessions, loading, error, currentId, onOpen, onDelete }) {
  if (loading) return <Skeleton active paragraph={{ rows: 3 }} />;
  if (error) return <Alert type="error" showIcon message="Your earlier chats could not be loaded." />;
  if (!sessions || sessions.length === 0) {
    return (
      <div className="assistant-empty" data-testid="assistant-no-sessions">
        <Typography.Text type="secondary">No earlier chats.</Typography.Text>
      </div>
    );
  }

  return (
    <ul className="assistant-sessions" aria-label="Past chats" data-testid="assistant-sessions">
      {sessions.map((session) => {
        const title = session.title || UNTITLED;
        return (
          <li key={session.id} className="assistant-session">
            <Button
              type={session.id === currentId ? 'link' : 'text'}
              className="assistant-session-open"
              onClick={() => onOpen(session)}
            >
              <span className="assistant-session-title">{title}</span>
              <Typography.Text type="secondary" className="assistant-session-date">
                {formatDateTime(session.lastMessageAt || session.createdAt)}
              </Typography.Text>
            </Button>
            <Popconfirm
              title="Delete this chat?"
              okText="Delete"
              okButtonProps={{ danger: true }}
              cancelText="Cancel"
              onConfirm={() => onDelete(session)}
            >
              <Button type="text" aria-label={`Delete chat ${title}`} icon={<DeleteOutlined />} />
            </Popconfirm>
          </li>
        );
      })}
    </ul>
  );
}

SessionList.propTypes = {
  sessions: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      title: PropTypes.string,
      createdAt: PropTypes.string,
      lastMessageAt: PropTypes.string,
    }),
  ),
  loading: PropTypes.bool,
  error: PropTypes.bool,
  currentId: PropTypes.string,
  onOpen: PropTypes.func.isRequired,
  onDelete: PropTypes.func.isRequired,
};
