import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table,
  Button,
  Space,
  Typography,
  Card,
  Tag,
  Select,
  DatePicker,
  Row,
  Col,
} from 'antd';
import { PlusOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

const STATUS_TAGS = {
  SUBMITTED: { color: 'blue', label: 'Submitted' },
  PENDING: { color: 'gold', label: 'Pending Approval' },
  APPROVED: { color: 'green', label: 'Approved' },
  REJECTED: { color: 'red', label: 'Rejected' },
  WITHDRAWN: { color: 'default', label: 'Withdrawn' },
  CANCELLED: { color: 'purple', label: 'Cancelled' },
};

export function LeaveRequests() {
  const navigate = useNavigate();
  const canRead = useCan('core.leave.read');
  const canManage = useCan('core.leave.manage');

  const [loading, setLoading] = useState(false);
  const [requests, setRequests] = useState([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 15, total: 0 });

  // Filters
  const [employeeFilter, setEmployeeFilter] = useState(null);
  const [statusFilter, setStatusFilter] = useState(null);
  const [dateRange, setDateRange] = useState(null);

  // Lookups
  const [employees, setEmployees] = useState([]);
  const [employeeMap, setEmployeeMap] = useState({});
  const [leaveTypeMap, setLeaveTypeMap] = useState({});

  useEffect(() => {
    employeeService
      .list(true)
      .then((data) => {
        const list = Array.isArray(data) ? data : data?.content || [];
        setEmployees(list);
        const map = {};
        list.forEach((e) => {
          map[e.id] = `${e.firstName || ''} ${e.lastName || ''}`.trim() || e.employeeNumber;
        });
        setEmployeeMap(map);
      })
      .catch(() => {});

    leaveTypeService
      .list()
      .then((data) => {
        const list = Array.isArray(data) ? data : [];
        const map = {};
        list.forEach((t) => {
          map[t.id] = `${t.name} (${t.code})`;
        });
        setLeaveTypeMap(map);
      })
      .catch(() => {});
  }, []);

  const loadRequests = useCallback(
    async (page = 1, pageSize = 15) => {
      setLoading(true);
      try {
        const params = {
          page: page - 1,
          size: pageSize,
        };
        if (employeeFilter) params.employeeId = employeeFilter;
        if (statusFilter) params.status = statusFilter;
        if (dateRange && dateRange[0] && dateRange[1]) {
          params.from = dateRange[0].format('YYYY-MM-DD');
          params.to = dateRange[1].format('YYYY-MM-DD');
        }

        const data = await leaveRequestService.list(params);
        const content = Array.isArray(data) ? data : data?.content || [];
        const total = data?.totalElements ?? content.length;

        setRequests(content);
        setPagination({ current: page, pageSize, total });
      } catch (err) {
        await errorMsg(err);
      } finally {
        setLoading(false);
      }
    },
    [employeeFilter, statusFilter, dateRange]
  );

  useEffect(() => {
    if (canRead) {
      loadRequests(1, pagination.pageSize);
    }
  }, [canRead, loadRequests, pagination.pageSize]);

  const handleTableChange = (newPagination) => {
    loadRequests(newPagination.current, newPagination.pageSize);
  };

  if (!canRead) {
    return <NotEntitled />;
  }

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, record) => (
        <Text strong>{employeeMap[record.employeeId] || record.employeeId}</Text>
      ),
    },
    {
      title: 'Leave Type',
      key: 'leaveType',
      render: (_, record) => leaveTypeMap[record.leaveTypeId] || record.leaveTypeId,
    },
    {
      title: 'Dates',
      key: 'dates',
      render: (_, record) => {
        const isSameDay = record.fromDate === record.toDate;
        const dateStr = isSameDay
          ? record.fromDate
          : `${record.fromDate} to ${record.toDate}`;
        return (
          <Space>
            <span>{dateStr}</span>
            {record.isHalfDay && (
              <Tag color="cyan">
                {record.halfDayPeriod === 'FIRST' ? '1st Half' : '2nd Half'}
              </Tag>
            )}
          </Space>
        );
      },
    },
    {
      title: 'Days',
      dataIndex: 'workingDays',
      key: 'workingDays',
      render: (days) => <Text strong>{days}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        const conf = STATUS_TAGS[status] || { color: 'default', label: status };
        return <Tag color={conf.color}>{conf.label}</Tag>;
      },
    },
    {
      title: 'Submitted On',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (dt) => (dt ? dayjs(dt).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => navigate(`/leave/requests/${record.id}`)}
        >
          View
        </Button>
      ),
    },
  ];

  return (
    <Card style={{ margin: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>
            Leave Requests
          </Title>
        </Col>
        <Col>
          {canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/leave/requests/new')}
            >
              Record Leave
            </Button>
          )}
        </Col>
      </Row>

      {/* Filter Bar */}
      <Card size="small" style={{ marginBottom: 16, background: '#fafafa' }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Select
              allowClear
              showSearch
              placeholder="Filter by employee"
              style={{ width: '100%' }}
              optionFilterProp="label"
              value={employeeFilter}
              onChange={setEmployeeFilter}
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.firstName || ''} ${e.lastName || ''}`.trim() || e.employeeNumber,
              }))}
            />
          </Col>
          <Col xs={24} sm={8} md={6}>
            <Select
              allowClear
              placeholder="Filter by status"
              style={{ width: '100%' }}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'PENDING', label: 'Pending Approval' },
                { value: 'APPROVED', label: 'Approved' },
                { value: 'REJECTED', label: 'Rejected' },
                { value: 'WITHDRAWN', label: 'Withdrawn' },
                { value: 'CANCELLED', label: 'Cancelled' },
              ]}
            />
          </Col>
          <Col xs={24} sm={8} md={8}>
            <RangePicker
              style={{ width: '100%' }}
              value={dateRange}
              onChange={setDateRange}
              format="YYYY-MM-DD"
            />
          </Col>
          <Col xs={24} sm={24} md={4} style={{ textAlign: 'right' }}>
            <Button
              type="primary"
              icon={<SearchOutlined />}
              onClick={() => loadRequests(1, pagination.pageSize)}
            >
              Filter
            </Button>
          </Col>
        </Row>
      </Card>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={requests}
        loading={loading}
        pagination={pagination}
        onChange={handleTableChange}
      />
    </Card>
  );
}
