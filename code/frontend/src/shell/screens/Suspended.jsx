import { Result, Button, Space } from 'antd';
import { logout } from '../auth/keycloak.js';

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
        <Space>
          <Button type="primary" onClick={() => window.location.reload()}>
            Reload
          </Button>
          <Button id="btn-suspended-logout" onClick={() => logout()}>
            Log Out
          </Button>
        </Space>
      }
    />
  );
}
