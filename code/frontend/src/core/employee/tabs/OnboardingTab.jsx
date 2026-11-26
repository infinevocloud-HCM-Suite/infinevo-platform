import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Table,
  Tag,
  Button,
  Progress,
  Typography,
  Space,
  Popconfirm,
  Spin,
  Alert,
  Row,
  Col,
  Modal,
  Input,
  theme,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileDoneOutlined,
  ReloadOutlined,
  FileTextOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { onboardingService } from '../../onboarding/onboardingService.js';
import { documentService } from '../../document/documentService.js';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { formatDate } from '@shared/ui/format.js';

const { Text, Title, Paragraph } = Typography;

const STATUS_TAGS = {
  PENDING: { color: 'default', text: 'Pending' },
  SUBMITTED: { color: 'processing', text: 'Submitted' },
  COMPLETED: { color: 'success', text: 'Completed' },
  WAIVED: { color: 'warning', text: 'Waived' },
};

const TYPE_TAGS = {
  DOCUMENT_UPLOAD: { color: 'blue', text: 'Document' },
  PROFILE_UPDATE: { color: 'purple', text: 'Profile' },
  POLICY_ACKNOWLEDGEMENT: { color: 'cyan', text: 'Policy' },
  MANUAL_TASK: { color: 'gold', text: 'Task' },
};

