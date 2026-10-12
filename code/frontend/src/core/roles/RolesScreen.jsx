import { useEffect, useState } from 'react';
import { Card, Table, Tag, Typography, Breadcrumb, List } from 'antd';
import { useCan, NotEntitled } from '@shell/screens';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { rolesService } from './rolesService.js';

const { Title, Text } = Typography;

/**
 * Roles (D-73): the tenant's roles and the actions each holds, read only. A row expands to its
 * action codes, named from the action catalogue; if the catalogue cannot be read, the codes alone.
 */
export function RolesScreen() {
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
    { title: 'Role', dataIndex: 'name', key: 'name' },
    { title: 'Code', dataIndex: 'code', key: 'code' },
    {
      title: 'System?',
      dataIndex: 'system',
      key: 'system',
      render: (system) => (system ? <Tag color="blue">System</Tag> : <Tag>Custom</Tag>),
    },
    {
      title: 'Actions held',
      dataIndex: 'actionCodes',
      key: 'actionCodes',
      render: (codes) => (codes || []).length,
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
    <div style={{ padding: 24 }}>
      <Breadcrumb items={[{ title: 'Home' }, { title: 'Roles' }]} style={{ marginBottom: 16 }} />
      <Title level={3} style={{ marginTop: 0 }}>
        Roles
      </Title>
      <Card>
        <Table
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
