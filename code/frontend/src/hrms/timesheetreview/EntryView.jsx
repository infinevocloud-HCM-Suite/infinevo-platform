import { useEffect, useState } from 'react';
import { Alert, Button, Card, Collapse, Descriptions, Space, Spin, Table, Tag, Typography } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { useCan } from '@shell/screens';
import { reviewService } from './reviewService.js';
import { STATUS_COLOR, STATUS_LABEL, formatHundredths } from '../timesheet/weekGrid.js';
import { collectDescriptions, dayTotals, gridColumns, sumRows, taskRows } from './reviewGrid.js';
import { formatDate, statusLabel } from '@shared/ui/format.js';
import { DecideModal } from '../../core/approvals/DecideModal.jsx';
import { approvalService } from '../../core/approvals/approvalService.js';
import { decrementPendingCount } from '../../core/approvals/approvalSlice.js';
import { successMsg } from '@shared/ui/msgHelper.js';

/** The one project line an approver decides (W-48.3 §5), opened from the approvals inbox. */
export function EntryView() {
  const { entryId } = useParams();
  const location = useLocation();
  const dispatch = useDispatch();
  const canDecide = useCan('core.approval.decide');

  const [entry, setEntry] = useState(null);
  const [error, setError] = useState(null);
  const [step, setStep] = useState(location.state?.step || null);
  const [decideModal, setDecideModal] = useState({
    open: false,
    decision: 'APPROVED',
  });

  useEffect(() => {
    let live = true;
    reviewService
      .entry(entryId)
      .then((d) => live && setEntry(d))
      .catch(() => live && setError('Could not load the timesheet entry.'));
    return () => {
      live = false;
    };
  }, [entryId]);

  useEffect(() => {
    if (location.state?.step) {
      setStep(location.state.step);
      return;
    }
    if (!canDecide || !entryId) return;
    let live = true;
    approvalService
      .pending(0, 100)
      .then((res) => {
        if (!live) return;
        const items = res?.content || (Array.isArray(res) ? res : []);
        const match = items.find((s) => String(s.itemId) === String(entryId) && !s.decision);
        if (match) setStep(match);
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [entryId, canDecide, location.state?.step]);

  const handleOpenDecide = (decision) => {
    setDecideModal({ open: true, decision });
  };

  const handleDecideSuccess = async () => {
    const decision = decideModal.decision;
    setDecideModal({ open: false, decision: 'APPROVED' });
    setStep(null);
    setEntry((prev) => (prev ? { ...prev, status: decision } : prev));
    dispatch(decrementPendingCount());
    await successMsg(
      'Decision Recorded',
      `Timesheet entry has been ${decision.toLowerCase()} successfully.`
    );
  };

  const back = <Link to="/approvals">Back to approvals</Link>;
  if (error) return <Alert type="error" message={error} action={back} />;
  if (!entry) return <Spin />;

  const rows = taskRows([entry]);
  const { cols, dates } = gridColumns(entry.week_start_date, { withProject: false });
  const totals = dayTotals(rows, dates);
  const descriptions = collectDescriptions(rows, dates);
  const isPending = entry.status === 'SUBMITTED';

  const extra = (
    <Space wrap>
      {canDecide && step && isPending && (
        <>
          <Button
            type="primary"
            size="small"
            icon={<CheckOutlined />}
            onClick={() => handleOpenDecide('APPROVED')}
            id={`btn-approve-${entry.id}`}
          >
            Approve
          </Button>
          <Button
            danger
            size="small"
            icon={<CloseOutlined />}
            onClick={() => handleOpenDecide('REJECTED')}
            id={`btn-reject-${entry.id}`}
          >
            Reject
          </Button>
        </>
      )}
      {back}
    </Space>
  );

  return (
    <Card title={entry.project_name || entry.project_id} extra={extra}>
      <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Employee">{entry.employee_name || entry.employee_id}</Descriptions.Item>
        <Descriptions.Item label="Week">
          {formatDate(entry.week_start_date)} to {formatDate(entry.week_end_date)}
        </Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[entry.status]}>{STATUS_LABEL[entry.status] || statusLabel(entry.status)}</Tag>
        </Descriptions.Item>
      </Descriptions>
      <Table
        rowKey="key"
        size="small"
        pagination={false}
        columns={cols}
        dataSource={rows}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>Total</Table.Summary.Cell>
            {totals.map((t, i) => (
              <Table.Summary.Cell key={dates[i]} index={i + 1} align="right">
                {formatHundredths(t)}
              </Table.Summary.Cell>
            ))}
            <Table.Summary.Cell index={8} align="right">
              {formatHundredths(sumRows(rows))}
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
      {descriptions.length > 0 && (
        <Collapse
          ghost
          defaultActiveKey={['descriptions']}
          style={{ marginTop: 16 }}
          items={[
            {
              key: 'descriptions',
              label: <Typography.Text strong>Work descriptions ({descriptions.length})</Typography.Text>,
              children: (
                <Descriptions size="small" column={1} bordered>
                  {descriptions.map((item, idx) => (
                    <Descriptions.Item
                      key={idx}
                      label={`${formatDate(item.date)} · ${item.task} (${formatHundredths(item.hours)}h)`}
                    >
                      <Typography.Text style={{ whiteSpace: 'pre-wrap' }}>{item.description}</Typography.Text>
                    </Descriptions.Item>
                  ))}
                </Descriptions>
              ),
            },
          ]}
        />
      )}
      {entry.rejection_reason && (
        <Typography.Paragraph type="danger" style={{ marginTop: 12 }}>
          Rejected: {entry.rejection_reason}
        </Typography.Paragraph>
      )}
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


