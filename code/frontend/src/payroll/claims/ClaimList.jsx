import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Col, DatePicker, Result, Row, Select, Space, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { claimService } from './claimService.js';
import { EmployeeSelect } from './EmployeeSelect.jsx';
import { CLAIM_STATUS_OPTIONS, claimStatus, formatAmount, formatDate } from './claimLabels.js';
import { readError } from '../tax/apiError.js';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

/** `/payroll/claims` (W-47.4 §5): the officer's claim list. No mock rows on failure (DEBT-031). */
export function ClaimList() {
  const navigate = useNavigate();
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [employeeId, setEmployeeId] = useState(undefined);
  const [status, setStatus] = useState(undefined);
  const [range, setRange] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await claimService.list({
        employeeId,
        status,
        from: range?.[0] ? range[0].format('YYYY-MM-DD') : undefined,
        to: range?.[1] ? range[1].format('YYYY-MM-DD') : undefined,
        page: page - 1,
        size: pageSize,
      });
      setRows(data?.content ?? []);
      setTotal(data?.totalElements ?? 0);
    } catch (err) {
      setRows([]);
      setTotal(0);
      setError(readError(err, 'Could not load the claims').message);
    } finally {
      setLoading(false);
    }
  }, [employeeId, status, range, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const columns = [
    { title: 'Employee', dataIndex: 'employee_name', key: 'employee_name', render: (v) => v || '-' },
    { title: 'Component', dataIndex: 'component_name', key: 'component_name', render: (v) => v || '-' },
    { title: 'Bill date', dataIndex: 'bill_date', key: 'bill_date', render: formatDate },
    {
      title: 'Requested',
      dataIndex: 'requested_amount',
      key: 'requested_amount',
      align: 'right',
      render: formatAmount,
    },
    {
      title: 'Approved',
      dataIndex: 'approved_amount',
      key: 'approved_amount',
      align: 'right',
      render: formatAmount,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => {
        const tag = claimStatus(s);
        return <Tag color={tag.color}>{tag.label}</Tag>;
      },
    },
    { title: 'Posted period', dataIndex: 'posted_period', key: 'posted_period', render: (v) => v || '-' },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          Reimbursement claims
        </Title>
        <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
          Refresh
        </Button>
      </Space>

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Text type="secondary">Employee</Text>
            <EmployeeSelect
              ariaLabel="Employee filter"
              value={employeeId}
              onChange={(v) => {
                setEmployeeId(v);
                setPage(1);
              }}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={24} md={6}>
            <Text type="secondary">Status</Text>
            <Select
              aria-label="Status filter"
              allowClear
              placeholder="All statuses"
              options={CLAIM_STATUS_OPTIONS}
              value={status}
              onChange={(v) => {
                setStatus(v);
                setPage(1);
              }}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={24} md={10}>
            <Text type="secondary">Bill date</Text>
            <RangePicker
              value={range}
              format="YYYY-MM-DD"
              onChange={(v) => {
                setRange(v);
                setPage(1);
              }}
              style={{ width: '100%' }}
            />
          </Col>
        </Row>
      </Card>

      {error ? (
        <Result
          status="error"
          title="Could not load the claims"
          subTitle={error}
          extra={
            <Button type="primary" onClick={load}>
              Retry
            </Button>
          }
        />
      ) : (
        <Table
          rowKey="id"
          dataSource={rows}
          columns={columns}
          loading={loading}
          onRow={(record) => ({
            onClick: () => navigate(`/payroll/claims/${record.id}`),
            style: { cursor: 'pointer' },
          })}
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
        />
      )}
    </div>
  );
}
