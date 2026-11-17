import PropTypes from 'prop-types';
import { Card, Tag, Button, Typography } from 'antd';
import {
  CheckOutlined,
  CloseOutlined,
  ClockCircleOutlined,
  CalendarOutlined,
  UserOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

const { Text } = Typography;

const FLOW_TYPE_CONFIG = {
  LEAVE: { color: 'blue', label: 'Leave' },
  WORK_FROM_HOME: { color: 'cyan', label: 'WFH' },
  REGULARIZATION: { color: 'purple', label: 'Regularization' },
  OVERTIME: { color: 'orange', label: 'Overtime' },
  REIMBURSEMENT: { color: 'green', label: 'Claim' },
  TIMESHEET: { color: 'magenta', label: 'Timesheet' },
  PROOF_OF_INVESTMENT: { color: 'geekblue', label: 'POI' },
};

function getFlowBadge(flowType) {
  const cfg = FLOW_TYPE_CONFIG[flowType] || {
    color: 'default',
    label: flowType || 'General',
  };
  return (
    <Tag color={cfg.color} style={{ borderRadius: 6, fontWeight: 600, margin: 0 }}>
      {cfg.label}
    </Tag>
  );
}

function formatWaitingSince(dateStr) {
  if (!dateStr) return '';
  const d = dayjs(dateStr);
  return d.isValid() ? d.fromNow() : '';
}

/**
 * Touch-optimized Approval Request Card (W-98.5 §4.2)
 * Features employee monogram, request details, waiting time badge,
 * and 1-tap Approve & Reject buttons.
 */
export function ApprovalRequestCard({ item, onApprove, onReject, onClick }) {
  const employeeName = item.subjectEmployeeName || 'Employee';
  const initial = employeeName.charAt(0).toUpperCase();
  const waitingText = formatWaitingSince(item.createdAt);

  return (
    <Card
      className="mobile-approval-card"
      hoverable
      data-testid={`approval-card-${item.id}`}
      style={{
        borderRadius: 14,
        border: '1px solid #e2e8f0',
        marginBottom: 12,
        boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)',
      }}
      styles={{ body: { padding: '14px 16px' } }}
      onClick={() => onClick && onClick(item)}
    >
      {/* Top Header: Employee Info + Flow Badge */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 10,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              backgroundColor: '#e6f4ff',
              color: '#1677ff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: 14,
              flexShrink: 0,
            }}
          >
            {initial || <UserOutlined />}
          </div>
          <div>
            <Text strong style={{ fontSize: 14, display: 'block', lineHeight: 1.3 }}>
              {employeeName}
            </Text>
            {item.subjectEmployeeId && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                ID: {item.subjectEmployeeId}
              </Text>
            )}
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
          {getFlowBadge(item.flowType)}
          {waitingText && (
            <span style={{ fontSize: 10, color: '#94a3b8' }}>
              <ClockCircleOutlined style={{ marginRight: 3 }} />
              {waitingText}
            </span>
          )}
        </div>
      </div>

      {/* Summary / Request Description */}
      <div style={{ marginBottom: 12 }}>
        <Text style={{ fontSize: 13, color: '#334155', fontWeight: 500, display: 'block' }}>
          {item.summary}
        </Text>
        {item.reason && item.reason !== item.summary && (
          <Text
            type="secondary"
            style={{
              fontSize: 12,
              fontStyle: 'italic',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              marginTop: 2,
            }}
          >
            {`"${item.reason}"`}
          </Text>
        )}
      </div>

      {/* Step and Meta Info */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          paddingTop: 8,
          borderTop: '1px solid #f1f5f9',
          marginBottom: 12,
        }}
      >
        <span style={{ fontSize: 11, color: '#64748b' }}>
          Step {(item.stepIndex ?? 0) + 1} of {item.totalSteps || 1}
        </span>
        {item.daysCount && (
          <Tag color="default" style={{ borderRadius: 4, margin: 0, fontSize: 11 }}>
            <CalendarOutlined style={{ marginRight: 4 }} />
            {item.daysCount} {Number(item.daysCount) === 1 ? 'day' : 'days'}
          </Tag>
        )}
      </div>

      {/* 1-Tap Action Buttons */}
      <div style={{ display: 'flex', gap: 10 }} onClick={(e) => e.stopPropagation()}>
        <Button
          type="primary"
          icon={<CheckOutlined />}
          style={{
            flex: 1,
            borderRadius: 8,
            fontWeight: 600,
            backgroundColor: '#52c41a',
            borderColor: '#52c41a',
            boxShadow: 'none',
          }}
          onClick={() => onApprove && onApprove(item)}
          data-testid={`btn-quick-approve-${item.id}`}
        >
          Approve
        </Button>
        <Button
          danger
          icon={<CloseOutlined />}
          style={{
            flex: 1,
            borderRadius: 8,
            fontWeight: 600,
          }}
          onClick={() => onReject && onReject(item)}
          data-testid={`btn-quick-reject-${item.id}`}
        >
          Reject
        </Button>
      </div>
    </Card>
  );
}

ApprovalRequestCard.propTypes = {
  item: PropTypes.shape({
    id: PropTypes.string.isRequired,
    instanceId: PropTypes.string,
    flowType: PropTypes.string,
    summary: PropTypes.string,
    subjectEmployeeName: PropTypes.string,
    subjectEmployeeId: PropTypes.string,
    stepIndex: PropTypes.number,
    totalSteps: PropTypes.number,
    createdAt: PropTypes.string,
    reason: PropTypes.string,
    daysCount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  }).isRequired,
  onApprove: PropTypes.func.isRequired,
  onReject: PropTypes.func.isRequired,
  onClick: PropTypes.func,
};

