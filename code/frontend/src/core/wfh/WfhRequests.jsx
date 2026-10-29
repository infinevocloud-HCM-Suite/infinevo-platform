import { useState, useEffect } from 'react';
import { Card, Table, Tabs, Button, Modal, Form, Input, DatePicker, Switch, Radio, Tag, Space, Typography, Descriptions, Row, Col, Timeline, Spin } from 'antd';
import { SolutionOutlined, PlusOutlined, EyeOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { EmployeePicker } from '../leave/EmployeePicker.jsx';
import { wfhService } from './wfhService.js';
import { approvalService } from '../approvals/approvalService.js';
import { formatDate, humanize } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { RangePicker } = DatePicker;

const DECISIONS = {
  APPROVED: { label: 'Approved', color: 'green' },
  REJECTED: { label: 'Rejected', color: 'red' },
  PENDING: { label: 'Pending Approval', color: 'blue' },
};
const PENDING_DECISION = { label: 'Pending Approval', color: 'blue' };

export function WfhRequests() {
  const canRead = useCan('core.wfh.read');
  const canReadTeam = useCan('core.wfh.read_team');
  const canManage = useCan('core.wfh.manage');

  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('ALL');
  const [modalVisible, setModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [isHalfDay, setIsHalfDay] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [viewModalVisible, setViewModalVisible] = useState(false);
  const [approvalHistory, setApprovalHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [form] = Form.useForm();

  const handleOpenViewModal = (record) => {
    setSelectedRequest(record);
    setViewModalVisible(true);
    setApprovalHistory([]);
    if (record.approvalInstanceId && !record.onBehalf) {
      setHistoryLoading(true);
      approvalService
        .history(record.approvalInstanceId)
        .then((res) => {
          const steps = res?.steps || (Array.isArray(res) ? res : []);
          setApprovalHistory(steps);
        })
        .catch(() => setApprovalHistory([]))
        .finally(() => setHistoryLoading(false));
    }
  };

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
        return <Tag>{status ? humanize(status) : '—'}</Tag>;
    }
  };

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          <Text strong>{r.employeeName || 'Unknown'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.employeeNumber}</Text>
        </Space>
      ),
    },
    {
      title: 'Dates',
      key: 'dates',
      render: (_, r) => (
        <Space direction="vertical" size={2}>
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
    {
      title: 'Actions',
      key: 'actions',
      render: (_, r) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleOpenViewModal(r)}
          data-testid={`view-wfh-request-${r.id}`}
        >
          View
        </Button>
      ),
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
    <Card style={{ margin: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>
            <SolutionOutlined style={{ marginRight: 8 }} />
            WFH Applications
          </Title>
          <Text type="secondary">Review tenant-wide and team remote work requests</Text>
        </Col>

        <Col>
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
        </Col>
      </Row>

      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key)}
        items={tabItems}
        style={{ marginBottom: 16 }}
      />

      <Table
        dataSource={requests}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
        data-testid="all-wfh-requests-table"
      />

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
            label="Employee"
            rules={[{ required: true, message: 'Please select an employee' }]}
          >
            <EmployeePicker
              id="wfh-on-behalf-employee-picker"
              placeholder="Search employee by name or code..."
            />
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

      <Modal
        title="WFH Application Details"
        open={viewModalVisible}
        onCancel={() => setViewModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setViewModalVisible(false)}>
            Close
          </Button>,
        ]}
        width={600}
      >
        {selectedRequest && (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Descriptions column={1} bordered size="small" style={{ marginTop: 12 }}>
              <Descriptions.Item label="Employee">
                {selectedRequest.employeeName
                  ? `[${selectedRequest.employeeNumber || '—'}] ${selectedRequest.employeeName}`
                  : (selectedRequest.employeeId || '—')}
              </Descriptions.Item>
              <Descriptions.Item label="Dates">
                {formatDate(selectedRequest.fromDate)}
                {selectedRequest.fromDate !== selectedRequest.toDate && ` – ${formatDate(selectedRequest.toDate)}`}
              </Descriptions.Item>
              <Descriptions.Item label="Duration">
                {selectedRequest.halfDay
                  ? `0.5 day (${selectedRequest.halfDayPeriod === 'first' ? '1st Half' : '2nd Half'})`
                  : `${selectedRequest.workingDays || 1} day(s)`}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                {getStatusTag(selectedRequest.status)}
              </Descriptions.Item>
              <Descriptions.Item label="Reason">
                {selectedRequest.reason || 'No reason provided'}
              </Descriptions.Item>
              <Descriptions.Item label="Submission Type">
                {selectedRequest.onBehalf ? 'On-Behalf (Manager/HR)' : 'Self-Service'}
              </Descriptions.Item>
              <Descriptions.Item label="Applied On">
                {formatDate(selectedRequest.createdAt)}
              </Descriptions.Item>
            </Descriptions>

            <Card size="small" title="Approval Trail">
              {historyLoading ? (
                <div style={{ textAlign: 'center', padding: 16 }}>
                  <Spin size="small" />
                </div>
              ) : selectedRequest.onBehalf ? (
                <Timeline
                  items={[
                    {
                      color: 'green',
                      children: (
                        <>
                          <Text strong>Approved on Creation</Text>
                          <br />
                          <Text type="secondary">
                            Recorded directly by administrator on behalf of employee
                          </Text>
                        </>
                      ),
                    },
                  ]}
                />
              ) : approvalHistory.length > 0 ? (
                <Timeline
                  items={approvalHistory.map((step) => {
                    const decision = DECISIONS[step.decision] || PENDING_DECISION;
                    const by = step.assigneeName
                      ? `by ${step.assigneeName}`
                      : (step.role ? `by ${humanize(step.role)}` : null);
                    const when = step.decidedAt ? dayjs(step.decidedAt).format('D MMM YYYY, HH:mm') : null;
                    return {
                      key: step.stepId || step.id,
                      color: decision.color,
                      children: (
                        <>
                          <Text strong>{`Step ${step.stepIndex + 1} — ${decision.label}`}</Text>
                          <br />
                          <Text type="secondary">
                            {[by, when].filter(Boolean).join(' · ') || 'Awaiting decision'}
                          </Text>
                          {step.comment && (
                            <p style={{ margin: '4px 0 0 0', fontStyle: 'italic' }}>
                              &ldquo;{step.comment}&rdquo;
                            </p>
                          )}
                        </>
                      ),
                    };
                  })}
                />
              ) : (
                <Text type="secondary">No approval steps recorded.</Text>
              )}
            </Card>
          </Space>
        )}
      </Modal>
    </Card>
  );
}
