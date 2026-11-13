import { useCallback, useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { Alert, Button, Card, DatePicker, Input, Modal, Space, Table, Tag, Typography, theme } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { NotEntitled, useCan } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';
import { formatDate, formatMonth } from '@shared/ui/format.js';
import { LOP_KIND, MANUAL_LOP_REF_PREFIX, lossOfPayService } from './lossOfPayService.js';
import { LossOfPayDrawer } from './LossOfPayDrawer.jsx';

const { Text } = Typography;
const MONTH = 'YYYY-MM';

/** "2.50" as "2.5", "3.00" as "3" - trimmed as text, never through a float. */
export function formatDays(v) {
  if (v == null || v === '') return '—';
  const text = String(v);
  return text.includes('.') ? text.replace(/\.?0+$/, '') : text;
}

/**
 * The reason shown for a row: its `note`. A row with no note shows its `source_ref` only when that
 * is a caller's own reference (a leave or attendance event), never this screen's random key.
 */
export function lopReason(r) {
  if (r?.note) return r.note;
  const ref = r?.source_ref;
  return ref && !ref.startsWith(MANUAL_LOP_REF_PREFIX) ? ref : '—';
}

/**
 * HR: loss-of-pay days entered by hand, one month at a time (D-140). The rows are core's
 * `LOP_DAYS` pay inputs for the month; a row with `reverses_id` is a reversal, and the row it
 * points at reads "Reversed".
 */
export function LossOfPayLog() {
  const { token } = theme.useToken();
  const canRead = useCan('core.pay_input.read');
  const canWrite = useCan('core.pay_input.write');
  const [month, setMonth] = useState(() => dayjs().startOf('month'));
  const [rows, setRows] = useState([]);
  const [names, setNames] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [adding, setAdding] = useState(false);
  const [reversing, setReversing] = useState(null);
  const [reason, setReason] = useState('');
  const [posting, setPosting] = useState(false);

  const load = useCallback(async (m) => {
    setLoading(true);
    setError(null);
    try {
      const data = await lossOfPayService.forPeriod(m.format(MONTH));
      setRows((data?.rows || []).filter((r) => r.kind === LOP_KIND));
    } catch (err) {
      setRows([]);
      setError(err?.message || 'Could not load loss of pay');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canRead) load(month);
  }, [canRead, load, month]);

  // Names once per visit; a failed directory leaves ids showing rather than hiding the rows.
  useEffect(() => {
    if (!canRead) return;
    lossOfPayService
      .directory()
      .then(setNames)
      .catch(() => setNames({}));
  }, [canRead]);

  const byId = useMemo(() => Object.fromEntries(rows.map((r) => [r.id, r])), [rows]);
  const reversed = useMemo(() => new Set(rows.filter((r) => r.reverses_id).map((r) => r.reverses_id)), [rows]);

  if (!canRead) return <NotEntitled />;

  const closeReverse = () => {
    setReversing(null);
    setReason('');
  };

  const confirmReverse = async () => {
    setPosting(true);
    try {
      await lossOfPayService.reverse(reversing.id, reason.trim());
      closeReverse();
      successMsg('Loss of pay reversed');
      load(month);
    } catch (err) {
      errorMsg(err);
    } finally {
      setPosting(false);
    }
  };

  const status = (r) => {
    if (r.reverses_id) {
      const original = byId[r.reverses_id];
      return <Tag color="orange">{`Reversal of ${formatDays(original ? original.quantity : r.quantity)}`}</Tag>;
    }
    return reversed.has(r.id) ? <Tag>Reversed</Tag> : <Tag color="green">Posted</Tag>;
  };

  const columns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) => <Text strong>{names[r.employee_id] || r.employee_id}</Text>,
    },
    { title: 'Month posted', dataIndex: 'posted_period', key: 'posted_period', render: (v) => formatMonth(v) },
    {
      title: 'Days',
      dataIndex: 'quantity',
      key: 'quantity',
      align: 'right',
      render: (v, r) => <span data-testid={`lop-days-${r.id}`}>{formatDays(v)}</span>,
    },
    {
      title: 'Reason',
      key: 'reason',
      render: (_, r) => <span data-testid={`lop-reason-${r.id}`}>{lopReason(r)}</span>,
    },
    { title: 'Entered by', dataIndex: 'created_by', key: 'created_by', render: (v) => v || '—' },
    { title: 'Entered on', dataIndex: 'created_at', key: 'created_at', render: (v) => formatDate(v) },
    {
      title: 'Status',
      key: 'status',
      render: (_, r) => <span data-testid={`lop-status-${r.id}`}>{status(r)}</span>,
    },
  ];
  if (canWrite) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      render: (_, r) =>
        !r.reverses_id && !reversed.has(r.id) ? (
          <Button size="small" danger data-testid={`lop-reverse-${r.id}`} onClick={() => setReversing(r)}>
            Reverse
          </Button>
        ) : null,
    });
  }

  return (
    <div className="saas-page-container fadeSlideUp">
      <Card className="saas-card" variant="borderless" data-testid="lop-log">
        <PageHeader
          title="Loss of pay"
          subtitle="Days of loss of pay entered by hand, which the pay run deducts."
          count={String(rows.length)}
          actions={
            canWrite ? (
              <Button type="primary" icon={<PlusOutlined />} id="lop-add" onClick={() => setAdding(true)}>
                Add loss of pay
              </Button>
            ) : null
          }
        />
        <Space direction="vertical" style={{ width: '100%' }}>
          <DatePicker
            id="lop-filter-month"
            picker="month"
            format="MMM YYYY"
            value={month}
            allowClear={false}
            onChange={(v) => v && setMonth(v.startOf('month'))}
            aria-label="Month"
            style={{ borderRadius: 8 }}
          />
          {error && <Alert type="error" message={error} showIcon style={{ marginBottom: token.margin }} />}
          <Table
            className="saas-table"
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            locale={{ emptyText: 'No loss of pay entered for this month' }}
          />
        </Space>
      </Card>
      {canWrite && (
        <LossOfPayDrawer
          open={adding}
          onClose={() => setAdding(false)}
          onSaved={(saved) => {
            setAdding(false);
            // D-142: an entry that landed in a later month (the chosen one was locked) is listed
            // there, so show that month; otherwise reload this one.
            const posted = saved?.posted_period;
            if (posted && posted !== month.format(MONTH)) setMonth(dayjs(posted, MONTH));
            else load(month);
          }}
        />
      )}
      <Modal
        title="Reverse loss of pay"
        open={!!reversing}
        onCancel={closeReverse}
        onOk={confirmReverse}
        okText="Reverse"
        okButtonProps={{ danger: true, disabled: !reason.trim(), loading: posting, id: 'lop-reverse-confirm' }}
        destroyOnClose
      >
        {reversing && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text>
              {`Reverse ${formatDays(reversing.quantity)} day(s) for ${
                names[reversing.employee_id] || reversing.employee_id
              }?`}
            </Text>
            <Input.TextArea
              id="lop-reverse-reason"
              aria-label="Reason for reversal"
              placeholder="Reason for reversal"
              maxLength={200}
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </Space>
        )}
      </Modal>
    </div>
  );
}
