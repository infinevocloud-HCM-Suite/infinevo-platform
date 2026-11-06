import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, DatePicker, Drawer, Space, Table, Tag, Typography, theme } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useCan } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { requestService } from './requestService.js';
import { RegularizationForm } from './RegularizationForm.jsx';
import { FMT, MAX_RANGE_DAYS, STATUS_COLOR, presets, spanDays, stamp, timeOf } from './range.js';
import { formatDate, statusLabel } from '@shared/ui/format.js';

const { Text } = Typography;

/** The caller's regularization requests, newest first, and a form to ask for one (W-48.5 §5). */
export function MyRegularizations() {
  const { token } = theme.useToken();
  const canMark = useCan('hrms.attendance.mark');
  const canReadAll = useCan('core.attendance.read');
  const navigate = useNavigate();
  const [range, setRange] = useState(null);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [open, setOpen] = useState(false);

  const load = useCallback(async (r) => {
    setLoading(true);
    setError(null);
    try {
      const data = await requestService.myRegularizations(r[0].format(FMT), r[1].format(FMT));
      const list = Array.isArray(data) ? [...data] : [];
      list.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
      setRows(list);
    } catch (err) {
      setRows([]);
      setError(err?.message || 'Could not load requests');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (range) load(range);
  }, [load, range]);

  const onRange = (value) => {
    if (!value || !value[0] || !value[1]) return;
    if (spanDays(value[0], value[1]) > MAX_RANGE_DAYS) {
      setError(`Pick at most ${MAX_RANGE_DAYS} days.`);
      return;
    }
    setRange([value[0], value[1]]);
  };

  const columns = [
    { title: 'Date', dataIndex: 'date', key: 'date', render: (d) => <Text strong>{formatDate(d)}</Text> },
    { title: 'In', key: 'in', render: (_, r) => timeOf(r.inAt) },
    { title: 'Out', key: 'out', render: (_, r) => timeOf(r.outAt) },
    { title: 'Reason', dataIndex: 'reason', key: 'reason', ellipsis: true },
    { title: 'Status', key: 'status', render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{statusLabel(r.status)}</Tag> },
    { title: 'Decided at', key: 'decidedAt', render: (_, r) => stamp(r.decidedAt) },
  ];

  const actions = (
    <Space>
      {canReadAll && <Link to="/hrms/regularizations/all">Everyone&apos;s requests</Link>}
      {canMark && (
        <Button type="primary" className="btn-spring" onClick={() => setOpen(true)}>
          New request
        </Button>
      )}
    </Space>
  );

  return (
    <div className="saas-page-container fadeSlideUp">
      <Card className="saas-card" variant="borderless">
        <PageHeader
          title="Regularizations"
          subtitle="Submit and track punch corrections, missed swipes, and attendance regularizations."
          count={range ? String(rows.length) : undefined}
          actions={actions}
        />
        <Space direction="vertical" style={{ width: '100%' }}>
          <DatePicker.RangePicker
            value={range}
            onChange={onRange}
            allowClear={false}
            aria-label="Range"
            placeholder={['Pick a range', `at most ${MAX_RANGE_DAYS} days`]}
            presets={presets}
            style={{ borderRadius: 8 }}
          />
          {error && <Alert type="error" message={error} showIcon style={{ marginBottom: token.margin }} />}
          <Table
            className="saas-table"
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            onRow={(r) => ({ onClick: () => navigate(`/hrms/regularizations/${r.id}`), style: { cursor: 'pointer' } })}
            locale={{ emptyText: range ? 'No requests in this range' : 'Pick a range to load requests' }}
          />
        </Space>
        <Drawer title="New regularization" open={open} onClose={() => setOpen(false)} destroyOnClose width={420}>
          <RegularizationForm
            onSaved={() => {
              setOpen(false);
              if (range) load(range);
            }}
          />
        </Drawer>
      </Card>
    </div>
  );
}
