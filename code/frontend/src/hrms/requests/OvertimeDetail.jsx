import { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Tag } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { useLocation, useParams } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { NotFound, useCan } from '@shell/screens';
import { DecideModal } from '../../core/approvals/DecideModal.jsx';
import { approvalService } from '../../core/approvals/approvalService.js';
import { decrementPendingCount } from '../../core/approvals/approvalSlice.js';
import { successMsg } from '@shared/ui/msgHelper.js';
import { requestService } from './requestService.js';
import { backLink } from './RegularizationDetail.jsx';
import { STATUS_COLOR, stamp } from './range.js';
import { formatDate, formatMoney, formatMonth, humanize, statusLabel } from '@shared/ui/format.js';

/** One overtime request (W-48.5 §5). Displays request details and inline approval controls for approvers. */
export function OvertimeDetail() {
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
      .overtime(id)
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
      `Overtime request has been ${decision.toLowerCase()} successfully.`
    );
  };

  const back = backLink(location, '/hrms/overtime-requests');
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
    <Card title="Overtime request" extra={extra}>
      <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="Date">{formatDate(item.overtimeDate)}</Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[item.status]}>{statusLabel(item.status)}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Hours">{item.hours}</Descriptions.Item>
        <Descriptions.Item label="Amount">{formatMoney(item.amount)}</Descriptions.Item>
        <Descriptions.Item label="Source">{humanize(item.source)}</Descriptions.Item>
        {item.postedPeriod && <Descriptions.Item label="Posted period">{formatMonth(item.postedPeriod)}</Descriptions.Item>}
        <Descriptions.Item label="Remarks" span={2}>
          {item.remarks || '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Requested at">{stamp(item.createdAt)}</Descriptions.Item>
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

