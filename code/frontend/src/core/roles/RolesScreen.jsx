import { useEffect, useState } from 'react';
import { Card, Table, Tag, Typography, Breadcrumb, List, theme } from 'antd';
import { useCan, NotEntitled } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { rolesService } from './rolesService.js';

const { Text } = Typography;

/**
 * Roles (D-73, W-83.2): the tenant's roles and the actions each holds, read only. A row expands to its
 * action codes, named from the action catalogue; if the catalogue cannot be read, the codes alone.
 */
export function RolesScreen() {
  const { token } = theme.useToken();
  const canRead = useCan('core.role.read');
  const [roles, setRoles] = useState([]);
  const [catalogue, setCatalogue] = useState({});
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!canRead) return undefined;
    let live = true;
    setLoading(true);
    rolesService
      .list()
      .then((data) => live && setRoles(data))
      .catch((err) => live && errorMsg(err))
      .finally(() => live && setLoading(false));
    rolesService
      .actions()
      .then((data) => live && setCatalogue(Object.fromEntries(data.map((a) => [a.code, a]))))
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [canRead]);

  if (!canRead) {
    return <NotEntitled />;
  }

  const columns = [
    {
      title: 'Role',
      dataIndex: 'name',
      key: 'name',
      render: (name) => <Text strong style={{ color: token.colorText }}>{name}</Text>,
    },
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (code) => (
        <Tag style={{ borderRadius: 6, fontWeight: 600, background: token.colorInfoBg, color: token.colorInfoText, borderColor: token.colorInfoBorder }}>
          {code}
        </Tag>
      ),
    },
    {
      title: 'System?',
      dataIndex: 'system',
      key: 'system',
      render: (system) =>
        system ? (
          <Tag color="blue" style={{ borderRadius: 6, fontWeight: 500 }}>
            System
          </Tag>
        ) : (
          <Tag style={{ borderRadius: 6, fontWeight: 500 }}>
            Custom
          </Tag>
        ),
    },
    {
      title: 'Actions held',
      dataIndex: 'actionCodes',
      key: 'actionCodes',
      render: (codes) => (
        <Tag style={{ borderRadius: 12, fontWeight: 600, background: token.colorFillSecondary, color: token.colorTextSecondary, padding: '2px 8px' }}>
          {(codes || []).length}
        </Tag>
      ),
    },
  ];

  const renderActions = (role) => (
    <List
      size="small"
      dataSource={role.actionCodes || []}
      locale={{ emptyText: 'This role holds no actions.' }}
      renderItem={(code) => {
        const action = catalogue[code];
        return (
          <List.Item>
            <List.Item.Meta
              title={<Text code>{code}</Text>}
              description={action ? action.description || action.name : null}
            />
          </List.Item>
        );
      }}
    />
  );

  return (
    <div className="saas-page-container fadeSlideUp">
      <Breadcrumb items={[{ title: 'Home' }, { title: 'Roles' }]} style={{ marginBottom: 16 }} />
      <PageHeader title="Roles" subtitle="View assigned system and custom security roles with permissions matrix." />
      <Card className="saas-card" variant="borderless">
        <Table
          className="saas-table"
          rowKey="id"
          columns={columns}
          dataSource={roles}
          loading={loading}
          pagination={false}
          expandable={{ expandedRowRender: renderActions }}
        />
      </Card>
    </div>
  );
}
