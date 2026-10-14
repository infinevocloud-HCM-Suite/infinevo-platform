import PropTypes from 'prop-types';
import { Alert, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

/**
 * A screen's read failed for a reason other than "not there" (D-84, D-85): say so with the
 * server's message and offer Retry. The screen shows this instead of its form or rows, so nothing
 * is edited or saved on data the server never sent.
 */
export function LoadError({ title, error, onRetry }) {
  return (
    <Alert
      type="error"
      showIcon
      message={title}
      description={error?.message || (typeof error === 'string' ? error : null) || 'Something went wrong'}
      action={
        onRetry ? (
          <Button type="primary" icon={<ReloadOutlined />} onClick={onRetry}>
            Retry
          </Button>
        ) : null
      }
    />
  );
}

LoadError.propTypes = {
  title: PropTypes.string.isRequired,
  error: PropTypes.oneOfType([PropTypes.object, PropTypes.string]),
  onRetry: PropTypes.func,
};
