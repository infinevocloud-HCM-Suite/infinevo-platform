import { useEffect, useState, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useDispatch } from 'react-redux';
import {
  Table,
  Button,
  Input,
  Switch,
  Space,
  Typography,
  Card,
  Popconfirm,
  Modal,
  Tag,
  theme,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { invalidateMasters } from '../employee/employeeSlice.js';

const { Title, Text } = Typography;

export function MasterTable({ title, service }) {
  const { token } = theme.useToken();
  const dispatch = useDispatch();
  const canManage = useCan('core.org.manage');

  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState([]);

  // New row inline input
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [adding, setAdding] = useState(false);

  // Inline editing
  const [editingId, setEditingId] = useState(null);
  const [editCode, setEditCode] = useState('');
  const [editName, setEditName] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);

  // 409 Conflict modal state
  const [conflictModal, setConflictModal] = useState({
    open: false,
    message: '',
    record: null,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await service.list(false);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [service]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleAdd = async () => {
    if (!newCode.trim() || !newName.trim()) {
      return;
    }
    setAdding(true);
    try {
      await service.create({
        code: newCode.trim().toUpperCase(),
        name: newName.trim(),
        active: true,
      });
      dispatch(invalidateMasters());
      await successMsg(`${title} Created`, `${title} ${newCode.trim()} created successfully.`);
      setNewCode('');
      setNewName('');
      loadData();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setAdding(false);
    }
  };

  const handleStartEdit = (record) => {
    setEditingId(record.id);
    setEditCode(record.code || '');
    setEditName(record.name || '');
  };

  const handleCancelEdit = () => {
    setEditingId(null);
    setEditCode('');
    setEditName('');
  };

  const handleSaveEdit = async (record) => {
    if (!editCode.trim() || !editName.trim()) {
      return;
    }
    setSavingEdit(true);
    try {
      await service.update(record.id, {
        code: editCode.trim().toUpperCase(),
        name: editName.trim(),
        active: record.active,
      });
      dispatch(invalidateMasters());
      await successMsg(`${title} Updated`, `${title} updated successfully.`);
      setEditingId(null);
      loadData();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSavingEdit(false);
    }
  };

  const handleToggleActive = async (record) => {
    try {
      await service.update(record.id, {
        code: record.code,
        name: record.name,
        active: !record.active,
      });
      dispatch(invalidateMasters());
      await successMsg(
        `${title} Status Changed`,
        `${record.name} is now ${!record.active ? 'active' : 'inactive'}.`
      );
      loadData();
    } catch (err) {
      await errorMsg(err);
    }
  };

  const handleDelete = async (record) => {
    try {
      await service.remove(record.id);
      dispatch(invalidateMasters());
      await successMsg(`${title} Deleted`, `${record.name} deleted successfully.`);
      loadData();
    } catch (err) {
      if (err?.status === 409 || err?.code === 'CONFLICT') {
        setConflictModal({
          open: true,
          message: err.message || 'This record is currently in use and cannot be deleted.',
          record,
        });
      } else {
        await errorMsg(err);
      }
    }
  };

  const handleDeactivateInstead = async () => {
    const record = conflictModal.record;
    if (!record) return;
    try {
      await service.update(record.id, {
        code: record.code,
        name: record.name,
        active: false,
      });
      dispatch(invalidateMasters());
      setConflictModal({ open: false, message: '', record: null });
      await successMsg('Record Deactivated', `${record.name} has been deactivated instead of deleted.`);
      loadData();
    } catch (err) {
      await errorMsg(err);
    }
  };

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 180,
      render: (text, record) => {
        if (editingId === record.id) {
          return (
            <Input
              value={editCode}
              onChange={(e) => setEditCode(e.target.value)}
              id={`input-edit-code-${record.id}`}
            />
          );
        }
        return <Tag>{text}</Tag>;
      },
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => {
        if (editingId === record.id) {
          return (
            <Input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              id={`input-edit-name-${record.id}`}
            />
          );
        }
        return <Text strong>{text}</Text>;
      },
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 120,
      render: (active, record) => (
        <Switch
          checked={active}
          disabled={!canManage}
          onChange={() => handleToggleActive(record)}
          id={`switch-active-${record.id}`}
        />
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (date) => (date ? new Date(date).toLocaleDateString() : '—'),
    },
  ];

  if (canManage) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      width: 160,
      render: (_, record) => {
        if (editingId === record.id) {
          return (
            <Space size="small">
              <Button
                type="primary"
                size="small"
                icon={<SaveOutlined />}
                loading={savingEdit}
                onClick={() => handleSaveEdit(record)}
                id={`btn-save-edit-${record.id}`}
              >
                Save
              </Button>
              <Button size="small" icon={<CloseOutlined />} onClick={handleCancelEdit}>
                Cancel
              </Button>
            </Space>
          );
        }
        return (
          <Space size="small">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleStartEdit(record)}
              id={`btn-edit-${record.id}`}
            />
            <Popconfirm
              title={`Delete ${title}?`}
              description="Are you sure you want to delete this record?"
              onConfirm={() => handleDelete(record)}
              okText="Yes"
              cancelText="No"
            >
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                id={`btn-delete-${record.id}`}
              />
            </Popconfirm>
          </Space>
        );
      },
    });
  }

  return (
    <Card style={{ margin: token.marginLG }}>
      <div style={{ marginBottom: token.marginLG }}>
        <Title level={3} style={{ marginBottom: 4 }}>
          {title}
        </Title>
        <Text type="secondary">Manage your organization&apos;s {title.toLowerCase()} catalogue.</Text>
      </div>

      {canManage && (
        <Card
          size="small"
          style={{
            marginBottom: token.marginLG,
            background: token.colorBgContainer,
            border: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Space wrap size="middle">
            <Input
              placeholder="Code (e.g. ENG)"
              value={newCode}
              onChange={(e) => setNewCode(e.target.value)}
              style={{ width: 160 }}
              id="input-new-code"
            />
            <Input
              placeholder="Name (e.g. Engineering)"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              style={{ width: 260 }}
              id="input-new-name"
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              loading={adding}
              disabled={!newCode.trim() || !newName.trim()}
              onClick={handleAdd}
              id="btn-add-master"
            >
              Add {title}
            </Button>
          </Space>
        </Card>
      )}

      <Table
        dataSource={items}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 15 }}
      />

      <Modal
        title="Cannot Delete Record"
        open={conflictModal.open}
        onCancel={() => setConflictModal({ open: false, message: '', record: null })}
        footer={[
          <Button key="cancel" onClick={() => setConflictModal({ open: false, message: '', record: null })}>
            Cancel
          </Button>,
          <Button key="deactivate" type="primary" onClick={handleDeactivateInstead} id="btn-deactivate-instead">
            Deactivate Instead
          </Button>,
        ]}
      >
        <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
          <Text type="danger">{conflictModal.message}</Text>
          <Text>
            This {title.toLowerCase()} is assigned to active employees or records. You can deactivate it to prevent
            new assignments while keeping existing references intact.
          </Text>
        </Space>
      </Modal>
    </Card>
  );
}

MasterTable.propTypes = {
  title: PropTypes.string.isRequired,
  service: PropTypes.shape({
    list: PropTypes.func.isRequired,
    create: PropTypes.func.isRequired,
    update: PropTypes.func.isRequired,
    remove: PropTypes.func.isRequired,
  }).isRequired,
};
