import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Table, Input, Select, Switch, Button, Tag, Space, Typography, Card, Alert, Modal, theme, Avatar } from 'antd';
import { PlusOutlined, UserOutlined, UploadOutlined, MailOutlined, DownloadOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { employeeService } from './employeeService.js';
import { employeeImportService } from './employeeImportService.js';
import { orgMasterService } from './orgMasterService.js';
import { setMasters } from './employeeSlice.js';
import { formatDate, statusLabel } from '@shared/ui/format.js';

const { Title, Text } = Typography;

const statusColorStyles = {
  ACTIVE: { background: '#ecfdf5', color: '#059669', border: '#a7f3d0' },
  SUSPENDED: { background: '#fffbeb', color: '#d97706', border: '#fde68a' },
  TERMINATED: { background: '#fff1f2', color: '#e11d48', border: '#fecdd3' },
};

export function EmployeeList() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { token } = theme.useToken();

  const canCreate = useCan('core.employee.create');
  const canDelete = useCan('core.employee.delete');
  const canExport = useCan('core.employee.export');

  const masters = useSelector((state) => state.employee?.masters || {});
  const mastersLoadedAt = useSelector((state) => state.employee?.loadedAt);

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState(undefined);
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [loadError, setLoadError] = useState(null);

  // Load masters once per session
  useEffect(() => {
    if (!mastersLoadedAt) {
      orgMasterService.all().then((res) => {
        dispatch(setMasters(res));
      }).catch(() => {});
    }
  }, [dispatch, mastersLoadedAt]);

  const fetchEmployees = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await employeeService.list({
        q: searchQuery || undefined,
        status: statusFilter || undefined,
        includeDeleted: canDelete ? includeDeleted : false,
        page: page - 1,
        size: pageSize,
        sort: 'employeeNumber,asc',
      });
      setData(res.content || []);
      setTotal(res.totalElements || 0);
    } catch (err) {
      // A failed load must not look like "no employees": say so, and offer a retry.
      setData([]);
      setTotal(0);
      setLoadError(err?.message || 'Something went wrong');
    } finally {
      setLoading(false);
    }
  }, [searchQuery, statusFilter, includeDeleted, page, pageSize, canDelete]);

  useEffect(() => {
    fetchEmployees();
  }, [fetchEmployees]);

  // W-73.7: one job inviting every active employee with a work email and no account or live invitation,
  // with the employee role only. The count comes first so the confirm says how many will get an email.
  const handleInviteAll = async () => {
    let count;
    try {
      count = await employeeImportService.inviteAllCount();
    } catch (err) {
      await errorMsg(err);
      return;
    }
    if (!count) {
      await successMsg('Nobody to invite', 'Every employee with a work email already has access or an invitation.');
      return;
    }
    Modal.confirm({
      title: 'Invite all without access',
      content: `Send a portal invitation to ${count} employee${count === 1 ? '' : 's'} with a work email and no access yet? They get the Employee role only.`,
      okText: `Invite ${count}`,
      onOk: async () => {
        try {
          await employeeImportService.inviteAll();
          await successMsg('Invitations queued', 'Progress and the result file are on the Import page.');
          navigate('/employees/import');
        } catch (err) {
          await errorMsg(err);
        }
      },
    });
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const res = await employeeService.export({
        ...(statusFilter ? { status: statusFilter } : {}),
      });
      if (res?.url) {
        employeeService.downloadFile(res.url, res.fileName || 'employees.xlsx');
        await successMsg('Export downloaded', `Exported ${res.rowCount || 0} employees.`);
      } else {
        throw new Error('Export link not returned by server');
      }
    } catch (err) {
      await errorMsg(err);
    } finally {
      setExporting(false);
    }
  };

  const columns = [
    {
      title: 'Emp ID',
      dataIndex: 'employeeNumber',
      key: 'employeeNumber',
      render: (num) => (
        <span style={{ fontWeight: 600, color: '#0f172a', fontFamily: 'monospace' }}>
          {num}
        </span>
      ),
    },
    {
      title: 'Name',
      key: 'name',
      render: (_, r) => {
        const fullName = [r.firstName, r.middleName, r.lastName].filter(Boolean).join(' ');
        const initials = [r.firstName?.[0], r.lastName?.[0]].filter(Boolean).join('').toUpperCase() || 'U';
        return (
          <Space size="middle">
            <Avatar
              size={32}
              style={{
                backgroundColor: '#e0f2fe',
                color: '#0284c7',
                fontWeight: 700,
                fontSize: 12,
                border: '1px solid #bae6fd',
              }}
              icon={!initials ? <UserOutlined /> : undefined}
            >
              {initials}
            </Avatar>
            <div style={{ display: 'inline-flex', flexDirection: 'column' }}>
              <Text strong style={{ color: '#0f172a' }}>{fullName}</Text>
            </div>
          </Space>
        );
      },
    },
    {
      title: 'Work Email',
      dataIndex: 'workEmail',
      key: 'workEmail',
      render: (email) => <span style={{ color: email ? '#334155' : '#94a3b8' }}>{email || '—'}</span>,
    },
    {
      title: 'Department',
      dataIndex: 'departmentId',
      key: 'department',
      render: (id) => <span style={{ color: '#334155' }}>{(id && masters.departments?.[id]) || '—'}</span>,
    },
    {
      title: 'Designation',
      dataIndex: 'designationId',
      key: 'designation',
      render: (id) => <span style={{ color: '#334155' }}>{(id && masters.designations?.[id]) || '—'}</span>,
    },
    {
      title: 'Location',
      dataIndex: 'workLocationId',
      key: 'workLocation',
      render: (id) => <span style={{ color: '#334155' }}>{(id && masters.workLocations?.[id]) || '—'}</span>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status, r) => {
        const customStyle = statusColorStyles[status] || { background: '#f1f5f9', color: '#475569', border: '#e2e8f0' };
        return (
          <Space size="small">
            <Tag
              style={{
                borderRadius: 12,
                padding: '2px 10px',
                fontWeight: 600,
                fontSize: 11,
                backgroundColor: customStyle.background,
                color: customStyle.color,
                borderColor: customStyle.border,
              }}
            >
              {statusLabel(status)}
            </Tag>
            {r.isDeleted && <Tag color="error" style={{ borderRadius: 12 }}>Deleted</Tag>}
          </Space>
        );
      },
    },
    {
      title: 'Date of Joining',
      dataIndex: 'dateOfJoining',
      key: 'dateOfJoining',
      render: (date) => <span style={{ color: '#64748b' }}>{formatDate(date)}</span>,
    },
  ];

  return (
    <Card
      variant="borderless"
      style={{
        borderRadius: 12,
        border: '1px solid #e2e8f0',
        boxShadow: '0 1px 3px 0 rgba(15, 23, 42, 0.05)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: token.marginLG }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Title level={4} style={{ margin: 0, color: '#0f172a' }}>Employees</Title>
          <Tag
            style={{
              borderRadius: 12,
              backgroundColor: '#f0f9ff',
              color: '#0284c7',
              borderColor: '#bae6fd',
              fontWeight: 600,
              padding: '2px 10px',
            }}
          >
            {total} Active Staff
          </Tag>
        </div>
        <Space wrap>
          {canExport && (
            <Button
              icon={<DownloadOutlined />}
              onClick={handleExport}
              loading={exporting}
              id="btn-export-employees"
              style={{ borderRadius: 8, borderColor: '#cbd5e1' }}
            >
              Export
            </Button>
          )}
          {canCreate && (
            <>
              <Button
                icon={<MailOutlined />}
                onClick={handleInviteAll}
                id="btn-invite-all"
                style={{ borderRadius: 8, borderColor: '#cbd5e1' }}
              >
                Invite all without access
              </Button>
              <Button
                icon={<UploadOutlined />}
                onClick={() => navigate('/employees/import')}
                id="btn-import-employees"
                style={{ borderRadius: 8, borderColor: '#cbd5e1' }}
              >
                Import
              </Button>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => navigate('/employees/new')}
                id="btn-new-employee"
                style={{
                  borderRadius: 8,
                  backgroundColor: '#2563eb',
                  boxShadow: '0 1px 2px 0 rgba(37, 99, 235, 0.2)',
                }}
              >
                New Employee
              </Button>
            </>
          )}
        </Space>
      </div>

      <div style={{ display: 'flex', gap: token.margin, flexWrap: 'wrap', marginBottom: token.marginLG, alignItems: 'center' }}>
        <Input.Search
          placeholder="Search employees..."
          allowClear
          onSearch={(val) => {
            setSearchQuery(val);
            setPage(1);
          }}
          style={{ width: 280 }}
          id="input-search-employees"
        />

        <Select
          placeholder="Filter by Status"
          allowClear
          value={statusFilter}
          onChange={(val) => {
            setStatusFilter(val);
            setPage(1);
          }}
          style={{ width: 160 }}
          options={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'SUSPENDED', label: 'Suspended' },
            { value: 'TERMINATED', label: 'Terminated' },
          ]}
          id="select-status-filter"
        />

        {canDelete && (
          <Space style={{ marginLeft: 'auto' }} id="wrapper-include-deleted">
            <Text type="secondary">Include deleted:</Text>
            <Switch
              checked={includeDeleted}
              onChange={(checked) => {
                setIncludeDeleted(checked);
                setPage(1);
              }}
              id="switch-include-deleted"
            />
          </Space>
        )}
      </div>

      {loadError && (
        <Alert
          type="error"
          showIcon
          message="Employees could not be loaded"
          description={loadError}
          action={
            <Button size="small" onClick={fetchEmployees} id="btn-retry-employees">
              Retry
            </Button>
          }
          style={{ marginBottom: token.marginLG, borderRadius: 8 }}
          id="alert-employees-load-error"
        />
      )}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
        onRow={(record) => ({
          onClick: () => navigate(`/employees/${record.id}`),
          style: { cursor: 'pointer' },
          'data-testid': `employee-row-${record.id}`,
        })}
      />
    </Card>
  );
}
