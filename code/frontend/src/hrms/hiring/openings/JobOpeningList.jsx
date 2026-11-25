import { useState, useEffect, useCallback } from 'react';
import { Table, Tag, Button, Space, Card, Select, Typography, message, theme } from 'antd';
import { PlusOutlined, EyeOutlined, CheckCircleOutlined, PauseCircleOutlined, StopOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { hiringService } from '../hiringService.js';
import { JobOpeningDrawer } from './JobOpeningDrawer.jsx';

const { Text } = Typography;

export function JobOpeningList() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const canManage = useCan('hrms.hiring.manage');

  const [loading, setLoading] = useState(true);
  const [openings, setOpenings] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingOpening, setEditingOpening] = useState(null);

  const fetchOpenings = useCallback(async () => {
    setLoading(true);
    try {
      const data = await hiringService.openings.list(statusFilter || undefined);
      setOpenings(data);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load job openings');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchOpenings();
  }, [fetchOpenings]);

  const handleStatusChange = async (id, newStatus) => {
    try {
      await hiringService.openings.updateStatus(id, newStatus);
      message.success(`Opening status changed to ${newStatus}`);
      fetchOpenings();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Status change failed');
    }
  };

  const statusColors = {
    DRAFT: 'default',
    OPEN: 'processing',
    ON_HOLD: 'warning',
    FILLED: 'success',
    CLOSED: 'error',
  };

  const columns = [
    {
      title: 'Code',
      dataIndex: 'openingCode',
      key: 'openingCode',
      render: (code, row) => (
        <a
          onClick={() => navigate(`/hrms/hiring/openings/${row.id}`)}
          style={{ fontWeight: 600, color: token.colorPrimary }}
        >
          {code}
        </a>
      ),
    },
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
      render: (title, row) => (
        <Space direction="vertical" size={2}>
          <Text strong>{title}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {row.departmentName || 'General'} • {row.workLocationName || 'All Locations'}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'employmentType',
      key: 'employmentType',
      render: (type) => <Tag>{type || 'FULL_TIME'}</Tag>,
    },
    {
      title: 'Headcount',
      key: 'headcount',
      render: (_, row) => (
        <span>
          <Text strong>{row.filledCount}</Text> / {row.openingsCount}
        </span>
      ),
    },
    {
      title: 'Candidates',
      dataIndex: 'totalCandidates',
      key: 'totalCandidates',
      render: (total, row) => (
        <Space size={4}>
          <Tag color="blue">{total || 0} Total</Tag>
          {row.interviewingCount > 0 && <Tag color="purple">{row.interviewingCount} Intvw</Tag>}
          {row.offeredCount > 0 && <Tag color="orange">{row.offeredCount} Offer</Tag>}
          {row.hiredCount > 0 && <Tag color="green">{row.hiredCount} Hired</Tag>}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => <Tag color={statusColors[status] || 'default'}>{status}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/hrms/hiring/openings/${row.id}`)}
          >
            Pipeline
          </Button>
          {canManage && row.status === 'DRAFT' && (
            <Button
              size="small"
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={() => handleStatusChange(row.id, 'OPEN')}
            >
              Publish
            </Button>
          )}
          {canManage && row.status === 'OPEN' && (
            <>
              <Button
                size="small"
                icon={<PauseCircleOutlined />}
                onClick={() => handleStatusChange(row.id, 'ON_HOLD')}
              >
                Hold
              </Button>
              <Button
                size="small"
                danger
                icon={<StopOutlined />}
                onClick={() => handleStatusChange(row.id, 'CLOSED')}
              >
                Close
              </Button>
            </>
          )}
          {canManage && row.status === 'ON_HOLD' && (
            <Button
              size="small"
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={() => handleStatusChange(row.id, 'OPEN')}
            >
              Reopen
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Job Openings"
        subtitle="Manage recruitment requisitions, headcounts, and candidate pipelines"
        actions={
          canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingOpening(null);
                setDrawerOpen(true);
              }}
            >
              Create Job Opening
            </Button>
          )
        }
      />

      <Card style={{ marginTop: 16 }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Text type="secondary">Status:</Text>
            <Select
              style={{ width: 160 }}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: '', label: 'All Openings' },
                { value: 'DRAFT', label: 'Draft' },
                { value: 'OPEN', label: 'Open' },
                { value: 'ON_HOLD', label: 'On Hold' },
                { value: 'FILLED', label: 'Filled' },
                { value: 'CLOSED', label: 'Closed' },
              ]}
            />
          </Space>
          <Text type="secondary">{openings.length} opening{openings.length === 1 ? '' : 's'}</Text>
        </div>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={openings}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <JobOpeningDrawer
        open={drawerOpen}
        initialValues={editingOpening}
        onClose={() => setDrawerOpen(false)}
        onSuccess={fetchOpenings}
      />
    </div>
  );
}

