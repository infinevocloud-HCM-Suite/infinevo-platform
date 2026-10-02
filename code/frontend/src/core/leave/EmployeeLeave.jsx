import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Table,
  Button,
  Space,
  Typography,
  Row,
  Col,
  Statistic,
  DatePicker,
  Tag,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { leaveConsumptionService } from './leaveConsumptionService.js';
import { employeeService } from '../employee/employeeService.js';

const { Title, Text } = Typography;

export function EmployeeLeave() {
  const { id } = useParams();
  const navigate = useNavigate();
  const canRead = useCan('core.leave.read');

  const [employee, setEmployee] = useState(null);
  const [selectedYear, setSelectedYear] = useState(dayjs().year());
  const [selectedMonth, setSelectedMonth] = useState(dayjs().format('YYYY-MM'));

  const [consumptionLoading, setConsumptionLoading] = useState(false);
  const [consumptionRows, setConsumptionRows] = useState([]);

  const [lopLoading, setLopLoading] = useState(false);
  const [lopData, setLopData] = useState(null);

  useEffect(() => {
    if (!id) return;
    employeeService
      .get(id)
      .then(setEmployee)
      .catch(() => {});
  }, [id]);

  const loadConsumption = useCallback(async () => {
    if (!id) return;
    setConsumptionLoading(true);
    try {
      const rows = await leaveConsumptionService.rows(id, selectedYear);
      setConsumptionRows(Array.isArray(rows) ? rows : []);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setConsumptionLoading(false);
    }
  }, [id, selectedYear]);

  const loadLop = useCallback(async () => {
    if (!id || !selectedMonth) return;
    setLopLoading(true);
    try {
      const data = await leaveConsumptionService.lop(id, selectedMonth);
      setLopData(data);
    } catch {
      setLopData(null);
    } finally {
      setLopLoading(false);
    }
  }, [id, selectedMonth]);

  useEffect(() => {
    if (canRead) {
      loadConsumption();
    }
  }, [canRead, loadConsumption]);

  useEffect(() => {
    if (canRead) {
      loadLop();
    }
  }, [canRead, loadLop]);

  if (!canRead) {
    return <NotEntitled />;
  }

  const consumptionColumns = [
    {
      title: 'Consumed On',
      dataIndex: 'consumedOn',
      key: 'consumedOn',
      render: (d) => d || '-',
    },
    {
      title: 'Period',
      dataIndex: 'period',
      key: 'period',
    },
    {
      title: 'Days',
      dataIndex: 'consumedDays',
      key: 'consumedDays',
      render: (days) => <Text strong>{days}</Text>,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      render: (r) => r || '-',
    },
    {
      title: 'Request ID',
      dataIndex: 'leaveRequestId',
      key: 'leaveRequestId',
      render: (reqId) =>
        reqId ? (
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/leave/requests/${reqId}`)}
          >
            {reqId.substring(0, 8)}...
          </Button>
        ) : (
          '-'
        ),
    },
    {
      title: 'Type',
      key: 'type',
      render: (_, r) =>
        r.reversesId ? <Tag color="warning">Reversal</Tag> : <Tag color="blue">Debit</Tag>,
    },
  ];

  const lopColumns = [
    { title: 'Period', dataIndex: 'period', key: 'period' },
    {
      title: 'LOP Days',
      dataIndex: 'lopDays',
      key: 'lopDays',
      render: (d) => <Text strong type="danger">{d}</Text>,
    },
    {
      title: 'Type',
      key: 'lopType',
      render: (_, r) =>
        r.reversesId ? <Tag color="orange">Reversal</Tag> : <Tag color="red">Deduction</Tag>,
    },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (dt) => (dt ? dayjs(dt).format('YYYY-MM-DD HH:mm') : '-'),
    },
  ];

  return (
    <Card style={{ margin: 24 }}>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <Row justify="space-between" align="middle">
          <Col>
            <Space align="center">
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/leave/requests')}>
                Back
              </Button>
              <Title level={4} style={{ margin: 0 }}>
                {employee
                  ? `Leave & Loss of Pay: ${employee.firstName || ''} ${employee.lastName || ''}`.trim()
                  : 'Employee Leave & LOP'}
              </Title>
              {employee && <Tag>{employee.employeeNumber}</Tag>}
            </Space>
          </Col>
        </Row>

        {/* Loss of Pay Section */}
        <Card
          type="inner"
          title="Monthly Loss of Pay (LOP)"
          extra={
            <DatePicker
              picker="month"
              value={dayjs(selectedMonth, 'YYYY-MM')}
              onChange={(d) => {
                if (d) setSelectedMonth(d.format('YYYY-MM'));
              }}
              format="YYYY-MM"
              allowClear={false}
            />
          }
        >
          <Row gutter={24} align="middle" style={{ marginBottom: 16 }}>
            <Col span={8}>
              <Statistic
                title={`Total LOP for ${selectedMonth}`}
                value={lopData?.totalLopDays ?? 0}
                precision={1}
                valueStyle={{ color: '#cf1322' }}
                suffix="day(s)"
              />
            </Col>
            <Col span={16}>
              <Text type="secondary">
                Loss of pay (LOP) is automatically counted when leave balance falls below limit or
                policy dictates MARK_AS_LOP. These figures are sent directly to payroll for
                salary deductions.
              </Text>
            </Col>
          </Row>

          <Table
            rowKey="id"
            columns={lopColumns}
            dataSource={lopData?.deltaRows || []}
            loading={lopLoading}
            pagination={false}
            size="small"
          />
        </Card>

        {/* Consumption Ledger Section */}
        <Card
          type="inner"
          title="Consumption Ledger"
          extra={
            <DatePicker
              picker="year"
              value={dayjs(String(selectedYear), 'YYYY')}
              onChange={(d) => {
                if (d) setSelectedYear(d.year());
              }}
              format="YYYY"
              allowClear={false}
            />
          }
        >
          <Table
            rowKey="id"
            columns={consumptionColumns}
            dataSource={consumptionRows}
            loading={consumptionLoading}
            pagination={{ pageSize: 10 }}
          />
        </Card>
      </Space>
    </Card>
  );
}
