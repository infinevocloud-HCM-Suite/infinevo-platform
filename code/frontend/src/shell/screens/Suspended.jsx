import { Result, Button } from 'antd';

/**
 * Tenant suspended screen (W-45 §5).
 * Displayed full-page when the server reports TENANT_SUSPENDED.
 */
export function Suspended() {
  return (
    <Result
      status="warning"
      title="Subscription Suspended"
      subTitle="Your organisation's subscription is suspended. Please contact your account administrator."
      extra={
        <Button type="primary" onClick={() => window.location.reload()}>
          Reload
        </Button>
      }
    />
  );
}
