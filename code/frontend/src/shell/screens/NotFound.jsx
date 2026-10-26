import PropTypes from 'prop-types';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

/**
 * Not found screen (W-45 §5, D-108).
 * Rendered when the current route path does not match any registered route.
 */
export function NotFound({ homePath = '/' }) {
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title="404"
      subTitle="Sorry, the page you visited does not exist."
      extra={
        <Button type="primary" id="btn-not-found-home" onClick={() => navigate(homePath)}>
          Back to Home
        </Button>
      }
    />
  );
}

NotFound.propTypes = {
  homePath: PropTypes.string,
};
