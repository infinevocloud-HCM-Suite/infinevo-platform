import { Result } from 'antd';

/**
 * Not found screen (W-45 §5).
 * Rendered when the current route path does not match any registered route.
 */
export function NotFound() {
  return (
    <Result
      status="404"
      title="404"
      subTitle="Sorry, the page you visited does not exist."
    />
  );
}
