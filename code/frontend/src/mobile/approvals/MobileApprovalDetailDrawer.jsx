import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Drawer,
  Card,
  Button,
  Typography,
  Tag,
  Timeline,
  Divider,
  Skeleton,
  Alert,
} from 'antd';
import {
  CheckOutlined,
  CloseOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  UserOutlined,
  CalendarOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { mobileApprovalService } from './mobileApprovalService.js';

const { Title, Text } = Typography;

function formatTimestamp(str) {
  if (!str) return 'N/A';
  const d = dayjs(str);
  return d.isValid() ? d.format('DD MMM YYYY, hh:mm A') : String(str);
}

/**
 * Mobile Approval Detail Drawer (W-98.5 §4.2)
 * Slide-up bottom sheet rendering comprehensive request details
 * and multi-stage approval audit trail (<Timeline>).
 */
export function MobileApprovalDetailDrawer({
  open,
  onClose,
  item,
  onApprove,
  onReject,
}) {
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState(null);

  const instanceId = item?.instanceId;

  const loadDetail = useCallback(async () => {
    if (!instanceId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await mobileApprovalService.getInstanceDetail(instanceId);
      setHistory(res.history || []);
    } catch (err) {
      setError(err?.message || 'Failed to load request history');
    } finally {
      setLoading(false);
    }
  }, [instanceId]);

  useEffect(() => {
    if (open && instanceId) {
      loadDetail();
    } else if (!open) {
      setHistory([]);
      setError(null);
    }
  }, [open, instanceId, loadDetail]);

  const timelineItems = history.map((step, idx) => {
    const isApproved =
      step.status === 'APPROVED' || step.decision === 'APPROVED';
    const isRejected =
      step.status === 'REJECTED' || step.decision === 'REJECTED';
    const isPending =
      step.status === 'PENDING' || step.decision === 'PENDING';

    let color = 'gray';
    let icon = <ClockCircleOutlined />;
    if (isApproved) {
      color = 'green';
      icon = <CheckCircleOutlined />;
    } else if (isRejected) {
      color = 'red';
      icon = <CloseCircleOutlined />;
    } else if (isPending) {
      color = 'blue';
      icon = <ClockCircleOutlined />;
    }

    return {
      key: step.id || idx,
      color,
      dot: icon,
      children: (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text strong style={{ fontSize: 13 }}>
              {step.approverName || step.assigneeName || `Step ${(step.stepIndex ?? idx) + 1}`}
            </Text>
            <Tag
              color={isApproved ? 'success' : isRejected ? 'error' : 'processing'}
              style={{ borderRadius: 4, margin: 0, fontSize: 10 }}
            >
              {step.decision || step.status || 'PENDING'}
            </Tag>
          </div>
          <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>
            {formatTimestamp(step.decidedAt || step.createdAt || step.timestamp)}
          </div>
          {step.comment && (
            <div
              style={{
                background: '#f8fafc',
                padding: '6px 10px',
                borderRadius: 6,
                marginTop: 6,
                fontSize: 12,
                color: '#334155',
                border: '1px solid #f1f5f9',
              }}
            >
              {`"${step.comment}"`}
            </div>
          )}
        </div>
      ),
    };
  });

  return (
    <Drawer
      open={open}
      onClose={onClose}
      placement="bottom"
      height="88%"
      closable={false}
      className="mobile-approval-drawer"
      data-testid="mobile-approval-detail-drawer"
      styles={{
        body: { padding: '16px 20px', paddingBottom: 32 },
        header: { display: 'none' },
      }}
    >
      <div className="mobile-drawer-handle" aria-hidden="true" />

      {/* Top Bar with title and close */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0, fontWeight: 700 }}>
            {item?.flowType || 'Approval'} Details
          </Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {item?.subjectEmployeeName || 'Employee'}
          </Text>
        </div>
        <Button
          type="text"
          shape="circle"
          icon={<CloseOutlined />}
          onClick={onClose}
          aria-label="Close"
        />
      </div>

      {loading && (
        <div data-testid="approval-drawer-loading">
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      )}

      {error && !loading && (
        <Alert
          type="error"
          showIcon
          message="Unable to load history"
          description={error}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" type="primary" onClick={loadDetail}>
              Retry
            </Button>
          }
        />
      )}

      {!loading && item && (
        <div className="mobile-approval-drawer-content">
          {/* Summary Card */}
          <Card
            size="small"
            style={{
              borderRadius: 12,
              marginBottom: 16,
              background: '#f8fafc',
              border: '1px solid #e2e8f0',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
              <div
                style={{
                  width: 38,
                  height: 38,
                  borderRadius: '50%',
                  backgroundColor: '#e6f4ff',
                  color: '#1677ff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 600,
                }}
              >
                <UserOutlined />
              </div>
              <div style={{ flex: 1 }}>
                <Text strong style={{ fontSize: 14 }}>
                  {item.subjectEmployeeName || 'Employee'}
                </Text>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    ID: {item.subjectEmployeeId || 'N/A'} • {item.flowType}
                  </Text>
                </div>
              </div>
            </div>

            <Divider style={{ margin: '8px 0' }} />

            <div style={{ fontSize: 13, color: '#1e293b', marginBottom: 4 }}>
              <Text strong>Summary: </Text>
              <span>{item.summary}</span>
            </div>

            {item.reason && (
              <div style={{ fontSize: 12, color: '#64748b', marginTop: 4 }}>
                <Text strong style={{ color: '#475569' }}>Reason: </Text>
                <span>{`"${item.reason}"`}</span>
              </div>
            )}

            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 8 }}>
              <CalendarOutlined style={{ marginRight: 4 }} />
              Submitted: {formatTimestamp(item.createdAt)}
            </div>
          </Card>

          {/* Multi-tier Approval Trail Timeline */}
          <Card
            title="Approval Trail"
            size="small"
            style={{ borderRadius: 12, marginBottom: 20 }}
            data-testid="approval-timeline-card"
          >
            {timelineItems.length === 0 ? (
              <Timeline
                items={[
                  {
                    color: 'blue',
                    dot: <ClockCircleOutlined />,
                    children: (
                      <div>
                        <Text strong style={{ fontSize: 13 }}>Step {(item.stepIndex ?? 0) + 1} Pending</Text>
                        <div style={{ fontSize: 11, color: '#94a3b8' }}>Awaiting your decision</div>
                      </div>
                    ),
                  },
                ]}
              />
            ) : (
              <Timeline items={timelineItems} />
            )}
          </Card>

          {/* Action buttons */}
          <div style={{ display: 'flex', gap: 12 }}>
            <Button
              type="primary"
              icon={<CheckOutlined />}
              size="large"
              style={{
                flex: 1,
                borderRadius: 10,
                fontWeight: 600,
                backgroundColor: '#52c41a',
                borderColor: '#52c41a',
              }}
              onClick={() => {
                onClose();
                onApprove && onApprove(item);
              }}
              data-testid="drawer-btn-approve"
            >
              Approve
            </Button>
            <Button
              danger
              icon={<CloseOutlined />}
              size="large"
              style={{
                flex: 1,
                borderRadius: 10,
                fontWeight: 600,
              }}
              onClick={() => {
                onClose();
                onReject && onReject(item);
              }}
              data-testid="drawer-btn-reject"
            >
              Reject
            </Button>
          </div>
        </div>
      )}
    </Drawer>
  );
}

MobileApprovalDetailDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  item: PropTypes.object,
  onApprove: PropTypes.func,
  onReject: PropTypes.func,
};

