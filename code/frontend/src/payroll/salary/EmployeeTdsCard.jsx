import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { Card, Descriptions, Typography, Spin, Button, Select, Space, Table, Tag, Empty, Alert, Popconfirm } from 'antd';
import { EditOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { employeeTdsService } from './employeeTdsService.js';
import { EmployeeTdsDrawer } from './EmployeeTdsDrawer.jsx';
import { currentFy, fyOptions, fyForApi } from '../tax/financialYear.js';
import { MoneyText } from '@shared/ui/MoneyText.jsx';
import { EMPTY, formatMonth, formatDateTime, humanize } from '@shared/ui/format.js';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

function orEmpty(value) {
  return value === undefined || value === null || String(value).trim() === '' ? EMPTY : value;
}

/**
 * D-151: an automatic recalculation does not replace an officer's figure; it leaves a suggestion beside it.
 * The officer applies it (it becomes the active figure) or clears it (the officer's figure stays).
 */
function SuggestionAlert({ suggestion, canVerify, busy, onApply, onClear }) {
  return (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      data-testid="employee-tds-suggestion"
      message={
        <span>
          A recalculation suggests annual tax <MoneyText value={suggestion.annual_tax} /> ({humanize(suggestion.regime)}{' '}
          regime) on {formatDateTime(suggestion.suggested_at)}. The pay run keeps using the officer&apos;s figure until
          you apply or clear this.
        </span>
      }
      action={
        canVerify && (
          <Space direction="vertical">
            <Popconfirm
              title="Use the suggested figure?"
              description="It replaces the officer's figure from the next pay run."
              okText="Apply"
              onConfirm={onApply}
            >
              <Button id="btn-apply-tds-suggestion" data-testid="employee-tds-suggestion-apply" size="small" type="primary" loading={busy}>
                Apply suggestion
              </Button>
            </Popconfirm>
            <Button id="btn-clear-tds-suggestion" data-testid="employee-tds-suggestion-clear" size="small" disabled={busy} onClick={onClear}>
              Clear suggestion
            </Button>
          </Space>
        )
      }
    />
  );
}

SuggestionAlert.propTypes = {
  suggestion: PropTypes.object.isRequired,
  canVerify: PropTypes.bool,
  busy: PropTypes.bool,
  onApply: PropTypes.func.isRequired,
  onClear: PropTypes.func.isRequired,
};

/** The share is what the next regular run deducts; the server names that run's month (`next_pay_month`). */
function monthlyShareLabel(nextPayMonth) {
  return nextPayMonth ? `Monthly share (next pay run: ${formatMonth(nextPayMonth)})` : 'Monthly share';
}

const HISTORY_COLUMNS = [
  { title: 'Recorded', dataIndex: 'created_at', key: 'created_at', render: (v) => formatDateTime(v) },
  { title: 'Annual tax', dataIndex: 'annual_tax', key: 'annual_tax', render: (v) => <MoneyText value={v} /> },
  {
    title: 'Effective month',
    dataIndex: 'effective_from_period',
    key: 'effective_from_period',
    render: (v) => formatMonth(v),
  },
  { title: 'Source', dataIndex: 'source', key: 'source', render: (v) => humanize(v) },
  { title: 'Note', dataIndex: 'note', key: 'note', render: (v) => orEmpty(v) },
  { title: 'By', dataIndex: 'created_by', key: 'created_by', render: (v) => orEmpty(v) },
  {
    title: 'Status',
    dataIndex: 'is_active',
    key: 'is_active',
    render: (active) => (active ? <Tag color="green">Active</Tag> : <Tag>Superseded</Tag>),
  },
];

/**
 * The employee's TDS figures for a financial year on the Salary tab (D-145): the annual tax and how it
 * spreads (deducted so far, remaining, months left, and the share the next regular run deducts, from
 * `GET …/tds/{fy}`), and every
 * record for the year. A payroll officer with `payroll.tax_declaration.verify` sets the figure in the drawer.
 */
export function EmployeeTdsCard({ employeeId }) {
  const canRead = useCan('payroll.tax_declaration.read');
  const canVerify = useCan('payroll.tax_declaration.verify');
  const [fy, setFy] = useState(() => currentFy());
  const [record, setRecord] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [reload, setReload] = useState(0);
  const [busy, setBusy] = useState(false);

  const act = async (call, message) => {
    setBusy(true);
    try {
      await call(employeeId, fyForApi(fy));
      setBusy(false);
      await successMsg('Success', message);
      setReload((n) => n + 1);
    } catch (err) {
      setBusy(false);
      errorMsg(err);
    }
  };

  const load = useCallback(
    (live) => {
      const apiFy = fyForApi(fy);
      setLoading(true);
      const active = employeeTdsService.get(employeeId, apiFy).catch((err) => {
        if (err?.code !== 'NOT_FOUND') errorMsg(err);
        return null;
      });
      const all = employeeTdsService.history(employeeId, apiFy).catch((err) => {
        if (err?.code !== 'NOT_FOUND') errorMsg(err);
        return [];
      });
      Promise.all([active, all]).then(([current, rows]) => {
        if (!live()) return;
        setRecord(current || null);
        setHistory(rows || []);
        setLoading(false);
      });
    },
    [employeeId, fy]
  );

  useEffect(() => {
    if (!employeeId || !canRead) return undefined;
    let live = true;
    load(() => live);
    return () => {
      live = false;
    };
  }, [employeeId, canRead, load, reload]);

  if (!canRead || !employeeId) return null;

  const r = record || {};

  return (
    <Card
      variant="borderless"
      id="card-employee-tds"
      data-testid="employee-tds-card"
      title={<Title level={5} style={{ margin: 0 }}>TDS</Title>}
      extra={
        <Space>
          <Select
            id="select-tds-fy"
            data-testid="employee-tds-fy"
            value={fy}
            options={fyOptions(3)}
            onChange={setFy}
            style={{ width: 120 }}
            aria-label="Financial year"
          />
          {canVerify && (
            <Button id="btn-edit-tds" data-testid="btn-edit-tds" icon={<EditOutlined />} onClick={() => setDrawerOpen(true)}>
              Edit
            </Button>
          )}
        </Space>
      }
      style={{ marginTop: 24 }}
    >
      <Spin spinning={loading}>
        {record?.suggestion && (
          <SuggestionAlert
            suggestion={record.suggestion}
            canVerify={canVerify}
            busy={busy}
            onApply={() => act(employeeTdsService.applySuggestion, 'Suggested TDS figure applied')}
            onClear={() => act(employeeTdsService.clearSuggestion, 'Suggested TDS figure cleared')}
          />
        )}
        {record ? (
          <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }} data-testid="employee-tds-figures">
            <Descriptions.Item label="Annual gross">
              <MoneyText value={r.annual_gross} />
            </Descriptions.Item>
            <Descriptions.Item label="Taxable income">
              <MoneyText value={r.annual_taxable_income} />
            </Descriptions.Item>
            <Descriptions.Item label="Annual tax">
              <span data-testid="employee-tds-annual-tax">
                <MoneyText value={r.annual_tax} />
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="Regime">{humanize(r.regime)}</Descriptions.Item>
            <Descriptions.Item label="Effective month">{formatMonth(r.effective_from_period)}</Descriptions.Item>
            <Descriptions.Item label="Source">{humanize(r.source)}</Descriptions.Item>
            <Descriptions.Item label="Note" span={2}>
              {orEmpty(r.note)}
            </Descriptions.Item>
            <Descriptions.Item label="Deducted so far">
              <span data-testid="employee-tds-deducted">
                <MoneyText value={r.deducted_so_far ?? r.year_to_date} />
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="Remaining">
              <span data-testid="employee-tds-remaining">
                <MoneyText value={r.remaining} />
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="Months left">
              <span data-testid="employee-tds-months-left">{orEmpty(r.months_left)}</span>
            </Descriptions.Item>
            <Descriptions.Item label={monthlyShareLabel(r.next_pay_month)}>
              <span data-testid="employee-tds-monthly-share">
                <MoneyText value={r.monthly_share} />
              </span>
            </Descriptions.Item>
          </Descriptions>
        ) : (
          !loading && (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              data-testid="employee-tds-empty"
              description={<Text type="secondary">No TDS record for FY {fy}</Text>}
            />
          )
        )}

        {history.length > 0 && (
          <>
            <Title level={5} style={{ marginTop: 24 }}>
              History
            </Title>
            <Table
              data-testid="employee-tds-history"
              dataSource={history}
              columns={HISTORY_COLUMNS}
              rowKey="id"
              pagination={false}
              size="small"
            />
          </>
        )}
      </Spin>

      {drawerOpen && canVerify && (
        <EmployeeTdsDrawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          employeeId={employeeId}
          fy={fy}
          record={record}
          onSaved={() => setReload((n) => n + 1)}
        />
      )}
    </Card>
  );
}

EmployeeTdsCard.propTypes = {
  employeeId: PropTypes.string.isRequired,
};
