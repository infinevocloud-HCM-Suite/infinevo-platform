import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import {
  Table,
  Button,
  Switch,
  Space,
  Typography,
  Card,
  Popconfirm,
  Modal,
  Tag,
  Tooltip,
  theme,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { workLocationService } from './workLocationService.js';
import { invalidateMasters } from '../employee/employeeSlice.js';

const { Title, Text } = Typography;

export function WorkLocations() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const canManage = useCan('core.org.manage');

  const [loading, setLoading] = useState(false);
  const [locations, setLocations] = useState([]);

  // 409 Conflict modal state
  const [conflictModal, setConflictModal] = useState({
    open: false,
    message: '',
    record: null,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await workLocationService.list(false);
      setLocations(Array.isArray(data) ? data : []);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleToggleActive = async (record) => {
    try {
      await workLocationService.update(record.id, {
        ...record,
        active: !record.active,
      });
      dispatch(invalidateMasters());
      await successMsg(
        'Status Changed',
        `${record.name} is now ${!record.active ? 'active' : 'inactive'}.`
      );
      loadData();
    } catch (err) {
      await errorMsg(err);
    }
  };

  const handleDelete = async (record) => {
    try {
      await workLocationService.remove(record.id);
      dispatch(invalidateMasters());
      await successMsg('Location Deleted', `${record.name} deleted successfully.`);
      loadData();
    } catch (err) {
      if (err?.status === 409 || err?.code === 'CONFLICT') {
        setConflictModal({
          open: true,
          message: err.message || 'Cannot delete this work location.',
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
      await workLocationService.update(record.id, {
        ...record,
        active: false,
      });
      dispatch(invalidateMasters());
      setConflictModal({ open: false, message: '', record: null });
      await successMsg('Location Deactivated', `${record.name} has been deactivated instead of deleted.`);
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
      width: 140,
      render: (code) => <Tag>{code}</Tag>,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name) => <Text strong>{name}</Text>,
    },
    {
      title: 'City',
      dataIndex: 'city',
      key: 'city',
      width: 160,
      render: (city) => city || '—',
    },
    {
      title: 'State',
      dataIndex: 'state',
      key: 'state',
      width: 160,
      render: (state, record) => [state, record.stateCode ? `(${record.stateCode})` : null].filter(Boolean).join(' ') || '—',
    },
    {
      title: 'Filing Address',
      dataIndex: 'filingAddress',
      key: 'filingAddress',
      width: 150,
      render: (isFiling) =>
        isFiling ? (
          <Tag color="gold" id="tag-filing-address">
            Filing Address
          </Tag>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 110,
      render: (active, record) => (
        <Switch
          checked={active}
          disabled={!canManage}
          onChange={() => handleToggleActive(record)}
          id={`switch-active-${record.id}`}
        />
      ),
    },
  ];

  if (canManage) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/org/work-locations/${record.id}/edit`)}
            id={`btn-edit-location-${record.id}`}
          />
          {record.filingAddress ? (
            <Tooltip title="Filing address cannot be deleted. Statutory registrations are tied to it.">
              <Button
                type="text"
                disabled
                size="small"
                icon={<DeleteOutlined />}
                id={`btn-delete-location-${record.id}`}
              />
            </Tooltip>
          ) : (
            <Popconfirm
              title="Delete Work Location?"
              description="Are you sure you want to delete this location?"
              onConfirm={() => handleDelete(record)}
              okText="Yes"
              cancelText="No"
            >
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                id={`btn-delete-location-${record.id}`}
              />
            </Popconfirm>
          )}
        </Space>
      ),
    });
  }

  return (
    <Card style={{ margin: token.marginLG }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: token.marginLG,
        }}
      >
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Work Locations
          </Title>
          <Text type="secondary">Manage your organization&apos;s physical office and work locations.</Text>
        </div>
        {canManage && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/org/work-locations/new')}
            id="btn-new-location"
          >
            New Location
          </Button>
        )}
      </div>

      <Table
        dataSource={locations}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 15 }}
      />

      <Modal
        title="Cannot Delete Work Location"
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
          {/* The reason is the server's sentence above - in use, or the filing address - so
              this line only offers the way out and does not guess at the reason. */}
          <Text>You can deactivate this location instead of deleting it.</Text>
        </Space>
      </Modal>
    </Card>
  );
}
