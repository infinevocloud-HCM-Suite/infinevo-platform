import { useState, useEffect, useCallback, useMemo } from 'react';
import { useDispatch } from 'react-redux';
import {
  Card,
  Button,
  Typography,
  Empty,
  Spin,
  Alert,
  Segmented,
  Modal,
  Input,
  Badge,
} from 'antd';
import {
  ReloadOutlined,
} from '@ant-design/icons';
import { mobileApprovalService } from './mobileApprovalService.js';
import { ApprovalRequestCard } from './ApprovalRequestCard.jsx';
import { MobileApprovalDetailDrawer } from './MobileApprovalDetailDrawer.jsx';
import { setPendingCount, decrementPendingCount } from '../../core/approvals/approvalSlice.js';
import { successMsg, errorMsg } from '../../shared/ui/msgHelper.js';

const { Title, Text } = Typography;
const { TextArea } = Input;

const FILTER_SEGMENTS = [
  { label: 'All', value: 'ALL' },
  { label: 'Leave', value: 'LEAVE' },
  { label: 'WFH', value: 'WORK_FROM_HOME' },
  { label: 'Regularization', value: 'REGULARIZATION' },
  { label: 'Overtime', value: 'OVERTIME' },
];

/**
 * Mobile Approvals Screen (W-98.5 §4.2)
 * Clean touch dashboard with category filter segments,
 * 1-tap Approve/Reject decisions, and audit trail drawers.
 */
