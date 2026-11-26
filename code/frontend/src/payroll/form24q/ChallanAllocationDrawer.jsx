import { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Table, InputNumber, Button, Space, Alert, Typography, Spin, Descriptions } from 'antd';
import { formatMoney } from '@shared/ui/format.js';
import { form24QService } from './form24QService';

const { Text } = Typography;

const key = (c) => `${c.payRunId}:${c.employeeId}`;
const num = (v) => Number(v || 0);
const round2 = (v) => Math.round(v * 100) / 100;

/**
 * The amounts that changed, as [payRunId, items] calls: every lowered amount first, then every raised one, so moving
 * tax from one month to another never passes the challan's total part-way through.
 */
export function changesByRun(candidates, amounts) {
  const down = new Map();
  const up = new Map();
  candidates.forEach((c) => {
    const next = amounts[key(c)];
    if (next === undefined) return;
    const was = round2(num(c.allocatedHere));
    const now = round2(num(next));
    if (now === was) return;
    const bucket = now < was ? down : up;
    if (!bucket.has(c.payRunId)) bucket.set(c.payRunId, []);
    bucket.get(c.payRunId).push({ employeeId: c.employeeId, amount: now });
  });
  return [...down.entries(), ...up.entries()];
}

/**
 * W-89: split one challan's tax across the employees whose TDS it paid — everyone with TDS on a paid run of the
 * challan's quarter. Each amount is capped by what other challans have not already covered.
 */
export function ChallanAllocationDrawer({ open, challan, onClose, onSaved }) {
  const [candidates, setCandidates] = useState([]);
  const [amounts, setAmounts] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!open || !challan) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    (async () => {
      try {
        const list = await form24QService.candidates(challan.id);
        if (cancelled) return;
        setCandidates(list);
        setAmounts(Object.fromEntries(list.map((c) => [key(c), num(c.allocatedHere)])));
      } catch (err) {
        if (!cancelled) setError(err?.message || 'Failed to load the employees with TDS this quarter');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, challan]);

  const allocatable = num(challan?.allocatableAmount);
  const total = useMemo(
    () => round2(candidates.reduce((sum, c) => sum + num(amounts[key(c)]), 0)),
    [candidates, amounts],
  );
  const left = round2(allocatable - total);

  /** Fills each employee's open TDS in order until the challan's tax is used up. */
  const fillRemaining = () => {
    let budget = round2(allocatable - candidates.reduce((s, c) => s + num(amounts[key(c)]), 0));
    const next = { ...amounts };
    candidates.forEach((c) => {
      if (budget <= 0) return;
      const max = round2(num(c.tdsDeducted) - num(c.allocatedElsewhere));
      const have = num(next[key(c)]);
      const add = Math.min(round2(max - have), budget);
      if (add > 0) {
        next[key(c)] = round2(have + add);
        budget = round2(budget - add);
      }
    });
    setAmounts(next);
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      for (const [runId, items] of changesByRun(candidates, amounts)) {
        await form24QService.allocate(challan.id, runId, items);
      }
      onSaved?.();
      onClose();
    } catch (err) {
      setError(err?.message || 'Failed to save the allocation');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title={challan ? `Allocate challan ${challan.bsrCode} / ${challan.challanSerialNo}` : 'Allocate challan'}
      open={open}
      onClose={onClose}
      width={960}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={fillRemaining} disabled={loading || left <= 0} data-testid="btn-fill-remaining">
            Fill remaining
          </Button>
          <Button
            type="primary"
            onClick={save}
            loading={saving}
            disabled={loading || left < 0 || challan?.filed}
            data-testid="btn-save-allocation"
          >
            Save
          </Button>
        </Space>
      }
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      {challan?.filed && (
        <Alert type="info" showIcon message="This quarter is marked filed; reopen it to change allocations" style={{ marginBottom: 16 }} />
      )}
      <Descriptions size="small" column={3} bordered style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Tax on challan">{formatMoney(allocatable)}</Descriptions.Item>
        <Descriptions.Item label="Allocated">{formatMoney(total)}</Descriptions.Item>
        <Descriptions.Item label="Left">
          <Text type={left < 0 ? 'danger' : undefined} data-testid="allocation-left">
            {formatMoney(left)}
          </Text>
        </Descriptions.Item>
      </Descriptions>
      {left < 0 && <Alert type="error" showIcon message="More than the challan's tax is allocated" style={{ marginBottom: 16 }} />}
      {loading ? (
        <Spin />
      ) : (
        <Table
          size="small"
          rowKey={key}
          dataSource={candidates}
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: 'No employee had TDS on a paid run in this quarter' }}
          columns={[
            { title: 'Month', dataIndex: 'period', key: 'period', width: 90 },
            { title: 'No.', dataIndex: 'employeeNumber', key: 'no', width: 100 },
            { title: 'Name', dataIndex: 'employeeName', key: 'name', ellipsis: true },
            { title: 'TDS', key: 'tds', align: 'right', width: 120, render: (_, c) => formatMoney(c.tdsDeducted) },
            {
              title: 'Other challans',
              key: 'elsewhere',
              align: 'right',
              width: 130,
              render: (_, c) => formatMoney(c.allocatedElsewhere),
            },
            {
              title: 'This challan',
              key: 'here',
              width: 150,
              render: (_, c) => (
                <InputNumber
                  aria-label={`This challan for ${c.employeeNumber} ${c.period}`}
                  min={0}
                  max={round2(num(c.tdsDeducted) - num(c.allocatedElsewhere))}
                  precision={2}
                  value={amounts[key(c)]}
                  disabled={challan?.filed}
                  onChange={(v) => setAmounts((a) => ({ ...a, [key(c)]: v ?? 0 }))}
                  style={{ width: '100%' }}
                />
              ),
            },
          ]}
        />
      )}
    </Drawer>
  );
}

ChallanAllocationDrawer.propTypes = {
  open: PropTypes.bool,
  challan: PropTypes.object,
  onClose: PropTypes.func.isRequired,
  onSaved: PropTypes.func,
};
