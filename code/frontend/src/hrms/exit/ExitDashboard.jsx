import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Select, Space, Table, Tag, message } from 'antd';
import { NotEntitled, useCan } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { ClearanceDrawer } from './ClearanceDrawer.jsx';
import { exitService } from './exitService.js';

const STATUS_OPTIONS = [
  { value: '', label: 'All Statuses' },
  { value: 'SUBMITTED', label: 'Submitted' },
  { value: 'CLEARANCE_IN_PROGRESS', label: 'Clearance in Progress' },
  { value: 'READY_FOR_FNF', label: 'Ready for F&F' },
  { value: 'SETTLED', label: 'Settled' },
  { value: 'REJECTED', label: 'Rejected' },
];

const STATUS_COLORS = {
  SUBMITTED: 'gold',
  CLEARANCE_IN_PROGRESS: 'processing',
  READY_FOR_FNF: 'cyan',
  SETTLED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'default',
};

/** HR management dashboard of active employee exits under /hrms/exits (W-103 §7). */
export function ExitDashboard() {
  const canRead = useCan('core.employee.read');
  const canManage = useCan('core.employee.manage');
  const [statusFilter, setStatusFilter] = useState('');
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedExit, setSelectedExit] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await exitService.listExits(statusFilter || undefined);
      setRows(data);
    } catch (err) {
      setError(err?.message || 'Failed to load exit requests');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (canRead) load();
  }, [canRead, load]);

  if (!canRead) return <NotEntitled />;

  const handleApprove = async (record) => {
    try {
      const updated = await exitService.approveExit(record.id, record.requestedLwd);
      message.success('Exit approved and clearance checklist created');
      setRows((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    } catch (err) {
      message.error(err?.message || 'Approval failed');
    }
  };

  const handleHandOff = async (record) => {
    try {
      await exitService.handOffToPayroll(record.id);
      message.success('Handed off to Payroll for F&F settlement');
      await load();
    } catch (err) {
      message.error(err?.message || 'Hand-off failed');
    }
  };

  const handleDrawerUpdated = (updated) => {
    setSelectedExit(updated);
    setRows((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
  };

  const columns = [
    { title: 'Employee', dataIndex: 'employeeName', key: 'employeeName' },
    { title: 'Resignation Date', dataIndex: 'resignationDate', key: 'resignationDate' },
    {
      title: 'Last Working Day',
      key: 'lwd',
      render: (_, r) => r.approvedLwd || r.requestedLwd,
    },
    { title: 'Notice (Days)', dataIndex: 'noticePeriodDays', key: 'noticePeriodDays' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => <Tag color={STATUS_COLORS[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, r) => (
        <Space size="small">
          {canManage && r.status === 'SUBMITTED' && (
            <Button size="small" type="primary" onClick={() => handleApprove(r)}>
              Approve
            </Button>
          )}
          {(r.status === 'CLEARANCE_IN_PROGRESS' || r.status === 'READY_FOR_FNF') && (
            <Button size="small" onClick={() => setSelectedExit(r)}>
              Clearances
            </Button>
          )}
          {canManage && r.status === 'READY_FOR_FNF' && (
            <Button size="small" type="primary" onClick={() => handleHandOff(r)}>
              Hand Off to Payroll
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <PageHeader
        title="Exit Management"
        subtitle="Review resignations, track departmental clearances, and hand off to Payroll."
        extra={
          <Select
            style={{ width: 220 }}
            value={statusFilter}
            onChange={setStatusFilter}
            options={STATUS_OPTIONS}
          />
        }
      />

      {error && <Alert type="error" message={error} showIcon />}

      <Card>
        <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} pagination={false} />
      </Card>

      <ClearanceDrawer
        exit={selectedExit}
        open={Boolean(selectedExit)}
        onClose={() => setSelectedExit(null)}
        onUpdated={handleDrawerUpdated}
      />
    </Space>
  );
}
