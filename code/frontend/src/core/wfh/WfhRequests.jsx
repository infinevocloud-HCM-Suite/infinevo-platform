import { useState, useEffect } from 'react';
import { Card, Table, Tabs, Button, Modal, Form, Input, DatePicker, Switch, Radio, Tag, Space, Typography, theme } from 'antd';
import { SolutionOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { wfhService } from './wfhService.js';
import { formatDate } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { RangePicker } = DatePicker;

export function WfhRequests() {
  const { token } = theme.useToken();
  const canRead = useCan('core.wfh.read');
  const canReadTeam = useCan('core.wfh.read_team');
  const canManage = useCan('core.wfh.manage');

  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('ALL');
  const [modalVisible, setModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [isHalfDay, setIsHalfDay] = useState(false);
  const [form] = Form.useForm();

  const fetchRequests = async (status) => {
    setLoading(true);
    try {
      const filterStatus = status === 'ALL' ? null : status;
      const data = await wfhService.getAllRequests(filterStatus);
      setRequests(Array.isArray(data) ? data : []);
    } catch (err) {
      errorMsg('Error', err?.message || 'Failed to fetch WFH requests');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (canRead || canReadTeam) {
      fetchRequests(activeTab);
    }
  }, [canRead, canReadTeam, activeTab]);

  if (!canRead && !canReadTeam) {
    return <NotEntitled action="core.wfh.read" />;
  }

  const handleApplyOnBehalf = async (values) => {
    setSubmitting(true);
    try {
      let fromDate;
      let toDate;

      if (values.isHalfDay) {
        const d = values.singleDate ? dayjs(values.singleDate).format('YYYY-MM-DD') : null;
        fromDate = d;
        toDate = d;
      } else {
        fromDate = values.dateRange?.[0] ? dayjs(values.dateRange[0]).format('YYYY-MM-DD') : null;
        toDate = values.dateRange?.[1] ? dayjs(values.dateRange[1]).format('YYYY-MM-DD') : null;
      }

      const payload = {
        employeeId: values.employeeId,
        fromDate,
        toDate,
        halfDay: !!values.isHalfDay,
        halfDayPeriod: values.isHalfDay ? values.halfDayPeriod || 'first' : null,
        reason: values.reason ? values.reason.trim() : '',
      };

      await wfhService.applyOnBehalf(payload);
      await successMsg('On-Behalf Request Submitted', 'WFH application has been recorded.');
      setModalVisible(false);
      form.resetFields();
      fetchRequests(activeTab);
    } catch (err) {
      errorMsg('Submission Failed', err?.response?.data?.message || err?.message);
    } finally {
      setSubmitting(false);
    }
  };

  const getStatusTag = (status) => {
    switch (status) {
      case 'APPROVED':
        return <Tag color="success">Approved</Tag>;
      case 'PENDING':
        return <Tag color="processing">Pending Approval</Tag>;
      case 'REJECTED':
        return <Tag color="error">Rejected</Tag>;
      case 'WITHDRAWN':
        return <Tag color="default">Withdrawn</Tag>;
      case 'CANCELLED':
        return <Tag color="warning">Cancelled</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) => (
        <Space orientation="vertical" size={2}>
          <Text strong>{r.employeeName || 'Unknown'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.employeeNumber}</Text>
        </Space>
      ),
    },
    {
      title: 'Dates',
      key: 'dates',
      render: (_, r) => (
        <Space orientation="vertical" size={2}>
          <Text>
            {formatDate(r.fromDate)}
            {r.fromDate !== r.toDate && ` – ${formatDate(r.toDate)}`}
          </Text>
          {r.halfDay && (
            <Tag color="purple" style={{ fontSize: 11 }}>
              Half Day ({r.halfDayPeriod === 'first' ? '1st Half' : '2nd Half'})
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Days',
      dataIndex: 'workingDays',
      key: 'workingDays',
      render: (d) => `${d} d`,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => getStatusTag(s),
    },
    {
      title: 'Type',
      dataIndex: 'onBehalf',
      key: 'onBehalf',
      render: (ob) => (ob ? <Tag color="orange">On Behalf</Tag> : <Tag color="cyan">Self-Service</Tag>),
    },
    {
      title: 'Submitted On',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d) => formatDate(d),
    },
  ];

  const tabItems = [
    { key: 'ALL', label: 'All Requests' },
    { key: 'PENDING', label: 'Pending Approval' },
    { key: 'APPROVED', label: 'Approved' },
    { key: 'REJECTED', label: 'Rejected' },
    { key: 'CANCELLED', label: 'Cancelled' },
  ];

  return (
    <div style={{ padding: token.paddingLG }}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Title level={3} style={{ margin: 0 }}>
              <SolutionOutlined style={{ marginRight: 8 }} />
              WFH Applications
            </Title>
            <Text type="secondary">Review tenant-wide and team remote work requests</Text>
          </div>

          {canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setModalVisible(true)}
              data-testid="apply-on-behalf-btn"
            >
              Apply on Behalf
            </Button>
          )}
        </div>

        <Card style={{ borderRadius: token.borderRadiusLG }}>
          <Tabs
            activeKey={activeTab}
            onChange={(key) => setActiveTab(key)}
            items={tabItems}
          />
          <Table
            dataSource={requests}
            columns={columns}
            rowKey="id"
            loading={loading}
            pagination={{ pageSize: 10 }}
            data-testid="all-wfh-requests-table"
          />
        </Card>
      </Space>

      <Modal
        title="Apply for WFH on Behalf of Employee"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ isHalfDay: false, halfDayPeriod: 'first' }}
          onFinish={handleApplyOnBehalf}
        >
          <Form.Item
            name="employeeId"
            label="Employee UUID"
            rules={[{ required: true, message: 'Please enter employee UUID' }]}
          >
            <Input placeholder="Enter employee UUID..." />
          </Form.Item>

          <Form.Item
            name="isHalfDay"
            label="Half Day Application"
            valuePropName="checked"
          >
            <Switch
              checkedChildren="Half Day"
              unCheckedChildren="Full Day"
              onChange={(checked) => {
                setIsHalfDay(checked);
                form.setFieldsValue({ singleDate: null, dateRange: null });
              }}
            />
          </Form.Item>

          {isHalfDay ? (
            <>
              <Form.Item
                name="singleDate"
                label="Date"
                rules={[{ required: true }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="halfDayPeriod"
                label="Period"
                rules={[{ required: true }]}
              >
                <Radio.Group>
                  <Radio value="first">1st Half</Radio>
                  <Radio value="second">2nd Half</Radio>
                </Radio.Group>
              </Form.Item>
            </>
          ) : (
            <Form.Item
              name="dateRange"
              label="Date Range"
              rules={[{ required: true }]}
            >
              <RangePicker style={{ width: '100%' }} />
            </Form.Item>
          )}

          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, message: 'Please provide reason' }]}
          >
            <TextArea rows={3} placeholder="Reason for remote work..." />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setModalVisible(false)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Submit on Behalf
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