export function MobileApprovalsScreen() {
  const dispatch = useDispatch();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [items, setItems] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [errorBanner, setErrorBanner] = useState(null);

  // Detail drawer state
  const [selectedItem, setSelectedItem] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Decision modal state
  const [decisionModal, setDecisionModal] = useState({
    open: false,
    decision: 'APPROVED', // 'APPROVED' or 'REJECTED'
    item: null,
    comment: '',
    error: '',
    submitting: false,
  });

  const loadData = useCallback(async () => {
    try {
      setErrorBanner(null);
      const res = await mobileApprovalService.getPendingApprovals({
        page: 0,
        size: 50,
      });
      setItems(res.rows || []);
      const total = res.unfilteredTotal ?? res.rows?.length ?? 0;
      dispatch(setPendingCount(total));
    } catch (err) {
      setErrorBanner(err?.message || 'Failed to load approvals');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [dispatch]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRefresh = () => {
    setRefreshing(true);
    loadData();
  };

  const filteredItems = useMemo(() => {
    if (selectedCategory === 'ALL') return items;
    return items.filter((item) => item.flowType === selectedCategory);
  }, [items, selectedCategory]);

  const handleOpenApprove = (item) => {
    setDecisionModal({
      open: true,
      decision: 'APPROVED',
      item,
      comment: '',
      error: '',
      submitting: false,
    });
  };

  const handleOpenReject = (item) => {
    setDecisionModal({
      open: true,
      decision: 'REJECTED',
      item,
      comment: '',
      error: '',
      submitting: false,
    });
  };

  const handleConfirmDecision = async () => {
    const { decision, item, comment } = decisionModal;
    if (!item) return;

    if (decision === 'REJECTED' && (!comment || !comment.trim())) {
      setDecisionModal((prev) => ({
        ...prev,
        error: 'Please provide a reason for rejection',
      }));
      return;
    }

    setDecisionModal((prev) => ({ ...prev, submitting: true, error: '' }));

    try {
      await mobileApprovalService.decide({
        stepId: item.id,
        decision,
        comment: comment.trim(),
      });

      // Remove decided item from local list
      setItems((prev) => prev.filter((i) => i.id !== item.id));
      dispatch(decrementPendingCount());

      setDecisionModal({
        open: false,
        decision: 'APPROVED',
        item: null,
        comment: '',
        error: '',
        submitting: false,
      });

      successMsg(
        decision === 'APPROVED' ? 'Request Approved' : 'Request Rejected'
      );
    } catch (err) {
      setDecisionModal((prev) => ({ ...prev, submitting: false }));
      errorMsg(err?.message || 'Failed to submit decision');
    }
  };

  const handleCardClick = (item) => {
    setSelectedItem(item);
    setDrawerOpen(true);
  };

  return (
    <div className="mobile-approvals-container" data-testid="mobile-approvals-screen">
      {/* Screen Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Title level={3} style={{ margin: 0, fontWeight: 700 }}>
            Approvals
          </Title>
          <Badge
            count={items.length}
            style={{
              backgroundColor: '#1677ff',
              fontWeight: 600,
            }}
            data-testid="approvals-badge-count"
          />
        </div>
        <Button
          type="text"
          shape="circle"
          icon={<ReloadOutlined spin={refreshing} />}
          onClick={handleRefresh}
          aria-label="Refresh approvals"
        />
      </div>

      {errorBanner && (
        <Alert
          type="error"
          showIcon
          message={errorBanner}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" type="primary" onClick={handleRefresh}>
              Retry
            </Button>
          }
        />
      )}

      {/* Segmented Filter Pills */}
      <div className="mobile-approvals-segmented" style={{ marginBottom: 16 }}>
        <Segmented
          block
          options={FILTER_SEGMENTS.map((seg) => {
            const count =
              seg.value === 'ALL'
                ? items.length
                : items.filter((i) => i.flowType === seg.value).length;
            return {
              label: `${seg.label}${count > 0 ? ` (${count})` : ''}`,
              value: seg.value,
            };
          })}
          value={selectedCategory}
          onChange={setSelectedCategory}
          data-testid="approval-segment-filter"
        />
      </div>

      {/* Main Approvals Feed */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }} data-testid="approvals-loading">
          <Spin size="large" />
          <div style={{ marginTop: 12, color: '#64748b', fontSize: 13 }}>
            Checking pending requests...
          </div>
        </div>
      ) : filteredItems.length === 0 ? (
        <Card style={{ borderRadius: 14, textAlign: 'center', padding: '40px 16px' }}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <div>
                <Text strong style={{ fontSize: 15, display: 'block', marginBottom: 4 }}>
                  All Caught Up!
                </Text>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  No pending requests waiting for your approval in this category.
                </Text>
              </div>
            }
          />
        </Card>
      ) : (
        <div className="mobile-approvals-feed" data-testid="approvals-feed">
          {filteredItems.map((item) => (
            <ApprovalRequestCard
              key={item.id}
              item={item}
              onApprove={handleOpenApprove}
              onReject={handleOpenReject}
              onClick={handleCardClick}
            />
          ))}
        </div>
      )}

      {/* Full Details Drawer */}
      <MobileApprovalDetailDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        item={selectedItem}
        onApprove={handleOpenApprove}
        onReject={handleOpenReject}
      />

      {/* Decision Confirmation Modal */}
      <Modal
        open={decisionModal.open}
        title={
          decisionModal.decision === 'APPROVED'
            ? 'Approve Request'
            : 'Reject Request'
        }
        onCancel={() =>
          setDecisionModal((prev) => ({ ...prev, open: false, error: '' }))
        }
        onOk={handleConfirmDecision}
        confirmLoading={decisionModal.submitting}
        okText={decisionModal.decision === 'APPROVED' ? 'Confirm Approval' : 'Confirm Rejection'}
        okButtonProps={
          decisionModal.decision === 'APPROVED'
            ? {
                style: {
                  backgroundColor: '#52c41a',
                  borderColor: '#52c41a',
                },
                'data-testid': 'modal-confirm-approve',
              }
            : {
                danger: true,
                'data-testid': 'modal-confirm-reject',
              }
        }
      >
        <div style={{ margin: '16px 0' }}>
          <Text style={{ display: 'block', marginBottom: 8, fontSize: 13 }}>
            {decisionModal.decision === 'APPROVED'
              ? 'Add an optional approval comment:'
              : 'Please enter a reason for rejecting this request:'}
          </Text>
          <TextArea
            rows={3}
            value={decisionModal.comment}
            onChange={(e) =>
              setDecisionModal((prev) => ({
                ...prev,
                comment: e.target.value,
                error: '',
              }))
            }
            placeholder={
              decisionModal.decision === 'APPROVED'
                ? 'Optional notes for employee...'
                : 'Reason required (e.g. Coverage required, project deadlines)...'
            }
            data-testid="decision-comment-input"
          />
          {decisionModal.error && (
            <Text type="danger" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
              {decisionModal.error}
            </Text>
          )}
        </div>
      </Modal>
    </div>
  );
}

