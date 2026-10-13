import { Tag } from 'antd';
import PropTypes from 'prop-types';
import { EMPTY, statusTag } from './format.js';

/**
 * A server status code shown as a coloured tag in words (D-78): <StatusTag status="APPROVED" />
 * reads "Approved" in green. Unknown codes are humanised; a missing one shows a bare '—'.
 */
export function StatusTag({ status }) {
  const { label, color } = statusTag(status);
  if (label === EMPTY) return EMPTY;
  return <Tag color={color}>{label}</Tag>;
}

StatusTag.propTypes = {
  status: PropTypes.string,
};

/** A Table column render for a status code: `render: renderStatus`. */
export function renderStatus(status) {
  return <StatusTag status={status} />;
}
