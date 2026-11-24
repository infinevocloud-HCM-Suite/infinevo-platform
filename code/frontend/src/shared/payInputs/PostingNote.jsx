import PropTypes from 'prop-types';
import { Typography } from 'antd';
import { postingNote } from './postingPeriod.js';

const TYPE = { info: 'secondary', warning: 'warning', error: 'danger' };

/**
 * The one line under a month picker saying which run picks the entry up (D-144): grey for an open
 * month, amber for a locked one, red for a closed one. Renders nothing until the answer arrives.
 */
export function PostingNote({ posting, testId, style }) {
  const note = postingNote(posting);
  if (!note) return null;
  return (
    <Typography.Text
      type={TYPE[note.tone]}
      role={note.tone === 'error' ? 'alert' : 'status'}
      data-testid={testId}
      data-state={posting.state}
      style={{ display: 'block', fontSize: 12, ...style }}
    >
      {note.text}
    </Typography.Text>
  );
}

PostingNote.propTypes = {
  posting: PropTypes.shape({
    requested_period: PropTypes.string,
    posted_period: PropTypes.string,
    state: PropTypes.string,
  }),
  testId: PropTypes.string,
  style: PropTypes.object,
};
