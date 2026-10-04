import PropTypes from 'prop-types';
import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Descriptions,
  Table,
  Timeline,
  Tag,
  Button,
  Space,
  Typography,
  Modal,
  Form,
  Select,
  Input,
  theme,
} from 'antd';
import { ArrowLeftOutlined, SwapOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { approvalService } from './approvalService.js';
import { useEmployeeSearch } from './useEmployeeSearch.js';
import { getItemRoute } from './itemRoutes.js';

const { Title } = Typography;

const STATUS_TAGS = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
};

export function InstanceDetail({ instanceId: propInstanceId } = {}) {
  const params = useParams();
  const instanceId = propInstanceId || params?.instanceId;
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const canManage = useCan('core.approval.manage');

  const [loading, setLoading] = useState(false);
  const [instance, setInstance] = useState(null);
  const [historyEvents, setHistoryEvents] = useState([]);

  // Reassign modal state
  const [reassignOpen, setReassignOpen] = useState(false);
  const [reassignForm] = Form.useForm();
  const [reassigning, setReassigning] = useState(false);

  // Employee picker for reassign
  const {
    options: employeeOptions,
    loading: searchLoading,
    error: searchError,
    search: searchEmployees,
  } = useEmployeeSearch();

  const loadData = useCallback(async () => {
    if (!instanceId) return;
    setLoading(true);

    try {
      const inst = await approvalService.get(instanceId);
      setInstance(inst);
    } catch (err) {
      await errorMsg(err);
    }

    try {
      const hist = await approvalService.history(instanceId);
      setHistoryEvents(hist?.steps || hist?.events || (Array.isArray(hist) ? hist : []));
    } catch {
      setHistoryEvents([]);
    } finally {
      setLoading(false);
    }
  }, [instanceId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleReassign = async () => {
    try {
      const values = await reassignForm.validateFields();
      setReassigning(true);
      await approvalService.reassign(instanceId, values);
      setReassignOpen(false);
      reassignForm.resetFields();
      await successMsg('Step Reassigned', 'Approval step has been reassigned.');
      loadData();
    } catch (err) {
      if (err?.errorFields) return;
      await errorMsg(err);
    } finally {
      setReassigning(false);
    }
  };

  const stepsColumns = [
    {
      title: 'Step',
      dataIndex: 'stepIndex',
      key: 'stepIndex',
      width: 80,
      render: (index) => `Step ${index + 1}`,
    },
    {
      title: 'Approver Kind',
      dataIndex: 'approverKind',
      key: 'approverKind',
      width: 180,
    },
    {
      title: 'Assignee Employee ID',
      dataIndex: 'assigneeEmployeeId',
      key: 'assigneeEmployeeId',
      render: (id) => id || '—',
    },
    {
      title: 'Decision',
      dataIndex: 'decision',
      key: 'decision',
      width: 120,
      render: (decision) =>
        decision ? (
          <Tag color={STATUS_TAGS[decision] || 'default'}>{decision}</Tag>
        ) : (
          <Tag color="gold">PENDING</Tag>
        ),
    },
    {
      title: 'Comment',
      dataIndex: 'comment',
      key: 'comment',
      render: (comment) => comment || '—',
    },
    {
      title: 'Approved Amount',
      dataIndex: 'approvedAmount',
      key: 'approvedAmount',
      width: 150,
      render: (amt) => (amt !== null && amt !== undefined ? `INR ${amt}` : '—'),
    },
    {
      title: 'Decided At',
      dataIndex: 'decidedAt',
      key: 'decidedAt',
      width: 180,
      render: (date) => (date ? new Date(date).toLocaleString() : '—'),
    },
  ];

  return (
    <Card style={{ margin: token.marginLG }} loading={loading}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: token.marginLG,
        }}
      >
        <Space size="middle">
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/approvals')}
            id="btn-back-to-inbox"
          >
            Inbox
          </Button>
          <Title level={3} style={{ margin: 0 }}>
            Approval Instance Details
          </Title>
        </Space>

        {canManage && instance?.status === 'PENDING' && (
          <Button
            icon={<SwapOutlined />}
            onClick={() => setReassignOpen(true)}
            id="btn-open-reassign"
          >
            Reassign Step
          </Button>
        )}
      </div>

      {instance && (
        <>
          <Descriptions
            bordered
            size="small"
            column={{ xs: 1, sm: 2, md: 3 }}
            style={{ marginBottom: token.marginLG }}
          >
            <Descriptions.Item label="Instance ID">{instance.id}</Descriptions.Item>
            <Descriptions.Item label="Flow Type">
              <Tag color="blue">{instance.flowType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              <Tag color={STATUS_TAGS[instance.status] || 'default'}>
                {instance.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Subject Employee ID">
              {instance.subjectEmployeeId || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Subject Reference">
              {instance.subjectTable ? (
                getItemRoute(instance.flowType, instance.subjectId) ? (
                  <Button
                    type="link"
                    id="btn-view-subject-item"
                    style={{ padding: 0, height: 'auto' }}
                    onClick={() => navigate(getItemRoute(instance.flowType, instance.subjectId), { state: { from: '/approvals' } })}
                  >
                    {instance.subjectTable} #{instance.subjectId}
                  </Button>
                ) : (
                  `${instance.subjectTable} #${instance.subjectId}`
                )
              ) : (
                '—'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Started At">
              {instance.startedAt ? new Date(instance.startedAt).toLocaleString() : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Completed At">
              {instance.completedAt ? new Date(instance.completedAt).toLocaleString() : '—'}
            </Descriptions.Item>
          </Descriptions>

          <Title level={4} style={{ marginTop: token.marginLG, marginBottom: 12 }}>
            Approval Steps
          </Title>
          <Table
            dataSource={instance.steps || []}
            columns={stepsColumns}
            rowKey="id"
            pagination={false}
            style={{ marginBottom: token.marginLG }}
          />

          <Title level={4} style={{ marginTop: token.marginLG, marginBottom: 16 }}>
            History Trail
          </Title>
          {historyEvents.length > 0 ? (
            <Timeline
              items={historyEvents.map((event, idx) => {
                const label =
                  event.decision ||
                  (event.reassignedFromEmployeeId
                    ? 'REASSIGNED'
                    : event.eventType ||
                      `Step ${event.stepIndex !== undefined ? event.stepIndex + 1 : idx + 1}`);
                const reason = event.comment || event.reassignReason || event.reason || '';
                const time = event.decidedAt || event.createdAt || event.occurredAt;
                const color =
                  label === 'APPROVED'
                    ? 'green'
                    : label === 'REJECTED'
                    ? 'red'
                    : label === 'REASSIGNED'
                    ? 'orange'
                    : 'blue';
                return {
                  color,
                  children: (
                    <div>
                      <strong>{label}</strong>
                      {reason ? ` — ${reason}` : ''}
                      <div style={{ color: '#888', fontSize: 12 }}>
                        {time ? new Date(time).toLocaleString() : ''}
                        {event.reassignedFromEmployeeId || event.fromEmployeeId
                          ? ` • From: ${event.reassignedFromEmployeeId || event.fromEmployeeId}`
                          : ''}
                        {event.assigneeEmployeeId || event.toEmployeeId
                          ? ` • Assignee: ${event.assigneeEmployeeId || event.toEmployeeId}`
                          : ''}
                        {event.delegatedFromEmployeeId
                          ? ` • Delegated from: ${event.delegatedFromEmployeeId}`
                          : ''}
                        {event.escalatedFromEmployeeId
                          ? ` • Escalated from: ${event.escalatedFromEmployeeId}`
                          : ''}
                      </div>
                    </div>
                  ),
                };
              })}
            />
          ) : (
            <Typography.Text type="secondary">No history events recorded.</Typography.Text>
          )}
        </>
      )}

      <Modal
        title="Reassign Step"
        open={reassignOpen}
        onOk={handleReassign}
        onCancel={() => setReassignOpen(false)}
        confirmLoading={reassigning}
        okText="Reassign"
      >
        <Form form={reassignForm} layout="vertical">
          <Form.Item
            name="employeeId"
            label="New Assignee Employee"
            rules={[{ required: true, message: 'Please select an employee.' }]}
          >
            <Select
              id="input-reassign-employee"
              showSearch
              placeholder="Search employee by name or ID"
              filterOption={false}
              loading={searchLoading}
              onSearch={searchEmployees}
              onFocus={() => {
                if (employeeOptions.length === 0) searchEmployees('');
              }}
              options={employeeOptions}
              notFoundContent={searchError || undefined}
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, message: 'Please state the reason for reassignment.' }]}
          >
            <Input.TextArea id="input-reassign-reason" rows={3} placeholder="Reason for reassignment..." />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

InstanceDetail.propTypes = {
  instanceId: PropTypes.string,
};
