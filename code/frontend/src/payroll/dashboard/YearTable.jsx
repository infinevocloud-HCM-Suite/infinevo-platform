import PropTypes from 'prop-types';
import { useNavigate } from 'react-router-dom';
import { Card, Table, Tag } from 'antd';
import { formatAmount, formatDate } from '../claims/claimLabels.js';
import { runStatus } from './labels.js';

const statusCell = (s) => {
  const tag = runStatus(s);
  return <Tag color={tag.color}>{tag.label}</Tag>;
};

const money = (key, title) => ({ title, dataIndex: key, key, align: 'right', render: formatAmount });

/**
 * The year's months and the recent runs (W-47.5 §5). The months footer is `year_totals` as the
 * API sends it, over PAID runs only — never the column added up on the client.
 */
export function YearTable({ months, yearTotals, recentRuns }) {
  const navigate = useNavigate();
  const openRun = (record) => ({
    onClick: () => navigate(`/payroll/runs/${record.payrun_id}`),
    style: { cursor: 'pointer' },
  });

  const monthColumns = [
    { title: 'Period', dataIndex: 'period', key: 'period' },
    { title: 'Status', dataIndex: 'status', key: 'status', render: statusCell },
    money('gross', 'Gross'),
    money('deductions', 'Deductions'),
    money('tax', 'Tax'),
    money('net_pay', 'Net pay'),
  ];

  const recentColumns = [
    { title: 'Period', dataIndex: 'period', key: 'period' },
    { title: 'Status', dataIndex: 'status', key: 'status', render: statusCell },
    { title: 'Pay date', dataIndex: 'pay_date', key: 'pay_date', render: formatDate },
    money('net_pay', 'Net pay'),
  ];

  return (
    <>
      <Card title="This year" style={{ marginBottom: 16 }}>
        <Table
          rowKey="payrun_id"
          size="small"
          aria-label="Months"
          dataSource={months ?? []}
          columns={monthColumns}
          pagination={false}
          onRow={openRun}
          summary={() =>
            yearTotals ? (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={2}>
                  <strong>Paid ({yearTotals.paid_runs} runs)</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={2} align="right">
                  {formatAmount(yearTotals.gross)}
                </Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right">
                  {formatAmount(yearTotals.deductions)}
                </Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right">
                  {formatAmount(yearTotals.tax)}
                </Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right">
                  {formatAmount(yearTotals.net_pay)}
                </Table.Summary.Cell>
              </Table.Summary.Row>
            ) : null
          }
        />
      </Card>
      <Card title="Recent runs">
        <Table
          rowKey="payrun_id"
          size="small"
          aria-label="Recent runs"
          dataSource={recentRuns ?? []}
          columns={recentColumns}
          pagination={false}
          onRow={openRun}
        />
      </Card>
    </>
  );
}

const amount = PropTypes.oneOfType([PropTypes.number, PropTypes.string]);

YearTable.propTypes = {
  months: PropTypes.arrayOf(
    PropTypes.shape({
      payrun_id: PropTypes.string,
      period: PropTypes.string,
      status: PropTypes.string,
      gross: amount,
      deductions: amount,
      tax: amount,
      net_pay: amount,
    })
  ),
  yearTotals: PropTypes.shape({
    gross: amount,
    deductions: amount,
    tax: amount,
    net_pay: amount,
    paid_runs: PropTypes.number,
  }),
  recentRuns: PropTypes.arrayOf(
    PropTypes.shape({
      payrun_id: PropTypes.string,
      period: PropTypes.string,
      status: PropTypes.string,
      pay_date: PropTypes.string,
      net_pay: amount,
    })
  ),
};
