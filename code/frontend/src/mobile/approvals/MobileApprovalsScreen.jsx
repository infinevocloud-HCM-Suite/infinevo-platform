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

const KNOWN_FLOW_TYPES = ['LEAVE', 'WORK_FROM_HOME', 'REGULARIZATION', 'OVERTIME'];

const FILTER_SEGMENTS = [
  { label: 'All', value: 'ALL' },
  { label: 'Leave', value: 'LEAVE' },
  { label: 'WFH', value: 'WORK_FROM_HOME' },
  { label: 'Regularization', value: 'REGULARIZATION' },
  { label: 'Overtime', value: 'OVERTIME' },
  { label: 'Other', value: 'OTHER' },
];

/**
 * Mobile Approvals Screen (W-98.5)
 * Paged approvals inbox with category filters, 1-tap Approve/Reject decisions,
 * graceful 400 already-decided recovery, and detailed audit history drawer.
 */
export function MobileApprovalsScreen() {
  const dispatch = useDispatch();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [countsByType, setCountsByType] = useState({ ALL: 0 });
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
        size: 20,
      });
      setItems(res.rows || []);
      setPage(0);
      setHasMore(Boolean(res.hasMore));
      const total = res.totalElements ?? res.unfilteredTotal ?? res.rows?.length ?? 0;
      setTotalElements(total);
      if (res.countsByType) {
        setCountsByType(res.countsByType);
      }
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

  const handleLoadMore = async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      const nextPage = page + 1;
      const res = await mobileApprovalService.getPendingApprovals({
        page: nextPage,
        size: 20,
      });
      setItems((prev) => [...prev, ...(res.rows || [])]);
      setPage(nextPage);
      setHasMore(Boolean(res.hasMore));
    } catch (err) {
      errorMsg(err?.message || 'Failed to load more approvals');
    } finally {
      setLoadingMore(false);
    }
  };

  const filteredItems = useMemo(() => {
    if (selectedCategory === 'ALL') return items;
    if (selectedCategory === 'OTHER') {
      return items.filter((item) => !KNOWN_FLOW_TYPES.includes(item.flowType));
    }
    return items.filter((item) => item.flowType === selectedCategory);
  }, [items, selectedCategory]);

  const segmentOptions = useMemo(() => {
    return FILTER_SEGMENTS.map((seg) => {
      const count = countsByType[seg.value] ?? (seg.value === 'ALL' ? totalElements : 0);
      return {
        label: count !== undefined ? `${seg.label} (${count})` : seg.label,
        value: seg.value,
      };
    });
  }, [countsByType, totalElements]);

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
      setTotalElements((prev) => Math.max(0, prev - 1));
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
      const is400 = err?.response?.status === 400 || err?.status === 400;
      if (is400) {
        setDecisionModal({
          open: false,
          decision: 'APPROVED',
          item: null,
          comment: '',
          error: '',
          submitting: false,
        });
        errorMsg('This request was already decided');
        // Reload list to drop stale card
        loadData();
      } else {
        setDecisionModal((prev) => ({ ...prev, submitting: false }));
        errorMsg(err?.response?.data?.message || err?.message || 'Failed to submit decision');
      }
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
            count={totalElements}
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

      {errorBanner && items.length > 0 && (
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

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }} data-testid="approvals-loading">
          <Spin size="large" />
          <div style={{ marginTop: 12, color: '#64748b', fontSize: 13 }}>Loading pending approvals...</div>
        </div>
      ) : errorBanner && items.length === 0 ? (
        <Card
          style={{ borderRadius: 14, textAlign: 'center', padding: '40px 16px' }}
          data-testid="approvals-error-view"
        >
          <Alert
            type="error"
            showIcon
            message="Failed to load approvals"
            description={errorBanner}
            style={{ marginBottom: 16, textAlign: 'left' }}
          />
          <Button type="primary" onClick={handleRefresh}>
            Retry
          </Button>
        </Card>
      ) : items.length === 0 ? (
        <Card
          style={{ borderRadius: 14, textAlign: 'center', padding: '40px 16px' }}
          data-testid="approvals-empty-view"
        >
          <Empty
            description={
              <div>
                <Text strong style={{ fontSize: 16, display: 'block', marginBottom: 4 }}>
                  All Caught Up!
                </Text>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Nothing to approve. When team members submit requests, they will appear here.
                </Text>
              </div>
            }
          />
        </Card>
      ) : (
        <>
          {/* Category Filter Segments */}
          <div
            style={{
              marginBottom: 16,
              overflowX: 'auto',
              WebkitOverflowScrolling: 'touch',
              paddingBottom: 4,
            }}
          >
            <Segmented
              options={segmentOptions}
              value={selectedCategory}
              onChange={setSelectedCategory}
              block
              size="middle"
              style={{ minWidth: 420 }}
            />
          </div>

          {/* Pending Cards List */}
          <div className="mobile-approvals-feed" data-testid="approvals-feed">
            {filteredItems.length === 0 ? (
              <Card style={{ borderRadius: 12, textAlign: 'center', padding: '30px 16px' }}>
                <Empty description="No requests in this category" />
              </Card>
            ) : (
              filteredItems.map((item) => (
                <ApprovalRequestCard
                  key={item.id}
                  item={item}
                  onApprove={handleOpenApprove}
                  onReject={handleOpenReject}
                  onClick={handleCardClick}
                />
              ))
            )}

            {/* Next page loader */}
            {hasMore && (
              <div style={{ textAlign: 'center', marginTop: 16, marginBottom: 24 }}>
                <Button
                  onClick={handleLoadMore}
                  loading={loadingMore}
                  data-testid="btn-load-more-approvals"
                  style={{ borderRadius: 8 }}
                >
                  Load More
                </Button>
              </div>
            )}
          </div>
        </>
      )}

      {/* Confirmation Modal for 1-Tap Decision */}
      <Modal
        open={decisionModal.open}
        title={
          decisionModal.decision === 'APPROVED'
            ? 'Confirm Approval'
            : 'Confirm Rejection'
        }
        onCancel={() =>
          !decisionModal.submitting &&
          setDecisionModal((prev) => ({ ...prev, open: false }))
        }
        footer={[
          <Button
            key="cancel"
            onClick={() => setDecisionModal((prev) => ({ ...prev, open: false }))}
            disabled={decisionModal.submitting}
          >
            Cancel
          </Button>,
          <Button
            key="submit"
            type="primary"
            danger={decisionModal.decision === 'REJECTED'}
            loading={decisionModal.submitting}
            onClick={handleConfirmDecision}
            data-testid={
              decisionModal.decision === 'APPROVED'
                ? 'modal-confirm-approve'
                : 'modal-confirm-reject'
            }
          >
            {decisionModal.decision === 'APPROVED' ? 'Approve' : 'Reject'}
          </Button>,
        ]}
      >
        {decisionModal.item && (
          <div style={{ marginBottom: 16 }}>
            <Text strong style={{ fontSize: 14 }}>
              {decisionModal.item.subjectEmployeeName}
            </Text>
            <div style={{ color: '#64748b', fontSize: 13, marginTop: 4 }}>
              {decisionModal.item.summary}
            </div>
          </div>
        )}

        <div style={{ marginBottom: 8 }}>
          <Text style={{ fontSize: 13 }}>
            {decisionModal.decision === 'APPROVED'
              ? 'Comment (optional):'
              : 'Reason for rejection (required):'}
          </Text>
        </div>

        <TextArea
          rows={3}
          value={decisionModal.comment}
          onChange={(e) =>
            setDecisionModal((prev) => ({ ...prev, comment: e.target.value, error: '' }))
          }
          placeholder={
            decisionModal.decision === 'APPROVED'
              ? 'e.g. Approved. Have a good break!'
              : 'e.g. Critical deployment window, please reschedule.'
          }
          data-testid="decision-comment-input"
        />

        {decisionModal.error && (
          <div style={{ color: '#cf1322', fontSize: 12, marginTop: 6 }}>
            {decisionModal.error}
          </div>
        )}
      </Modal>

      {/* Slide-up detailed audit drawer */}
      <MobileApprovalDetailDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        instanceId={selectedItem?.instanceId}
        onDecided={() => {
          setDrawerOpen(false);
          loadData();
        }}
      />
    </div>
  );
}
