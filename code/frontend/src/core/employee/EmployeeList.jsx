import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Table, Input, Select, Switch, Button, Tag, Space, Typography, Card, theme } from 'antd';
import { PlusOutlined, UserOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';
import { setMasters } from './employeeSlice.js';

const { Title, Text } = Typography;

const statusColorMap = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  TERMINATED: 'error',
};

export function EmployeeList() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { token } = theme.useToken();

  const canCreate = useCan('core.employee.create');
  const canDelete = useCan('core.employee.delete');

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
    try {
      const res = await employeeService.list({
        q: searchQuery || undefined,
        status: statusFilter || undefined,
        includeDeleted: canDelete ? includeDeleted : false,
        page: page - 1,
        size: pageSize,
        sort: 'lastName,asc',
      });
      setData(res.content || []);
      setTotal(res.totalElements || 0);
    } catch {
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [searchQuery, statusFilter, includeDeleted, page, pageSize, canDelete]);

  useEffect(() => {
    fetchEmployees();
  }, [fetchEmployees]);

  const columns = [
    {
      title: 'Emp ID',
      dataIndex: 'employeeNumber',
      key: 'employeeNumber',
      render: (num) => <Text strong>{num}</Text>,
    },
    {
      title: 'Name',
      key: 'name',
      render: (_, r) => {
        const fullName = [r.firstName, r.middleName, r.lastName].filter(Boolean).join(' ');
        return (
          <Space size="small">
            <UserOutlined style={{ color: token.colorPrimary }} />
            <Text>{fullName}</Text>
          </Space>
        );
      },
    },
    {
      title: 'Work Email',
      dataIndex: 'workEmail',
      key: 'workEmail',
      render: (email) => email || '—',
    },
    {
      title: 'Department',
      dataIndex: 'departmentId',
      key: 'department',
      render: (id) => (id && masters.departments?.[id]) || '—',
    },
    {
      title: 'Designation',
      dataIndex: 'designationId',
      key: 'designation',
      render: (id) => (id && masters.designations?.[id]) || '—',
    },
    {
      title: 'Location',
      dataIndex: 'workLocationId',
      key: 'workLocation',
      render: (id) => (id && masters.workLocations?.[id]) || '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status, r) => (
        <Space size="small">
          <Tag color={statusColorMap[status] || 'default'}>{status}</Tag>
          {r.isDeleted && <Tag color="error">DELETED</Tag>}
        </Space>
      ),
    },
    {
      title: 'Date of Joining',
      dataIndex: 'dateOfJoining',
      key: 'dateOfJoining',
      render: (date) => date || '—',
    },
  ];

  return (
    <Card variant="borderless" style={{ borderRadius: token.borderRadiusLG }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: token.marginLG }}>
        <Title level={4} style={{ margin: 0 }}>Employees</Title>
        {canCreate && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/employees/new')}
            id="btn-new-employee"
          >
            New Employee
          </Button>
        )}
      </div>

      <div style={{ display: 'flex', gap: token.margin, flexWrap: 'wrap', marginBottom: token.marginLG, alignItems: 'center' }}>
        <Input.Search
          placeholder="Search employees..."
          allowClear
          onSearch={(val) => {
            setSearchQuery(val);
            setPage(1);
          }}
          style={{ width: 260 }}
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