export function OnboardingTab({ employeeId }) {
  const { token } = theme.useToken();
  const canManage = useCan('core.onboarding.manage');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [data, setData] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Reject modal state
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectTaskId, setRejectTaskId] = useState(null);
  const [rejectRemarks, setRejectRemarks] = useState('');
  const [rejectLoading, setRejectLoading] = useState(false);

  const loadData = useCallback(async () => {
    if (!employeeId) return;
    setLoading(true);
    setLoadError(null);
    try {
      const res = await onboardingService.getEmployeeProgress(employeeId);
      setData(res);
    } catch (err) {
      setLoadError(err?.message || 'Failed to load onboarding checklist');
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [employeeId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleAssignDefaults = async () => {
    setActionLoading(true);
    try {
      const res = await onboardingService.assignTasks(employeeId);
      setData(res);
      await successMsg('Tasks Assigned', 'Default onboarding checklist assigned to employee.');
    } catch (err) {
      errorMsg(err);
    } finally {
      setActionLoading(false);
    }
  };

  const handleUpdateStatus = async (taskId, nextStatus, remarks) => {
    try {
      const payload = { status: nextStatus };
      if (remarks) payload.remarks = remarks;
      await onboardingService.updateTask(employeeId, taskId, payload);
      await successMsg('Task Updated', `Task marked as ${nextStatus.toLowerCase()}.`);
      loadData();
    } catch (err) {
      errorMsg(err);
    }
  };

  const handleOpenReject = (taskId) => {
    setRejectTaskId(taskId);
    setRejectRemarks('');
    setRejectModalOpen(true);
  };

  const handleConfirmReject = async () => {
    if (!rejectRemarks.trim()) {
      return;
    }
    setRejectLoading(true);
    try {
      await onboardingService.updateTask(employeeId, rejectTaskId, {
        status: 'PENDING',
        remarks: rejectRemarks.trim(),
      });
      await successMsg('Document Rejected', 'Task sent back to pending with remarks.');
      setRejectModalOpen(false);
      loadData();
    } catch (err) {
      errorMsg(err);
    } finally {
      setRejectLoading(false);
    }
  };

  const handleViewDocument = async (documentId) => {
    try {
      const link = await documentService.link(documentId);
      if (link?.url) {
        window.open(link.url, '_blank');
      }
    } catch (err) {
      errorMsg(err);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (loadError) {
    return (
      <Alert
        message="Failed to load onboarding checklist"
        description={loadError}
        type="error"
        showIcon
        action={
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            Retry
          </Button>
        }
      />
    );
  }

  const tasks = data?.tasks || [];
  const total = data?.totalTasks || 0;
  const completed = data?.completedTasks || 0;
  const percent = data?.progressPercentage ?? (total === 0 ? 100 : Math.round((completed / total) * 100));

  const columns = [
    {
      title: 'Task',
      key: 'task',
      render: (_, record) => (
        <div>
          <Text strong>{record.title}</Text>
          {record.isMandatory && (
            <Tag color="red" style={{ marginLeft: 8 }}>
              Mandatory
            </Tag>
          )}
          {record.description && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {record.description}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'taskType',
      key: 'taskType',
      width: 120,
      render: (type) => {
        const conf = TYPE_TAGS[type] || { color: 'default', text: type };
        return <Tag color={conf.color}>{conf.text}</Tag>;
      },
    },
    {
      title: 'Assigned Role',
      dataIndex: 'assignedRole',
      key: 'assignedRole',
      width: 110,
      render: (role) => <Tag>{role || 'EMPLOYEE'}</Tag>,
    },
    {
      title: 'Due Date',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 130,
      render: (date) => (date ? formatDate(date) : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status) => {
        const conf = STATUS_TAGS[status] || { color: 'default', text: status };
        return <Tag color={conf.color}>{conf.text}</Tag>;
      },
    },
    {
      title: 'Document / Remarks',
      key: 'details',
      render: (_, record) => (
        <div>
          {record.documentId && (
            <Button
              type="link"
              size="small"
              icon={<FileTextOutlined />}
              onClick={() => handleViewDocument(record.documentId)}
              style={{ padding: 0, height: 'auto', display: 'block', marginBottom: 4 }}
            >
              View document
            </Button>
          )}
          {record.remarks && (
            <Text type="secondary" italic style={{ fontSize: 12, display: 'block' }}>
              {record.remarks}
            </Text>
          )}
        </div>
      ),
    },
    ...(canManage
      ? [
          {
            title: 'Actions',
            key: 'actions',
            width: 200,
            render: (_, record) => {
              if (record.status === 'COMPLETED' || record.status === 'WAIVED') {
                return (
                  <Popconfirm
                    title="Re-open this task?"
                    okText="Reopen"
                    onConfirm={() => handleUpdateStatus(record.id, 'PENDING')}
                  >
                    <Button size="small" type="link">
                      Reopen
                    </Button>
                  </Popconfirm>
                );
              }
              if (record.status === 'SUBMITTED') {
                return (
                  <Space size="small" wrap>
                    <Button
                      size="small"
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      onClick={() => handleUpdateStatus(record.id, 'COMPLETED')}
                    >
                      Approve
                    </Button>
                    <Button
                      size="small"
                      danger
                      icon={<CloseCircleOutlined />}
                      onClick={() => handleOpenReject(record.id)}
                    >
                      Reject
                    </Button>
                    <Popconfirm
                      title="Waive this onboarding requirement?"
                      okText="Waive"
                      onConfirm={() => handleUpdateStatus(record.id, 'WAIVED')}
                    >
                      <Button size="small" icon={<StopOutlined />}>
                        Waive
                      </Button>
                    </Popconfirm>
                  </Space>
                );
              }
              // PENDING
              return (
                <Space size="small">
                  {record.taskType !== 'DOCUMENT_UPLOAD' && (
                    <Button
                      size="small"
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      onClick={() => handleUpdateStatus(record.id, 'COMPLETED')}
                    >
                      Complete
                    </Button>
                  )}
                  <Popconfirm
                    title="Waive this onboarding requirement?"
                    okText="Waive"
                    onConfirm={() => handleUpdateStatus(record.id, 'WAIVED')}
                  >
                    <Button size="small" danger icon={<CloseCircleOutlined />}>
                      Waive
                    </Button>
                  </Popconfirm>
                </Space>
              );
            },
          },
        ]
      : []),
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Header Summary Card */}
      <Card variant="borderless">
        <Row gutter={[24, 16]} align="middle">
          <Col xs={24} md={16}>
            <Title level={4} style={{ margin: 0 }}>
              Onboarding Progress
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 12px 0' }}>
              Track onboarding checklist completion, verify submitted documents, and manage requirement waivers.
            </Paragraph>
            <Space size="middle" align="center" style={{ width: '100%', maxWidth: 500 }}>
              <Progress
                percent={percent}
                status={percent === 100 ? 'success' : 'active'}
                strokeColor={percent === 100 ? token.colorSuccess : token.colorPrimary}
                style={{ flex: 1 }}
              />
              <Text strong>
                {completed} of {total} tasks completed
              </Text>
            </Space>
          </Col>
          <Col xs={24} md={8} style={{ textAlign: 'right' }}>
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={loadData}>
                Refresh
              </Button>
              {canManage && total === 0 && (
                <Button
                  type="primary"
                  icon={<FileDoneOutlined />}
                  loading={actionLoading}
                  onClick={handleAssignDefaults}
                >
                  Assign Default Checklist
                </Button>
              )}
            </Space>
          </Col>
        </Row>
      </Card>

      {total === 0 ? (
        <Alert
          message="No Onboarding Tasks Assigned"
          description="This employee currently has no onboarding tasks assigned. You can assign the default checklist using the button above."
          type="info"
          showIcon
        />
      ) : (
        <Table
          rowKey="id"
          dataSource={tasks}
          columns={columns}
          pagination={false}
        />
      )}

      {/* Reject Remarks Modal */}
      <Modal
        title="Reject Submitted Document"
        open={rejectModalOpen}
        onOk={handleConfirmReject}
        onCancel={() => setRejectModalOpen(false)}
        confirmLoading={rejectLoading}
        okText="Reject Document"
        okButtonProps={{ danger: true, disabled: !rejectRemarks.trim() }}
      >
        <Paragraph type="secondary">
          Please provide remarks explaining why the submitted document was rejected. The task will be reset to pending for the employee to resubmit.
        </Paragraph>
        <Input.TextArea
          rows={3}
          value={rejectRemarks}
          onChange={(e) => setRejectRemarks(e.target.value)}
          placeholder="e.g. Document copy is blurred, please upload a clearer scan."
        />
      </Modal>
    </div>
  );
}

OnboardingTab.propTypes = {
  employeeId: PropTypes.string.isRequired,
};
