import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, DatePicker, Drawer, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useCan } from '@shell/screens';
import { requestService } from './requestService.js';
import { OvertimeForm } from './OvertimeForm.jsx';
import { FMT, MAX_RANGE_DAYS, STATUS_COLOR, presets, spanDays } from './range.js';

/** The caller's overtime requests and a form to ask for one (W-48.5 §5). */
export function MyOvertime() {
  const canRequest = useCan('hrms.overtime.request');
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
    { title: 'Date', dataIndex: 'overtimeDate', key: 'date' },
    { title: 'Hours', dataIndex: 'hours', key: 'hours' },
    { title: 'Remarks', dataIndex: 'remarks', key: 'remarks', ellipsis: true },
    { title: 'Status', key: 'status', render: (_, r) => <Tag color={STATUS_COLOR[r.status]}>{r.status}</Tag> },
  ];

  return (
    <Card
      title="Overtime requests"
      extra={
        canRequest && (
          <Button type="primary" onClick={() => setOpen(true)}>
            New request
          </Button>
        )
      }
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <DatePicker.RangePicker
          value={range}
          onChange={onRange}
          allowClear={false}
          aria-label="Range"
          placeholder={['Pick a range', `at most ${MAX_RANGE_DAYS} days`]}
          presets={presets}
        />
        {error && <Alert type="error" message={error} showIcon />}
        <Table
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
  );
}
