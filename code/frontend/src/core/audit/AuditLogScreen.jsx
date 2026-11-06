import { Card, Breadcrumb } from 'antd';
import { useCan, NotEntitled } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { AuditTab } from '../admin/AuditTab.jsx';

/**
 * Audit log (D-73, W-83.2): the signed-in tenant's trail.
 * `GET /api/v1/audit` reads the caller's own tenant,
 * so this is the platform-staff Audit tab (W-65.3) always enabled, behind the menu's `core.audit.read`.
 */
export function AuditLogScreen() {
  const canRead = useCan('core.audit.read');

  if (!canRead) {
    return <NotEntitled />;
  }

  return (
    <div className="saas-page-container fadeSlideUp">
      <Breadcrumb items={[{ title: 'Home' }, { title: 'Audit log' }]} style={{ marginBottom: 16 }} />
      <PageHeader
        title="Audit log"
        subtitle="Complete forensic trail of user actions, entity updates, and system operations."
      />
      <Card className="saas-card" variant="borderless">
        <AuditTab enabled />
      </Card>
    </div>
  );
}
