import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Table,
  Button,
  Tag,
  Space,
  Typography,
  Modal,
  theme,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { roleService } from './roleService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text, Paragraph } = Typography;

export function Roles() {
  const canRead = useCan('core.role.read');
  const canManage = useCan('core.role.manage');
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const [loading, setLoading] = useState(true);
  const [roles, setRoles] = useState([]);

  const fetchRoles = useCallback(async () => {
    setLoading(true);
    try {
      const data = await roleService.list();
      setRoles(Array.isArray(data) ? data : []);
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canRead) {
      fetchRoles();
    }
  }, [canRead, fetchRoles]);

  if (!canRead) {
    return <NotEntitled action="core.role.read" />;
  }

  const handleDelete = (role) => {
    Modal.confirm({
      title: 'Delete Role',
      content: `Are you sure you want to delete role "${role.name}" (${role.code})? This action cannot be undone.`,
      okText: 'Delete',
      okType: 'danger',
      onOk: async () => {
        try {
          await roleService.remove(role.id);
          await successMsg('Role Deleted', `Role "${role.name}" has been removed.`);
          fetchRoles();
        } catch (err) {
          await errorMsg(err);
        }
      },
    });
  };

  const columns = [
    {
      title: 'Role Name',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (name, record) => (
        <Space orientation="vertical" size={2}>
          <Text strong style={{ fontSize: 14 }}>{name}</Text>
          <Text type="secondary" style={{ fontSize: 12, fontFamily: 'monospace' }}>
            {record.code}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'system',
      key: 'system',
      width: 130,
      render: (system) =>
        system ? (
          <Tag icon={<LockOutlined />} color="purple">
            System
          </Tag>
        ) : (
          <Tag icon={<SafetyCertificateOutlined />} color="blue">
            Custom
          </Tag>
        ),
    },
    {
      title: 'Assigned Permissions',
      dataIndex: 'actionCodes',
      key: 'actionCodes',
      render: (actionCodes) => {
        const count = actionCodes?.length || 0;
        if (count === 0) {
          return <Text type="secondary">None</Text>;
        }
        return (
          <Space wrap size={[4, 4]}>
            <Tag color="cyan">{count} permissions</Tag>
            {actionCodes.slice(0, 3).map((code) => (
              <Tag key={code} style={{ fontSize: 11 }}>
                {code}
              </Tag>
            ))}
            {count > 3 && (
              <Tooltip title={actionCodes.slice(3).join(', ')}>
                <Tag style={{ fontSize: 11, cursor: 'pointer' }}>+{count - 3} more</Tag>
              </Tooltip>
            )}
          </Space>
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 140,
      align: 'right',
      render: (_, record) => {
        if (record.system) {
          return (
            <Tooltip title="System roles are built-in and cannot be modified.">
              <Text type="secondary" style={{ fontSize: 12 }}>
                <LockOutlined /> Read-only
              </Text>
            </Tooltip>
          );
        }
        return (
          <Space size="small">
            {canManage && (
              <>
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => navigate(`/roles/${record.id}`)}
                  title="Edit Role"
                  id={`btn-edit-role-${record.id}`}
                />
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => handleDelete(record)}
                  title="Delete Role"
                  id={`btn-delete-role-${record.id}`}
                />
              </>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ padding: '0 0 24px 0' }}>
      <Card variant="borderless" style={{ borderRadius: token.borderRadiusLG, marginBottom: token.marginLG }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              Roles & Permissions
            </Title>
            <Paragraph type="secondary" style={{ margin: 0, marginTop: 4 }}>
              Manage access controls, predefined system roles, and custom role definitions.
            </Paragraph>
          </div>
          {canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/roles/new')}
              id="btn-new-role"
            >
              New Role
            </Button>
          )}
        </div>
      </Card>

      <Card variant="borderless" style={{ borderRadius: token.borderRadiusLG }}>
        <Table
          id="table-roles"
          rowKey="id"
          columns={columns}
          dataSource={roles}
          loading={loading}
          pagination={{ pageSize: 15, showSizeChanger: true }}
        />
      </Card>
    </div>
  );
}
export default Roles;
