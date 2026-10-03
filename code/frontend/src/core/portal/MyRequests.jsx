import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useNavigate } from 'react-router-dom';
import { Table, Tag, Button, Space, Modal, Input, Typography, theme } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  StopOutlined,
  SendOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { portalService } from '@shell/portal/portalService.js';
import { leaveRequestService } from '../leave/leaveRequestService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { TextArea } = Input;
const { Text } = Typography;

const STATUS_CONFIG = {
  DRAFT: { color: 'default', label: 'Draft', icon: null },
  SUBMITTED: { color: 'processing', label: 'Submitted', icon: <ClockCircleOutlined /> },
  PENDING: { color: 'processing', label: 'Pending', icon: <ClockCircleOutlined /> },
  APPROVED: { color: 'success', label: 'Approved', icon: <CheckCircleOutlined /> },
  REJECTED: { color: 'error', label: 'Rejected', icon: <CloseCircleOutlined /> },
  WITHDRAWN: { color: 'default', label: 'Withdrawn', icon: <StopOutlined /> },
  CANCELLED: { color: 'purple', label: 'Cancelled', icon: <CloseCircleOutlined /> },
};

export function MyRequests({ requests: propRequests, onRefresh }) {
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const [requests, setRequests] = useState(propRequests || []);
  const [loading, setLoading] = useState(!propRequests);
  const [actionModalOpen, setActionModalOpen] = useState(false);
  const [modalActionType, setModalActionType] = useState(null); // 'withdraw' | 'cancel'
  const [targetRequestId, setTargetRequestId] = useState(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [submittingAction, setSubmittingAction] = useState(false);

  const fetchRequests = useCallback(async () => {
    setLoading(true);
    try {
      const data = await portalService.getLeaveRequests();
      const rows = Array.isArray(data) ? data : data?.content || [];
      setRequests(rows);
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (propRequests !== undefined) {
      setRequests(propRequests);
    } else {
      fetchRequests();
    }
  }, [propRequests, fetchRequests]);

  const handleOpenActionModal = (type, id) => {
    setModalActionType(type);
    setTargetRequestId(id);
    setReason('');
    setReasonError(null);
    setActionModalOpen(true);
  };

  const handleActionConfirm = async () => {
    if (!reason || !reason.trim()) {
      setReasonError('Reason is required');
      return;
    }

    setSubmittingAction(true);
    try {
      if (modalActionType === 'withdraw') {
        await leaveRequestService.withdraw(targetRequestId, reason.trim());
        await successMsg('Leave Request Withdrawn', 'Your request has been withdrawn.');
      } else if (modalActionType === 'cancel') {
        await leaveRequestService.cancel(targetRequestId, reason.trim());
        await successMsg('Leave Cancelled', 'Your approved leave has been cancelled.');
      }

      setActionModalOpen(false);
      if (onRefresh) {
        onRefresh();
      } else {
        await fetchRequests();
      }
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSubmittingAction(false);
    }
  };

  const handleSubmitDraft = async (id) => {
    try {
      await leaveRequestService.submit(id);
      await successMsg('Leave Request Submitted', 'Your draft leave request has been submitted.');
      if (onRefresh) {
        onRefresh();
      } else {
        await fetchRequests();
      }
    } catch (err) {
      await errorMsg(err);
    }
  };

  const columns = [
    {
      title: 'Leave Type',
      dataIndex: 'leaveTypeName',
      key: 'leaveType',
      render: (text, record) => text || record.leaveTypeCode || record.leaveTypeId || '—',
    },
    {
      title: 'Dates',
      key: 'dates',
      render: (_, record) => (
        <span>
          {record.fromDate} to {record.toDate}
          {record.isHalfDay && (
            <Tag color="cyan" style={{ marginLeft: 6 }}>
              {record.halfDayPeriod?.toUpperCase() === 'FIRST' ? '1st Half' : '2nd Half'}
            </Tag>
          )}
        </span>
      ),
    },
    {
      title: 'Days',
      dataIndex: 'workingDays',
      key: 'workingDays',
      render: (days) => (days !== undefined && days !== null ? `${days} day(s)` : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        const cfg = STATUS_CONFIG[status] || { color: 'default', label: status };
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: 'Submitted',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date) => (date ? dayjs(date).format('YYYY-MM-DD') : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => {
        const isDraft = record.status === 'DRAFT';
        const isPending = record.status === 'PENDING' || record.status === 'SUBMITTED';
        const isApproved = record.status === 'APPROVED';
        const isFuture = record.fromDate && dayjs(record.fromDate).isAfter(dayjs(), 'day');
        const canCancel = isApproved && isFuture;

        return (
          <Space size="small">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/me/leave/requests/${record.id}`)}
              id={`btn-view-${record.id}`}
            >
              View
            </Button>

            {isDraft && (
              <Button
                type="primary"
                size="small"
                icon={<SendOutlined />}
                onClick={() => handleSubmitDraft(record.id)}
                id={`btn-submit-${record.id}`}
              >
                Submit
              </Button>
            )}

            {isPending && (
              <Button
                danger
                size="small"
                icon={<StopOutlined />}
                onClick={() => handleOpenActionModal('withdraw', record.id)}
                id={`btn-withdraw-${record.id}`}
              >
                Withdraw
              </Button>
            )}

            {canCancel && (
              <Button
                danger
                size="small"
                icon={<CloseCircleOutlined />}
                onClick={() => handleOpenActionModal('cancel', record.id)}
                id={`btn-cancel-${record.id}`}
              >
                Cancel
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div data-testid="my-requests-table">
      <Table
        dataSource={requests}
        columns={columns}
        rowKey={(r, index) => r.id || index}
        loading={loading}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: 'No leave requests submitted yet.' }}
      />

      <Modal
        title={modalActionType === 'withdraw' ? 'Withdraw Leave Request' : 'Cancel Approved Leave'}
        open={actionModalOpen}
        onCancel={() => {
          if (!submittingAction) {
            setActionModalOpen(false);
          }
        }}
        onOk={handleActionConfirm}
        confirmLoading={submittingAction}
        okText={modalActionType === 'withdraw' ? 'Withdraw Request' : 'Cancel Leave'}
        okButtonProps={{ danger: true }}
      >
        <Space direction="vertical" style={{ width: '100%', marginTop: token.marginSM }} size="middle">
          <Text type="secondary">
            {modalActionType === 'withdraw'
              ? 'Please provide a reason for withdrawing your pending leave request:'
              : 'Please provide a reason for cancelling your approved future leave:'}
          </Text>
          <TextArea
            rows={4}
            placeholder="Reason (required)"
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              if (reasonError && e.target.value.trim()) {
                setReasonError(null);
              }
            }}
            status={reasonError ? 'error' : ''}
            id="input-action-reason"
          />
          {reasonError && <Text type="danger">{reasonError}</Text>}
        </Space>
      </Modal>
    </div>
  );
}

MyRequests.propTypes = {
  requests: PropTypes.array,
  onRefresh: PropTypes.func,
};

export default MyRequests;
