import { useState, useEffect } from 'react';
import { Card, Table, Button, Modal, Form, Input, InputNumber, DatePicker, Select, Space, Typography, Tag, Row, Col, theme } from 'antd';
import { PlusOutlined, CalendarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { EmployeePicker } from '../leave/EmployeePicker.jsx';
import { wfhService } from './wfhService.js';
import { formatDate } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

export function WfhAllocations() {
  const { token } = theme.useToken();
  const canRead = useCan('core.wfh.read');
  const canManage = useCan('core.wfh_balance.manage');

  const [allocations, setAllocations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedYear, setSelectedYear] = useState(String(new Date().getFullYear()));
  const [modalVisible, setModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchAllocations = async (year) => {
    setLoading(true);
    try {
      const data = await wfhService.getAllocations({ year });
      const raw = data?.data !== undefined ? data.data : data;
      const list = Array.isArray(raw) ? raw : (raw?.content || raw?.items || []);
      setAllocations(list);
    } catch (err) {
      errorMsg('Error', err?.message || 'Failed to fetch WFH allocations');
      setAllocations([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (canRead) {
      fetchAllocations(selectedYear);
    }
  }, [canRead, selectedYear]);

  if (!canRead && !canManage) {
    return <NotEntitled action="core.wfh.read" />;
  }

  const handleCreate = async (values) => {
    setSubmitting(true);
    try {
      const payload = {
        employeeId: values.employeeId,
        wfhYear: values.wfhYear.trim(),
        yearStartDate: dayjs(values.yearStartDate).format('YYYY-MM-DD'),
        yearEndDate: dayjs(values.yearEndDate).format('YYYY-MM-DD'),
        entitlementDays: values.entitlementDays,
        proRateFactor: values.proRateFactor || 1.0,
      };

      await wfhService.createAllocation(payload);
      await successMsg('Allocation Saved', 'WFH quota has been allocated successfully.');
      setModalVisible(false);
      form.resetFields();
      fetchAllocations(selectedYear);
    } catch (err) {
      errorMsg('Allocation Failed', err?.response?.data?.message || err?.message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          <Text strong>{r.employeeName || 'Unknown'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.employeeNumber || '-'}</Text>
        </Space>
      ),
    },
    {
      title: 'Department',
      dataIndex: 'departmentName',
      key: 'departmentName',
      render: (d) => d || '-',
    },
    {
      title: 'Year',
      dataIndex: 'wfhYear',
      key: 'wfhYear',
      render: (y) => <Tag color="blue">{y}</Tag>,
    },
    {
      title: 'Validity Range',
      key: 'validity',
      render: (_, r) => `${formatDate(r.yearStartDate)} – ${formatDate(r.yearEndDate)}`,
    },
    {
      title: 'Entitlement',
      dataIndex: 'entitlementDays',
      key: 'entitlementDays',
      render: (d) => <Text strong style={{ color: token.colorPrimary }}>{d} days</Text>,
    },
    {
      title: 'Used',
      dataIndex: 'consumedDays',
      key: 'consumedDays',
      render: (d) => `${d ?? 0} d`,
    },
    {
      title: 'Balance',
      dataIndex: 'balanceDays',
      key: 'balanceDays',
      render: (d, r) => {
        const bal = d !== undefined && d !== null ? d : (Number(r.entitlementDays || 0) - Number(r.consumedDays || 0));
        return <Text strong style={{ color: Number(bal) > 0 ? token.colorSuccess : token.colorTextSecondary }}>{bal} d</Text>;
      },
    },
    {
      title: 'Pro-rate',
      dataIndex: 'proRateFactor',
      key: 'proRateFactor',
      render: (f) => `${(Number(f || 1) * 100).toFixed(0)}%`,
    },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d) => formatDate(d),
    },
  ];

  const currentYear = new Date().getFullYear();
  const yearOptions = [
    { label: String(currentYear - 1), value: String(currentYear - 1) },
    { label: String(currentYear), value: String(currentYear) },
    { label: String(currentYear + 1), value: String(currentYear + 1) },
  ];

  return (
    <Card style={{ margin: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>
            <CalendarOutlined style={{ marginRight: 8 }} />
            WFH Allocations
          </Title>
          <Text type="secondary">Manage employee yearly remote work entitlements and pro-rata quotas</Text>
        </Col>

        <Col>
          <Space>
            <Select
              value={selectedYear}
              onChange={setSelectedYear}
              options={yearOptions}
              style={{ width: 120 }}
              data-testid="wfh-year-filter"
            />
            {canManage && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setModalVisible(true)}
                data-testid="create-wfh-allocation-btn"
              >
                Allocate Quota
              </Button>
            )}
          </Space>
        </Col>
      </Row>

      <Table
        dataSource={allocations}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: 'No WFH allocations found for this year' }}
        data-testid="wfh-allocations-table"
      />

      <Modal
        title="Allocate Annual WFH Quota"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            wfhYear: String(currentYear),
            yearStartDate: dayjs(`${currentYear}-01-01`),
            yearEndDate: dayjs(`${currentYear}-12-31`),
            entitlementDays: 12,
            proRateFactor: 1.0,
          }}
          onFinish={handleCreate}
        >
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Please select an employee' }]}
          >
            <EmployeePicker
              id="wfh-allocation-employee-picker"
              placeholder="Search employee by name or code..."
            />
          </Form.Item>

          <Form.Item
            name="wfhYear"
            label="Allocation Year"
            rules={[{ required: true }]}
          >
            <Input placeholder="e.g. 2026" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="yearStartDate"
                label="Start Date"
                rules={[{ required: true }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="yearEndDate"
                label="End Date"
                rules={[{ required: true }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="entitlementDays"
                label="Quota (Days)"
                rules={[{ required: true }]}
              >
                <InputNumber min={0} max={365} step={1} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="proRateFactor"
                label="Pro-rate Factor"
              >
                <InputNumber min={0} max={1} step={0.05} precision={4} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setModalVisible(false)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Save Allocation
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
