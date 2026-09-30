import { Result } from 'antd';

/**
 * No modules screen (W-45 §5).
 * Rendered when the navigation feed returns zero items and loading is complete.
 */
export function NoModules() {
  return (
    <Result
      status="info"
      title="No Modules Available"
      subTitle="No active modules or permissions are assigned to your account."
    />
  );
}
