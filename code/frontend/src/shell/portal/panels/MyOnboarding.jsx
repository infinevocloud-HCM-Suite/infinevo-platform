import { useCallback, useEffect, useState } from 'react';
import { Card, Progress, Typography, Button, Tag, Space, Spin, Alert, List, Popconfirm, Upload, theme } from 'antd';
import {
  CheckCircleOutlined,
  CheckOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  FileDoneOutlined,
  ReloadOutlined,
  UserOutlined,
  UploadOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { onboardingService } from '../../../core/onboarding/onboardingService.js';
import {
  documentService,
  MAX_DOCUMENT_BYTES,
  ACCEPTED_DOCUMENT_TYPES,
} from '../../../core/document/documentService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { formatDate } from '@shared/ui/format.js';

const { Title, Text, Paragraph } = Typography;

function renderTypeIcon(type, token) {
  switch (type) {
    case 'DOCUMENT_UPLOAD':
      return <UploadOutlined style={{ color: token.colorPrimary }} />;
    case 'PROFILE_UPDATE':
      return <UserOutlined style={{ color: token.colorWarning }} />;
    case 'POLICY_ACKNOWLEDGEMENT':
      return <FileDoneOutlined style={{ color: token.colorInfo }} />;
    case 'MANUAL_TASK':
    default:
      return <CheckCircleOutlined style={{ color: token.colorSuccess }} />;
  }
}

export function MyOnboarding() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [data, setData] = useState(null);
  const [actionTaskId, setActionTaskId] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await onboardingService.getMyProgress();
      setData(res);
    } catch (err) {
      setLoadError(err?.message || 'Failed to load onboarding checklist');
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleComplete = async (task) => {
    setActionTaskId(task.id);
    try {
      await onboardingService.updateTask(task.employeeId, task.id, {
        status: 'COMPLETED',
        remarks: 'Completed by employee via self-service portal',
      });
      await successMsg('Task Completed', `"${task.title}" marked as completed.`);
      loadData();
    } catch (err) {
      errorMsg(err);
    } finally {
      setActionTaskId(null);
    }
  };

  const handleFileUpload = async (task, file) => {
    if (!file) return;
    if (file.size > MAX_DOCUMENT_BYTES) {
      await errorMsg(new Error('File exceeds maximum allowable size (10 MB).'));
      return;
    }
    setActionTaskId(task.id);
    try {
      const doc = await documentService.uploadForEmployee(
        file,
        task.employeeId,
        task.documentLabel || 'OTHER'
      );
      await onboardingService.updateTask(task.employeeId, task.id, {
        status: 'SUBMITTED',
        documentId: doc.id,
        remarks: 'Document submitted for verification',
      });
      await successMsg('Document Submitted', `"${task.title}" submitted for HR verification.`);
      loadData();
    } catch (err) {
      errorMsg(err);
    } finally {
      setActionTaskId(null);
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Hero Welcome Card */}
      <Card
        variant="borderless"
        style={{
          background: `linear-gradient(135deg, ${token.colorInfoBg} 0%, ${token.colorBgContainer} 100%)`,
          border: `1px solid ${token.colorInfoBorder}`,
          borderRadius: 12,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={4} style={{ margin: 0, color: token.colorPrimary }}>
              Welcome! Let&apos;s get you onboarded
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
              Please complete all checklist items below. Mandatory tasks must be completed before your due dates.
            </Paragraph>
          </div>
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            Refresh
          </Button>
        </div>

        <div style={{ marginTop: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <Text strong>
              {completed} of {total} completed
            </Text>
            <Text type="secondary">{percent}%</Text>
          </div>
          <Progress
            percent={percent}
            status={percent === 100 ? 'success' : 'active'}
            strokeColor={percent === 100 ? token.colorSuccess : token.colorPrimary}
          />
        </div>
      </Card>

      {total === 0 ? (
        <Alert
          message="No Onboarding Tasks"
          description="You currently have no onboarding tasks assigned. Check back later or contact your HR administrator."
          type="info"
          showIcon
        />
      ) : (
        <List
          grid={{ gutter: 16, xs: 1, sm: 1, md: 1, lg: 1, xl: 1, xxl: 1 }}
          dataSource={tasks}
          renderItem={(item) => {
            const isDone = item.status === 'COMPLETED' || item.status === 'WAIVED';
            const isSubmitted = item.status === 'SUBMITTED';
            const isOverdue =
              !isDone && item.dueDate && dayjs(item.dueDate).isBefore(dayjs(), 'day');
            const isAssignedToOther = item.assignedRole && item.assignedRole !== 'EMPLOYEE';

            return (
              <List.Item>
                <Card
                  variant="borderless"
                  style={{
                    borderRadius: 10,
                    border: isDone
                      ? `1px solid ${token.colorSuccessBorder}`
                      : isOverdue
                        ? `1px solid ${token.colorErrorBorder}`
                        : `1px solid ${token.colorBorderSecondary}`,
                    background: isDone ? token.colorSuccessBg : token.colorBgContainer,
                    transition: 'all 0.2s',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
                    <Space align="start" size="middle">
                      <div style={{ fontSize: 24, marginTop: 2 }}>
                        {renderTypeIcon(item.taskType, token)}
                      </div>
                      <div>
                        <Space wrap>
                          <Text strong style={{ fontSize: 15, textDecoration: isDone ? 'line-through' : 'none' }}>
                            {item.title}
                          </Text>
                          {item.isMandatory && <Tag color="red">Mandatory</Tag>}
                          {isDone && <Tag color="success">Completed</Tag>}
                          {isSubmitted && <Tag color="processing">In Review</Tag>}
                          {isOverdue && (
                            <Tag icon={<ExclamationCircleOutlined />} color="error">
                              Overdue
                            </Tag>
                          )}
                          {isAssignedToOther && (
                            <Tag color="default">Assigned to {item.assignedRole}</Tag>
                          )}
                        </Space>
                        {item.description && (
                          <Paragraph type="secondary" style={{ margin: '4px 0 0 0', fontSize: 13 }}>
                            {item.description}
                          </Paragraph>
                        )}
                        <Space size="middle" wrap style={{ marginTop: 6, fontSize: 12 }}>
                          {item.dueDate && (
                            <Text type={isOverdue ? 'danger' : 'secondary'}>
                              <ClockCircleOutlined style={{ marginRight: 4 }} />
                              Due: {formatDate(item.dueDate)}
                            </Text>
                          )}
                          {item.documentId && (
                            <Button
                              type="link"
                              size="small"
                              icon={<FileTextOutlined />}
                              onClick={() => handleViewDocument(item.documentId)}
                              style={{ padding: 0, height: 'auto', fontSize: 12 }}
                            >
                              View attached document
                            </Button>
                          )}
                        </Space>
                      </div>
                    </Space>

                    <div>
                      {isDone ? (
                        <Tag color="green" icon={<CheckOutlined />} style={{ padding: '4px 10px', fontSize: 13 }}>
                          Done
                        </Tag>
                      ) : isSubmitted ? (
                        <Tag color="blue" style={{ padding: '4px 10px', fontSize: 13 }}>
                          Submitted for Verification
                        </Tag>
                      ) : isAssignedToOther ? (
                        <Tag color="default" style={{ padding: '4px 10px', fontSize: 13 }}>
                          Read-only ({item.assignedRole})
                        </Tag>
                      ) : (
                        <Space>
                          {item.taskType === 'PROFILE_UPDATE' && (
                            <>
                              <Button size="middle" onClick={() => navigate('/me/profile')}>
                                Go to Profile
                              </Button>
                              <Button
                                type="primary"
                                icon={<CheckOutlined />}
                                loading={actionTaskId === item.id}
                                onClick={() => handleComplete(item)}
                              >
                                Mark as Done
                              </Button>
                            </>
                          )}
                          {item.taskType === 'DOCUMENT_UPLOAD' && (
                            <Upload
                              beforeUpload={(file) => {
                                handleFileUpload(item, file);
                                return false;
                              }}
                              showUploadList={false}
                              accept={ACCEPTED_DOCUMENT_TYPES}
                            >
                              <Button
                                type="primary"
                                icon={<UploadOutlined />}
                                loading={actionTaskId === item.id}
                              >
                                Submit Document
                              </Button>
                            </Upload>
                          )}
                          {item.taskType === 'POLICY_ACKNOWLEDGEMENT' && (
                            <Popconfirm
                              title="Acknowledge Policy"
                              description="I confirm I have read and agree to this policy."
                              onConfirm={() => handleComplete(item)}
                              okText="I Agree"
                            >
                              <Button
                                type="primary"
                                icon={<CheckOutlined />}
                                loading={actionTaskId === item.id}
                              >
                                Acknowledge
                              </Button>
                            </Popconfirm>
                          )}
                          {item.taskType === 'MANUAL_TASK' && (
                            <Button
                              type="primary"
                              icon={<CheckOutlined />}
                              loading={actionTaskId === item.id}
                              onClick={() => handleComplete(item)}
                            >
                              Mark as Done
                            </Button>
                          )}
                        </Space>
                      )}
                    </div>
                  </div>
                </Card>
              </List.Item>
            );
          }}
        />
      )}
    </div>
  );
}
