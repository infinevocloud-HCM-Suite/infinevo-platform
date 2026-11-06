import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, DatePicker, Drawer, Space, Table, Tag, Typography, theme } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useCan } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { requestService } from './requestService.js';
import { OvertimeForm } from './OvertimeForm.jsx';
import { FMT, MAX_RANGE_DAYS, STATUS_COLOR, presets, spanDays } from './range.js';
import { formatDate, orEmpty, statusLabel } from '@shared/ui/format.js';

const { Text } = Typography;

/** The caller's overtime requests and a form to ask for one (W-48.5 §5). */
export function MyOvertime() {
  const { token } = theme.useToken();
  const canRequest = useCan('hrms.overtime.request');
  const canReadAll = useCan('core.overtime.read');
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
      const list = await requestService.myOvertime(r[0].format(FMT), r[1].format(FMT));
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
    { title: 'Date', dataIndex: 'overtimeDate', key: 'date', render: (d) => <Text strong>{formatDate(d)}</Text> },
    { title: 'Hours', dataIndex: 'hours', key: 'hours' },
    { title: 'Remarks', dataIndex: 'remarks', key: 'remarks', ellipsis: true, render: (v) => orEmpty(v) },
    { title: 'Status', key: 'status', render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{statusLabel(r.status)}</Tag> },
  ];

  return (
    <div className="saas-page-container fadeSlideUp">
      <Card className="saas-card" variant="borderless">
        <PageHeader
          title="Overtime requests"
          subtitle="Submit and monitor extra hours and overtime compensation claims."
          count={range ? String(rows.length) : undefined}
          actions={
            canRequest && (
              <Button type="primary" className="btn-spring" onClick={() => setOpen(true)}>
                New request
              </Button>
            )
          }
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
          {canReadAll && <Link to="/hrms/overtime-requests/all">Everyone&apos;s requests</Link>}
          {error && <Alert type="error" message={error} showIcon style={{ marginBottom: token.margin }} />}
          <Table
            className="saas-table"
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            onRow={(r) => ({ onClick: () => navigate(`/hrms/overtime-requests/${r.id}`), style: { cursor: 'pointer' } })}
            locale={{ emptyText: range ? 'No requests in this range' : 'Pick a range to load requests' }}
          />
        </Space>
        <Drawer title="New overtime request" open={open} onClose={() => setOpen(false)} destroyOnClose width={420}>
          <OvertimeForm
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
