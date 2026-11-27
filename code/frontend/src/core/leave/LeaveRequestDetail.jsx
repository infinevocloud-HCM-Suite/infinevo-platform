import { PageHeader } from '@shared/ui/PageHeader.jsx';
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
  Spin,
  Form,
  Select,
} from 'antd';
import { CloseCircleOutlined, StopOutlined, SwapOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';
import { approvalService } from '../approvals/approvalService.js';
import { useEmployeeSearch } from '../approvals/useEmployeeSearch.js';
import { portalService } from '@shell/portal/portalService.js';
import { formatDate } from '@shared/ui/format.js';

const { Text } = Typography;
const { TextArea } = Input;

const STATUS_TAGS = {
  DRAFT: { color: 'default', label: 'Draft' },
  SUBMITTED: { color: 'blue', label: 'Submitted' },
  PENDING: { color: 'gold', label: 'Pending Approval' },
  APPROVED: { color: 'green', label: 'Approved' },
  REJECTED: { color: 'red', label: 'Rejected' },
  WITHDRAWN: { color: 'default', label: 'Withdrawn' },
  CANCELLED: { color: 'purple', label: 'Cancelled' },
};

/*
 * D-71: an approval trail step is an ApprovalHistoryStepResponse - stepIndex is 0-based, decision
 * is ApprovalDecision (APPROVED | REJECTED) or null while the step waits, assigneeName may be null.
 */
const DECISIONS = {
  APPROVED: { color: 'green', label: 'Approved' },
  REJECTED: { color: 'red', label: 'Rejected' },
};
const PENDING_DECISION = { color: 'blue', label: 'Pending' };

export function LeaveRequestDetail({ readOnly = false, backPath = null }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const canManage = useCan('core.leave.manage');
  // D-72: a manager reaches this screen from /approvals (core.leave.read_team) and may not hold
  // the Leave requests menu, so the way back is the inbox they came from.
  const canReadAll = useCan('core.leave.read');
  // Founder 2026-11-27: an admin opening a pending leave here can hand its waiting step to another
  // approver (e.g. back to the manager after an escalation), the same reassign as the Approvals page.
  const canReassign = useCan('core.approval.manage');
  const [reassignOpen, setReassignOpen] = useState(false);
  const [reassigning, setReassigning] = useState(false);
  const [reassignForm] = Form.useForm();
  const {
    options: employeeOptions,
    loading: searchLoading,
    error: searchError,
    search: searchEmployees,
  } = useEmployeeSearch();

  const [loading, setLoading] = useState(false);
  const [request, setRequest] = useState(null);
  const [employee, setEmployee] = useState(null);
  const [leaveType, setLeaveType] = useState(null);
  const [approvalHistory, setApprovalHistory] = useState([]);
  const [currentEmployeeId, setCurrentEmployeeId] = useState(null);

  // Modal for Reason (Withdraw / Cancel)
  const [actionType, setActionType] = useState(null); // 'withdraw' | 'cancel'
  const [reasonModalOpen, setReasonModalOpen] = useState(false);
  const [reasonText, setReasonText] = useState('');
  const [submittingAction, setSubmittingAction] = useState(false);

  useEffect(() => {
    portalService
      ?.getProfile?.()
      ?.then((profile) => {
        if (profile?.id) setCurrentEmployeeId(profile.id);
      })
      ?.catch(() => {});
  }, []);

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
            .list()
            .then((res) => {
              const types = Array.isArray(res) ? res : res?.items || [];
              const match = types.find((t) => t.id === req.leaveTypeId);
              if (match) setLeaveType(match);
            })
            .catch(() => {
              if (req.employeeId) {
                return leaveTypeService
                  .eligible(req.employeeId)
                  .then((res) => {
                    const types = Array.isArray(res) ? res : res?.items || [];
                    const match = types.find((t) => t.id === req.leaveTypeId);
                    if (match) setLeaveType(match);
                  })
                  .catch(() => {});
              }
            })
        );
      }

      if (req.approvalInstanceId) {
        promises.push(
          approvalService
            .history(req.approvalInstanceId)
            .then((data) => {
              const steps = data?.steps || (Array.isArray(data) ? data : []);
              setApprovalHistory(steps);
            })
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

  const handleReassign = async () => {
    let values;
    try {
      values = await reassignForm.validateFields();
    } catch {
      return;
    }
    setReassigning(true);
    try {
      await approvalService.reassign(request.approvalInstanceId, values);
      await successMsg('Approver changed', 'The leave request now waits for the new approver.');
      setReassignOpen(false);
      reassignForm.resetFields();
      loadDetail();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setReassigning(false);
    }
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
  const effectiveBackPath =
    backPath || (isPortal ? '/me/leave' : canReadAll ? '/leave/requests' : '/approvals');

  // D-54: In HRMS workflows, "Withdraw" is strictly an employee self-service action allowing applicants
  // to pull back their own pending request before an approval decision is made. Administrators and
  // approvers should only see Approve, Reject, or Cancel Leave (for approved leaves), not a self-service Withdraw button.
  const isApplicant = Boolean(currentEmployeeId && request?.employeeId && currentEmployeeId === request.employeeId);
  const canWithdraw = isApplicant && (request.status === 'PENDING' || request.status === 'SUBMITTED');
  const canCancel = !isReadOnly && canManage && request.status === 'APPROVED';
  const showReassign =
    !isReadOnly &&
    canReassign &&
    Boolean(request.approvalInstanceId) &&
    (request.status === 'PENDING' || request.status === 'SUBMITTED');

  return (
    <div className="saas-page-container fadeSlideUp" style={{ maxWidth: 900, margin: '0 auto' }}>
      <Card className="saas-card" variant="borderless">
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <PageHeader
            title="Leave Request Details"
            count={statusConfig.label}
            onBack={() => navigate(effectiveBackPath)}
            backLabel="Back"
            actions={
              <Space>
                {showReassign && (
                  <Button
                    className="btn-spring"
                    icon={<SwapOutlined />}
                    onClick={() => setReassignOpen(true)}
                    data-testid="btn-leave-reassign"
                  >
                    Reassign approver
                  </Button>
                )}
                {canWithdraw && (
                  <Button
                    danger
                    className="btn-spring"
                    icon={<StopOutlined />}
                    onClick={() => handleOpenActionModal('withdraw')}
                  >
                    Withdraw
                  </Button>
                )}
                {canCancel && (
                  <Button
                    danger
                    className="btn-spring"
                    icon={<CloseCircleOutlined />}
                    onClick={() => handleOpenActionModal('cancel')}
                  >
                    Cancel Leave
                  </Button>
                )}
              </Space>
            }
          />

        <Descriptions bordered column={{ xs: 1, sm: 2, md: 2 }}>
          <Descriptions.Item label="Employee">
            {employee
              ? `${employee.firstName || ''} ${employee.lastName || ''} (${employee.employeeNumber || employee.id})`.trim()
              : request.employeeId}
          </Descriptions.Item>
          <Descriptions.Item label="Leave Type">
            {leaveType ? `${leaveType.name} (${leaveType.code})` : request.leaveTypeId}
          </Descriptions.Item>
          <Descriptions.Item label="From Date">{formatDate(request.fromDate)}</Descriptions.Item>
          <Descriptions.Item label="To Date">{formatDate(request.toDate)}</Descriptions.Item>
          <Descriptions.Item label="Duration">
            <Space>
              <Text>{request.workingDays} working day(s)</Text>
              {request.isHalfDay && (
                <Tag color="cyan">
                  {request.halfDayPeriod?.toUpperCase() === 'FIRST' ? '1st Half' : '2nd Half'}
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
          {request.documentIds && request.documentIds.length > 0 && (
            <Descriptions.Item label="Attachments" span={2}>
              <Text>{request.documentIds.length} document(s) attached</Text>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="Created At">
            {request.createdAt ? dayjs(request.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Decided At">
            {request.decidedAt ? dayjs(request.decidedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
        </Descriptions>

        <Card type="inner" variant="borderless" title="Approval Trail">
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
              items={approvalHistory.map((step) => {
                const decision = DECISIONS[step.decision] || PENDING_DECISION;
                const by = step.assigneeName ? `by ${step.assigneeName}` : null;
                const when = step.decidedAt ? dayjs(step.decidedAt).format('D MMM YYYY, HH:mm') : null;
                return {
                  key: step.stepId,
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

      <Modal
        title="Reassign approver"
        open={reassignOpen}
        onOk={handleReassign}
        onCancel={() => setReassignOpen(false)}
        confirmLoading={reassigning}
        okText="Reassign"
      >
        <Form form={reassignForm} layout="vertical">
          <Form.Item
            name="employeeId"
            label="New approver"
            rules={[{ required: true, message: 'Choose the new approver.' }]}
          >
            <Select
              id="input-leave-reassign-employee"
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
            rules={[{ required: true, message: 'Say why the approver changes.' }]}
          >
            <TextArea id="input-leave-reassign-reason" rows={3} placeholder="e.g. Back to reporting manager" />
          </Form.Item>
        </Form>
      </Modal>

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
    </div>
  );
}

LeaveRequestDetail.propTypes = {
  readOnly: PropTypes.bool,
  backPath: PropTypes.string,
};
