import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Card,
  Descriptions,
  Timeline,
  Tag,
  Button,
  Space,
  Typography,
  Modal,
  Input,
  Row,
  Col,
  Spin,
} from 'antd';
import { ArrowLeftOutlined, CloseCircleOutlined, StopOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';
import { approvalService } from '../approvals/approvalService.js';

const { Title, Text } = Typography;
const { TextArea } = Input;

const STATUS_TAGS = {
  SUBMITTED: { color: 'blue', label: 'Submitted' },
  PENDING: { color: 'gold', label: 'Pending Approval' },
  APPROVED: { color: 'green', label: 'Approved' },
  REJECTED: { color: 'red', label: 'Rejected' },
  WITHDRAWN: { color: 'default', label: 'Withdrawn' },
  CANCELLED: { color: 'purple', label: 'Cancelled' },
};

export function LeaveRequestDetail({ readOnly = false, backPath = null }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const canManage = useCan('core.leave.manage');

  const [loading, setLoading] = useState(false);
  const [request, setRequest] = useState(null);
  const [employee, setEmployee] = useState(null);
  const [leaveType, setLeaveType] = useState(null);
  const [approvalHistory, setApprovalHistory] = useState([]);

  // Modal for Reason (Withdraw / Cancel)
  const [actionType, setActionType] = useState(null); // 'withdraw' | 'cancel'
  const [reasonModalOpen, setReasonModalOpen] = useState(false);
  const [reasonText, setReasonText] = useState('');
  const [submittingAction, setSubmittingAction] = useState(false);

  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const req = await leaveRequestService.get(id);
      setRequest(req);

      // Concurrently load employee, leave type and approval history if applicable
      const promises = [];

      if (req.employeeId) {
        promises.push(
          employeeService
            .get(req.employeeId)
            .then(setEmployee)
            .catch(() => {})
        );
      }

      if (req.leaveTypeId) {
        promises.push(
          leaveTypeService
            .get(req.leaveTypeId)
            .then(setLeaveType)
            .catch(() => {})
        );
      }

      if (req.approvalInstanceId) {
        promises.push(
          approvalService
            .history(req.approvalInstanceId)
            .then((data) => setApprovalHistory(Array.isArray(data) ? data : []))
            .catch(() => {})
        );
      }

      await Promise.all(promises);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  const handleOpenActionModal = (type) => {
    setActionType(type);
    setReasonText('');
    setReasonModalOpen(true);
  };

  const handleActionSubmit = async () => {
    setSubmittingAction(true);
    try {
      if (actionType === 'withdraw') {
        await leaveRequestService.withdraw(id, reasonText);
        await successMsg('Leave request withdrawn');
      } else if (actionType === 'cancel') {
        await leaveRequestService.cancel(id, reasonText);
        await successMsg('Approved leave cancelled');
      }
      setReasonModalOpen(false);
      loadDetail();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSubmittingAction(false);
    }
  };

  if (loading && !request) {
    return (
      <Card style={{ margin: 24, textAlign: 'center', padding: 50 }}>
        <Spin />
      </Card>
    );
  }

  if (!request) {
    return (
      <Card style={{ margin: 24 }}>
        <Text type="secondary">Leave request not found.</Text>
      </Card>
    );
  }

  const statusConfig = STATUS_TAGS[request.status] || { color: 'default', label: request.status };
  const isPortal = location.pathname.startsWith('/me');
  const isReadOnly = readOnly || isPortal;
  const effectiveBackPath = backPath || (isPortal ? '/me/leave' : '/leave/requests');

  const canWithdraw = !isReadOnly && canManage && (request.status === 'PENDING' || request.status === 'SUBMITTED');
  const canCancel = !isReadOnly && canManage && request.status === 'APPROVED';

  return (
    <Card style={{ margin: 24, maxWidth: 900 }}>
      <Space direction="vertical" orientation="vertical" orientationMargin={0} style={{ width: '100%' }} size="large">
        <Row justify="space-between" align="middle">
          <Col>
            <Space align="center">
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(effectiveBackPath)}>
                Back
              </Button>
              <Title level={4} style={{ margin: 0 }}>
                Leave Request Details
              </Title>
              <Tag color={statusConfig.color}>{statusConfig.label}</Tag>
            </Space>
          </Col>
          <Col>
            <Space>
              {canWithdraw && (
                <Button
                  danger
                  icon={<StopOutlined />}
                  onClick={() => handleOpenActionModal('withdraw')}
                >
                  Withdraw
                </Button>
              )}
              {canCancel && (
                <Button
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => handleOpenActionModal('cancel')}
                >
                  Cancel Leave
                </Button>
              )}
            </Space>
          </Col>
        </Row>

        <Descriptions bordered column={{ xs: 1, sm: 2, md: 2 }}>
          <Descriptions.Item label="Employee">
            {employee
              ? `${employee.firstName || ''} ${employee.lastName || ''} (${employee.employeeNumber || employee.id})`.trim()
              : request.employeeId}
          </Descriptions.Item>
          <Descriptions.Item label="Leave Type">
            {leaveType ? `${leaveType.name} (${leaveType.code})` : request.leaveTypeId}
          </Descriptions.Item>
          <Descriptions.Item label="From Date">{request.fromDate}</Descriptions.Item>
          <Descriptions.Item label="To Date">{request.toDate}</Descriptions.Item>
          <Descriptions.Item label="Duration">
            <Space>
              <Text strong>{request.workingDays} working day(s)</Text>
              {request.isHalfDay && (
                <Tag color="cyan">
                  {request.halfDayPeriod === 'FIRST' ? '1st Half' : '2nd Half'}
                </Tag>
              )}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Type">
            {request.onBehalf ? (
              <Tag color="blue">Recorded on Behalf (Admin)</Tag>
            ) : (
              <Tag>Self-Service Apply</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Reason" span={2}>
            {request.reason || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Created At">
            {request.createdAt ? dayjs(request.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Decided At">
            {request.decidedAt ? dayjs(request.decidedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
        </Descriptions>

        <Card type="inner" title="Approval Trail">
          {request.onBehalf ? (
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
              items={approvalHistory.map((step) => ({
                color:
                  step.status === 'APPROVED'
                    ? 'green'
                    : step.status === 'REJECTED'
                    ? 'red'
                    : 'blue',
                children: (
                  <>
                    <Text strong>{`${step.stepName || 'Approval Step'} - ${step.status}`}</Text>
                    <br />
                    <Text type="secondary">
                      {step.decidedBy ? `Decided by ${step.decidedBy}` : 'Pending assignment'}
                      {step.decidedAt ? ` at ${dayjs(step.decidedAt).format('YYYY-MM-DD HH:mm')}` : ''}
                    </Text>
                    {step.comment && (
                      <p style={{ margin: '4px 0 0 0', fontStyle: 'italic' }}>
                        &ldquo;{step.comment}&rdquo;
                      </p>
                    )}
                  </>
                ),
              }))}
            />
          ) : (
            <Text type="secondary">No approval steps recorded.</Text>
          )}
        </Card>
      </Space>

      {/* Reason Modal for Withdraw / Cancel */}
      <Modal
        title={actionType === 'withdraw' ? 'Withdraw Leave Request' : 'Cancel Approved Leave'}
        open={reasonModalOpen}
        onCancel={() => setReasonModalOpen(false)}
        onOk={handleActionSubmit}
        confirmLoading={submittingAction}
        okButtonProps={{ danger: true }}
        okText={actionType === 'withdraw' ? 'Withdraw' : 'Confirm Cancellation'}
      >
        <p>
          {actionType === 'withdraw'
            ? 'Are you sure you want to withdraw this pending leave request?'
            : 'Are you sure you want to cancel this approved leave? Consumed days will be restored and any loss of pay reversed.'}
        </p>
        <TextArea
          rows={3}
          placeholder="Reason for this action (optional)"
          value={reasonText}
          onChange={(e) => setReasonText(e.target.value)}
        />
      </Modal>
    </Card>
  );
}

LeaveRequestDetail.propTypes = {
  readOnly: PropTypes.bool,
  backPath: PropTypes.string,
};
