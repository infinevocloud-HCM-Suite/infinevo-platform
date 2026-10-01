import { Result } from 'antd';

/**
 * Module not entitled screen (W-45 §5).
 * Exported so screens can render it on err.isModuleNotEntitled (403 module denial).
 */
export function NotEntitled() {
  return (
    <Result
      status="403"
      title="Module Not Subscribed"
      subTitle="Your organisation has not subscribed to this module. Please contact your administrator to upgrade."
    />
  );
}
