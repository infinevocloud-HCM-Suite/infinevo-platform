import { useSearchParams } from 'react-router-dom';
import { Card, Tabs, Typography, Breadcrumb } from 'antd';
import { useCan, NotEntitled } from '@shell/screens';
import { UsersTab } from './UsersTab.jsx';
import { InvitationsTab } from './InvitationsTab.jsx';

const { Title } = Typography;

const TABS = ['users', 'invitations'];

/**
 * Users & access (W-73.4): who has access and as what, on one screen. Two tabs — Users (accounts, roles,
 * Disable) and Invitations (both kinds, Invite user, Resend, Revoke). `?tab=invitations` opens the second,
 * which is where the old `/invitations/users` and `/invitations/employees` routes now land.
 */
export function UsersScreen() {
  const canManage = useCan('core.user.manage');
  const [params, setParams] = useSearchParams();
  const tab = TABS.includes(params.get('tab')) ? params.get('tab') : 'users';

  if (!canManage) {
    return <NotEntitled action="core.user.manage" />;
  }

  return (
    <div style={{ padding: 24 }}>
      <Breadcrumb items={[{ title: 'Home' }, { title: 'Users & access' }]} style={{ marginBottom: 16 }} />
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <Title level={3} style={{ margin: 0, color: '#0F172A', fontWeight: 600 }}>
            Users &amp; access
          </Title>
          <Typography.Text type="secondary" style={{ fontSize: 13, display: 'block', marginTop: 2 }}>
            Manage active user accounts, security roles, and onboarding invitations
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
          ● Identity &amp; Access
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
        <Tabs
          activeKey={tab}
          onChange={(key) => setParams(key === 'users' ? {} : { tab: key }, { replace: true })}
          destroyInactiveTabPane
          items={[
            { key: 'users', label: 'Users', children: <UsersTab /> },
            { key: 'invitations', label: 'Invitations', children: <InvitationsTab /> },
          ]}
        />
      </Card>
    </div>
  );
}
