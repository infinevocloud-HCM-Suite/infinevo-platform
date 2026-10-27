import { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Tag } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { NotFound, useCan } from '@shell/screens';
import { DecideModal } from '../../core/approvals/DecideModal.jsx';
import { approvalService } from '../../core/approvals/approvalService.js';
import { decrementPendingCount } from '../../core/approvals/approvalSlice.js';
import { successMsg } from '@shared/ui/msgHelper.js';
import { requestService } from './requestService.js';
import { STATUS_COLOR, stamp, timeOf } from './range.js';
import { EMPTY, formatDate } from '@shared/ui/format.js';

/** The back link: to the inbox when opened from it, else to the list (W-48.5 §5). */
export function backLink(location, list) {
  return location.state?.from === '/approvals' ? (
    <Link to="/approvals">Back to approvals</Link>
  ) : (
    <Link to={list}>Back to list</Link>
  );
}



/** One regularization request (W-48.5 §5). Displays request details and inline approval controls for approvers. */
export function RegularizationDetail() {
  const { id } = useParams();
  const location = useLocation();
  const dispatch = useDispatch();
  const canDecide = useCan('core.approval.decide');

  const [item, setItem] = useState(null);
  const [error, setError] = useState(null);
  const [step, setStep] = useState(location.state?.step || null);
  const [decideModal, setDecideModal] = useState({
    open: false,
    decision: 'APPROVED',
  });

  useEffect(() => {
    let live = true;
    setItem(null);
    setError(null);
    requestService
      .regularization(id)
      .then((d) => live && setItem(d))
      .catch((err) => live && setError(err || {}));
    return () => {
      live = false;
    };
  }, [id]);

  useEffect(() => {
    if (location.state?.step) {
      setStep(location.state.step);
      return;
    }
    if (!canDecide || !id) return;
    let live = true;
    approvalService
      .pending(0, 100)
      .then((res) => {
        if (!live) return;
        const items = res?.content || (Array.isArray(res) ? res : []);
        const match = items.find((s) => String(s.itemId) === String(id) && !s.decision);
        if (match) setStep(match);
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [id, canDecide, location.state?.step]);

  const handleOpenDecide = (decision) => {
    setDecideModal({ open: true, decision });
  };

  const handleDecideSuccess = async () => {
    const decision = decideModal.decision;
    setDecideModal({ open: false, decision: 'APPROVED' });
    setStep(null);
    setItem((prev) => (prev ? { ...prev, status: decision } : prev));
    dispatch(decrementPendingCount());
    await successMsg(
      'Decision Recorded',
      `Regularization request has been ${decision.toLowerCase()} successfully.`
    );
  };

  const back = backLink(location, '/hrms/regularizations');
  if (error?.status === 404) return <NotFound />;
  if (error) return <Alert type="error" message={error.message || 'Could not load the request'} action={back} />;
  if (!item) return <Spin />;

  const isPending = item.status === 'PENDING' || item.status === 'SUBMITTED';

  const extra = (
    <Space wrap>
      {canDecide && step && isPending && (
        <>
          <Button
            type="primary"
            size="small"
            icon={<CheckOutlined />}
            onClick={() => handleOpenDecide('APPROVED')}
            id={`btn-approve-${item.id}`}
          >
            Approve
          </Button>
          <Button
            danger
            size="small"
            icon={<CloseOutlined />}
            onClick={() => handleOpenDecide('REJECTED')}
            id={`btn-reject-${item.id}`}
          >
            Reject
          </Button>
        </>
      )}
      {back}
    </Space>
  );

  return (
    <Card title="Regularization" extra={extra}>
      <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="Employee">{item.employeeName || EMPTY}</Descriptions.Item>
        <Descriptions.Item label="Date">{formatDate(item.date)}</Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[item.status]}>{item.status}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="In">{timeOf(item.inAt)}</Descriptions.Item>
        <Descriptions.Item label="Out">{timeOf(item.outAt)}</Descriptions.Item>
        <Descriptions.Item label="Reason" span={2}>
          {item.reason}
        </Descriptions.Item>
        <Descriptions.Item label="Decided at">{stamp(item.decidedAt)}</Descriptions.Item>
        {/* D-79: the reply names the decider; the id alone is never shown. */}
        <Descriptions.Item label="Decided by">{item.decidedByName || EMPTY}</Descriptions.Item>
        <Descriptions.Item label="Decision comment" span={2}>
          {item.decisionComment || '—'}
        </Descriptions.Item>
      </Descriptions>
      {step && (
        <DecideModal
          open={decideModal.open}
          decision={decideModal.decision}
          step={step}
          onCancel={() => setDecideModal({ open: false, decision: 'APPROVED' })}
          onSuccess={handleDecideSuccess}
        />
      )}
    </Card>
  );
}

