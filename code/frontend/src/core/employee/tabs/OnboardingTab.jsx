import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Table, Tag, Button, Progress, Typography, Space, Popconfirm, Spin, Alert, Row, Col, theme } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileDoneOutlined,
  ReloadOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { onboardingService } from '../../onboarding/onboardingService.js';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { formatDate } from '@shared/ui/format.js';

const { Text, Title } = Typography;

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
  const [data, setData] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadData = useCallback(async () => {
    if (!employeeId) return;
    setLoading(true);
    try {
      const res = await onboardingService.getEmployeeProgress(employeeId);
      setData(res);
    } catch (err) {
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

  const handleUpdateStatus = async (taskId, nextStatus) => {
    try {
      await onboardingService.updateTask(employeeId, taskId, { status: nextStatus });
      await successMsg('Task Updated', `Task marked as ${nextStatus.toLowerCase()}.`);
      loadData();
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
            <Tag icon={<FileTextOutlined />} color="blue">
              Document attached
            </Tag>
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
            width: 160,
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
              return (
                <Space size="small">
                  <Button
                    size="small"
                    type="primary"
                    icon={<CheckCircleOutlined />}
                    onClick={() => handleUpdateStatus(record.id, 'COMPLETED')}
                  >
                    Complete
                  </Button>
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
      {/* Progress Card */}
      <Card variant="borderless" style={{ background: token.colorFillAlter }}>
        <Row gutter={[24, 16]} align="middle">
          <Col xs={24} md={16}>
            <Title level={5} style={{ margin: 0 }}>
              Onboarding Checklist Progress
            </Title>
            <Text type="secondary">
              {completed} of {total} tasks completed ({percent}%)
            </Text>
            <div style={{ marginTop: 8 }}>
              <Progress
                percent={percent}
                status={percent === 100 ? 'success' : 'active'}
                strokeColor={percent === 100 ? token.colorSuccess : token.colorPrimary}
              />
            </div>
          </Col>
          <Col xs={24} md={8} style={{ textAlign: 'right' }}>
            <Space>
              <Button icon={<ReloadOutlined />} onClick={loadData}>
                Refresh
              </Button>
              {canManage && total === 0 && (
                <Button
                  type="primary"
                  icon={<FileDoneOutlined />}
                  loading={actionLoading}
                  onClick={handleAssignDefaults}
                  id="btn-assign-onboarding"
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
          description={
            canManage
              ? 'This employee has no onboarding tasks assigned. Click "Assign Default Checklist" to assign standard templates.'
              : 'No onboarding tasks have been assigned to this employee yet.'
          }
          type="info"
          showIcon
        />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={tasks}
          pagination={false}
          size="middle"
        />
      )}
    </div>
  );
}

OnboardingTab.propTypes = {
  employeeId: PropTypes.string.isRequired,
};
