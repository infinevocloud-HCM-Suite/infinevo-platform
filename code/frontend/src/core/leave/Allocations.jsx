import { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Button,
  Space,
  Typography,
  Card,
  Modal,
  Form,
  Select,
  InputNumber,
  DatePicker,
  Row,
  Col,
  Input,
} from 'antd';
import { PlusOutlined, SyncOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { leaveTypeService } from './leaveTypeService.js';
import { leaveBalanceService } from './leaveBalanceService.js';
import { employeeService } from '../employee/employeeService.js';
import { companyProfileService } from '../settings/companyProfileService.js';
import { EMPTY, formatDate } from '@shared/ui/format.js';
import { EmployeePicker } from './EmployeePicker.jsx';

/** Employees per page of the allocations table; balances are read for this page only (D-80). */
export const ALLOCATIONS_PAGE_SIZE = 15;

const { Title, Text } = Typography;

/** The server's default for core.tenant.leave_year_start_month (V033): April. */
const DEFAULT_LEAVE_YEAR_START_MONTH = 4;

/** The leave-year window for the year it starts in: Jan-Dec when the tenant's year starts in January, Apr-Mar for April. */
export function leaveYearWindow(year, startMonth) {
  const start = dayjs(new Date(year, startMonth - 1, 1));
  const end = start.add(1, 'year').subtract(1, 'day');
  return { yearStartDate: start.format('YYYY-MM-DD'), yearEndDate: end.format('YYYY-MM-DD') };
}

function leaveYearHint(year, startMonth, fromTenant) {
  if (!year) return null;
  const { yearStartDate, yearEndDate } = leaveYearWindow(Number(year), startMonth);
  const window = `Leave year ${dayjs(yearStartDate).format('D MMM YYYY')} to ${dayjs(yearEndDate).format('D MMM YYYY')}`;
  return fromTenant ? window : `${window} (company settings could not be read; assuming the leave year starts in April)`;
}

export function Allocations() {
  const canManage = useCan('core.leave_balance.manage');
  const [selectedYear, setSelectedYear] = useState(dayjs().year());
  // D-93: the tenant's leave year may start in April; the window sent with an allocation follows it.
  // Until the company profile answers, assume April - the server's own default (core.tenant, V033).
  const [leaveYearStartMonth, setLeaveYearStartMonth] = useState(DEFAULT_LEAVE_YEAR_START_MONTH);
  const [leaveYearFromTenant, setLeaveYearFromTenant] = useState(false);
  const [loading, setLoading] = useState(false);

  // D-80: the table pages employees on the server and searches them there; it used to ask for 1,000
  // and get the first 100, so everyone past them could not be seen or allocated.
  const [employees, setEmployees] = useState([]);
  const [employeePage, setEmployeePage] = useState({ current: 1, pageSize: ALLOCATIONS_PAGE_SIZE, total: 0 });
  const [employeeQuery, setEmployeeQuery] = useState('');
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [balancesByEmployee, setBalancesByEmployee] = useState({});

  // Allocate Modal
  const [allocateModalOpen, setAllocateModalOpen] = useState(false);
  const [allocateForm] = Form.useForm();
  const typedLeaveYear = Form.useWatch('leaveYear', allocateForm);
  const [allocating, setAllocating] = useState(false);

  // Accrual Modal
  const [accrualModalOpen, setAccrualModalOpen] = useState(false);
  const [accrualAsOf, setAccrualAsOf] = useState(dayjs());
  const [accruing, setAccruing] = useState(false);

  useEffect(() => {
    let live = true;
    Promise.all([
      leaveTypeService.list().catch(() => []),
      companyProfileService.get().catch(() => null),
    ]).then(([typeData, profile]) => {
      if (!live) return;
      const month = Number(profile?.leaveYearStartMonth);
      const known = month >= 1 && month <= 12;
      setLeaveYearStartMonth(known ? month : DEFAULT_LEAVE_YEAR_START_MONTH);
      setLeaveYearFromTenant(known);
      setLeaveTypes(Array.isArray(typeData) ? typeData : []);
    });
    return () => {
      live = false;
    };
  }, []);

  const loadData = useCallback(async (page = 1, pageSize = ALLOCATIONS_PAGE_SIZE) => {
    setLoading(true);
    try {
      const empData = await employeeService.list({
        q: employeeQuery || undefined,
        page: page - 1,
        size: pageSize,
      });
      const empList = Array.isArray(empData)
        ? empData
        : empData?.content || [];
      setEmployees(empList);
      setEmployeePage({ current: page, pageSize, total: empData?.totalElements ?? empList.length });

      // Balances for this page's employees only, for the year
      const asOf = `${selectedYear}-12-31`;
      const balanceEntries = await Promise.all(
        empList.map(async (emp) => {
          try {
            const b = await leaveBalanceService.forEmployee(emp.id, asOf);
            return [emp.id, Array.isArray(b) ? b : []];
          } catch {
            return [emp.id, []];
          }
        })
      );
      setBalancesByEmployee(Object.fromEntries(balanceEntries));
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [selectedYear, employeeQuery]);

  useEffect(() => {
    loadData(1, ALLOCATIONS_PAGE_SIZE);
  }, [loadData]);

  const reloadPage = () => loadData(employeePage.current, employeePage.pageSize);

  const handleOpenAllocate = () => {
    allocateForm.resetFields();
    allocateForm.setFieldsValue({
      leaveYear: selectedYear,
      openingDays: 0,
    });
    setAllocateModalOpen(true);
  };

  const handleAllocateSubmit = async (values) => {
    setAllocating(true);
    try {
      const payload = {
        employeeId: values.employeeId,
        leaveTypeId: values.leaveTypeId,
        leaveYear: String(values.leaveYear),
        ...leaveYearWindow(Number(values.leaveYear), leaveYearStartMonth),
        openingDays: Number(values.openingDays),
      };
      await leaveBalanceService.allocate(payload);
      await successMsg('Leave allocated successfully');
      setAllocateModalOpen(false);
      reloadPage();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setAllocating(false);
    }
  };

  const handleAccrueSubmit = async () => {
    setAccruing(true);
    try {
      const asOfStr = accrualAsOf ? accrualAsOf.format('YYYY-MM-DD') : null;
      const res = await leaveBalanceService.accrue(asOfStr);
      await successMsg(`Accrual completed. Rows updated: ${res?.accruedCount ?? 0}`);
      setAccrualModalOpen(false);
      reloadPage();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setAccruing(false);
    }
  };

  if (!canManage) {
    return <NotEntitled />;
  }

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      fixed: 'left',
      width: 220,
      render: (_, emp) => (
        <Space direction="vertical" size={0}>
          <Text strong>{`${emp.firstName || ''} ${emp.lastName || ''}`.trim() || EMPTY}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {emp.employeeNumber || EMPTY}
          </Text>
        </Space>
      ),
    },
    ...leaveTypes.map((type) => ({
      title: type.name,
      key: `type_${type.id}`,
      width: 130,
      render: (_, emp) => {
        const balances = balancesByEmployee[emp.id] || [];
        const record = balances.find((b) => b.leaveTypeId === type.id);
        if (!record) return '-';
        return <Text strong>{record.remainingDays ?? record.entitlementDays ?? 0}</Text>;
      },
    })),
  ];

  const expandedRowRender = (emp) => {
    const balances = balancesByEmployee[emp.id] || [];
    if (balances.length === 0) {
      return <Text type="secondary">No allocations recorded for this year.</Text>;
    }

    const detailColumns = [
      { title: 'Leave Type', dataIndex: 'leaveTypeName', key: 'leaveTypeName' },
      { title: 'Entitlement', dataIndex: 'entitlementDays', key: 'entitlementDays' },
      { title: 'Accrued', dataIndex: 'accruedDays', key: 'accruedDays' },
      { title: 'Carried Forward', dataIndex: 'carriedForwardDays', key: 'carriedForwardDays' },
      { title: 'Consumed', dataIndex: 'consumedDays', key: 'consumedDays' },
      {
        title: 'Remaining',
        dataIndex: 'remainingDays',
        key: 'remainingDays',
        render: (days) => <Text strong>{days}</Text>,
      },
      {
        title: 'Carry Forward Expires',
        dataIndex: 'carryForwardExpiresOn',
        key: 'carryForwardExpiresOn',
        render: (d) => formatDate(d),
      },
    ];

    return (
      <Table
        rowKey="leaveTypeId"
        columns={detailColumns}
        dataSource={balances}
        pagination={false}
        size="small"
      />
    );
  };

  return (
    <Card style={{ margin: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Space align="center" size="middle">
            <Title level={4} style={{ margin: 0 }}>
              Leave Allocations
            </Title>
            <Select
              value={selectedYear}
              onChange={setSelectedYear}
              style={{ width: 120 }}
              options={[
                { value: selectedYear - 1, label: String(selectedYear - 1) },
                { value: selectedYear, label: String(selectedYear) },
                { value: selectedYear + 1, label: String(selectedYear + 1) },
              ]}
            />
          </Space>
        </Col>
        <Col>
          <Space>
            <Button
              icon={<SyncOutlined />}
              onClick={() => setAccrualModalOpen(true)}
            >
              Run Accrual As Of
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenAllocate}
            >
              Allocate
            </Button>
          </Space>
        </Col>
      </Row>

      <Input.Search
        allowClear
        placeholder="Search employee by name or number"
        aria-label="Search employees"
        style={{ maxWidth: 360, marginBottom: 16 }}
        onSearch={(q) => setEmployeeQuery((q || '').trim())}
      />

      <Table
        rowKey="id"
        columns={columns}
        dataSource={employees}
        loading={loading}
        expandable={{ expandedRowRender }}
        pagination={{ ...employeePage, showSizeChanger: false }}
        onChange={(p) => loadData(p.current, p.pageSize)}
        scroll={{ x: 'max-content' }}
      />

      {/* Manual Allocate Modal */}
      <Modal
        title="Manual Leave Allocation"
        open={allocateModalOpen}
        onCancel={() => setAllocateModalOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={allocateForm}
          layout="vertical"
          onFinish={handleAllocateSubmit}
        >
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Please select an employee' }]}
          >
            <EmployeePicker placeholder="Search employee" />
          </Form.Item>

          <Form.Item
            name="leaveTypeId"
            label="Leave Type"
            rules={[{ required: true, message: 'Please select a leave type' }]}
          >
            <Select
              placeholder="Select leave type"
              options={leaveTypes.map((t) => ({
                value: t.id,
                label: `${t.name} (${t.code})`,
              }))}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="leaveYear"
                label="Leave Year"
                extra={leaveYearHint(typedLeaveYear ?? selectedYear, leaveYearStartMonth, leaveYearFromTenant)}
                rules={[{ required: true, message: 'Please enter year' }]}
              >
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="openingDays"
                label="Opening Days"
                rules={[{ required: true, message: 'Please enter opening days' }]}
              >
                <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginTop: 16, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setAllocateModalOpen(false)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={allocating}>
                Save Allocation
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Accrual Confirmation Modal */}
      <Modal
        title="Run Accrual As Of"
        open={accrualModalOpen}
        onCancel={() => setAccrualModalOpen(false)}
        onOk={handleAccrueSubmit}
        confirmLoading={accruing}
        okText="Run Accrual"
      >
        <p>
          Running accrual will compute and credit earned leave days up to the selected date
          for all eligible employees according to each leave type&apos;s accrual policy.
        </p>
        <Form.Item label="Accrual As Of Date">
          <DatePicker
            value={accrualAsOf}
            onChange={(d) => setAccrualAsOf(d)}
            style={{ width: '100%' }}
            format="YYYY-MM-DD"
          />
        </Form.Item>
      </Modal>
    </Card>
  );
}
