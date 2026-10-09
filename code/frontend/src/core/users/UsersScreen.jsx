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
      <Title level={3} style={{ marginTop: 0 }}>
        Users &amp; access
      </Title>
      <Card>
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
