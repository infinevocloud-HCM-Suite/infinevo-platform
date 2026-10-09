import { Card, Typography, Breadcrumb } from 'antd';
import { useCan, NotEntitled } from '@shell/screens';
import { AuditTab } from '../admin/AuditTab.jsx';

const { Title } = Typography;

/**
 * Audit log (D-73): the signed-in tenant's trail. `GET /api/v1/audit` reads the caller's own tenant,
 * so this is the platform-staff Audit tab (W-65.3) always enabled, behind the menu's `core.audit.read`.
 */
export function AuditLogScreen() {
  const canRead = useCan('core.audit.read');

  if (!canRead) {
    return <NotEntitled />;
  }

  return (
    <div style={{ padding: 24 }}>
      <Breadcrumb items={[{ title: 'Home' }, { title: 'Audit log' }]} style={{ marginBottom: 16 }} />
      <Title level={3} style={{ marginTop: 0 }}>
        Audit log
      </Title>
      <Card>
        <AuditTab enabled />
      </Card>
    </div>
  );
}
