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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <Title level={3} style={{ margin: 0, color: '#0F172A', fontWeight: 600 }}>
            Audit log
          </Title>
          <Typography.Text type="secondary" style={{ fontSize: 13, display: 'block', marginTop: 2 }}>
            Immutable chronological record of administrative actions, status mutations, and security events
          </Typography.Text>
        </div>
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            background: '#E0F2FE',
            color: '#0369A1',
            border: '1px solid #BAE6FD',
            borderRadius: 9999,
            padding: '4px 12px',
            fontSize: 12,
            fontWeight: 600,
          }}
        >
          ● Security Trail
        </span>
      </div>
      <Card
        variant="borderless"
        style={{
          borderRadius: 16,
          border: '1px solid #E2E8F0',
          boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px -1px rgba(0, 0, 0, 0.05)',
          background: '#FFFFFF',
        }}
      >
        <AuditTab enabled />
      </Card>
    </div>
  );
}
