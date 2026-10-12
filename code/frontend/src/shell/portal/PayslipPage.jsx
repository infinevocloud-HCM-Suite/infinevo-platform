import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { Button, Card, Descriptions, Result, Skeleton, Space, Table, Typography, theme } from 'antd';
import { ArrowLeftOutlined, DollarOutlined } from '@ant-design/icons';
import { portalService } from './portalService.js';
import { EMPTY, formatDate, formatMoney } from '../../shared/ui/format.js';

const { Title, Text } = Typography;

/**
 * A pay period as the server sends it ('2026-07', a YearMonth) shown as "Jul 2026".
 *
 * @param {string|null} period
 * @returns {string}
 */
export function formatPeriod(period) {
  if (!period) return EMPTY;
  const month = dayjs(`${period}-01`);
  return month.isValid() ? month.format('MMM YYYY') : period;
}

function show(value) {
  return value === null || value === undefined || value === '' ? EMPTY : value;
}

const LINE_COLUMNS = [
  { title: 'Component', dataIndex: 'name', key: 'name' },
  {
    title: 'Amount',
    dataIndex: 'amount',
    key: 'amount',
    align: 'right',
    render: (amount) => formatMoney(amount),
  },
];

function PayslipLines({ title, lines, testId }) {
  return (
    <Card size="small" title={title} data-testid={testId}>
      <Table
        size="small"
        rowKey={(line, index) => line.code || `${title}-${index}`}
        columns={LINE_COLUMNS}
        dataSource={lines || []}
        pagination={false}
        locale={{ emptyText: 'None' }}
      />
    </Card>
  );
}

PayslipLines.propTypes = {
  title: PropTypes.string.isRequired,
  lines: PropTypes.arrayOf(
    PropTypes.shape({
      code: PropTypes.string,
      name: PropTypes.string,
      amount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    })
  ),
  testId: PropTypes.string.isRequired,
};

/**
 * One of the caller's paid payslips (D-74), read from GET /api/v1/me/payslips/{payrunId}.
 * Every field name below is PayslipResponse's @JsonProperty.
 */
export function PayslipPage() {
  const { payrunId } = useParams();
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [payslip, setPayslip] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPayslip(await portalService.getPayslip(payrunId));
    } catch (err) {
      setError(err?.message || 'Failed to load the payslip');
    } finally {
      setLoading(false);
    }
  }, [payrunId]);

  useEffect(() => {
    load();
  }, [load]);

  const back = (
    <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/me/payslips')}>
      Back to payslips
    </Button>
  );

  if (loading) {
    return (
      <Card data-testid="payslip-loading">
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  if (error || !payslip) {
    return (
      <Card>
        <Result
          status="error"
          title="Unable to load the payslip"
          subTitle={error}
          extra={
            <Space>
              {back}
              <Button type="primary" onClick={load}>
                Retry
              </Button>
            </Space>
          }
        />
      </Card>
    );
  }

  const run = payslip.run || {};
  const employee = payslip.employee || {};
  const days = payslip.days || {};
  const totals = payslip.totals || {};

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%', padding: token.paddingLG }} data-testid="payslip-page">
      {back}

      <Card style={{ borderRadius: token.borderRadiusLG }}>
        <Space align="center" style={{ marginBottom: token.marginMD }}>
          <DollarOutlined style={{ color: token.colorPrimary, fontSize: 24 }} />
          <div>
            <Title level={3} style={{ margin: 0 }}>
              Payslip for {formatPeriod(run.period)}
            </Title>
            <Text type="secondary">{show(payslip.employer?.name)}</Text>
          </div>
        </Space>

        <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
          <Descriptions.Item label="Employee">{show(employee.name)}</Descriptions.Item>
          <Descriptions.Item label="Employee #">{show(employee.number)}</Descriptions.Item>
          <Descriptions.Item label="Designation">{show(employee.designation)}</Descriptions.Item>
          <Descriptions.Item label="Department">{show(employee.department)}</Descriptions.Item>
          <Descriptions.Item label="Pay date">{formatDate(run.pay_date)}</Descriptions.Item>
          <Descriptions.Item label="Paid on">{formatDate(run.paid_on)}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card size="small" title="Days" data-testid="payslip-days">
        <Descriptions size="small" column={{ xs: 2, md: 4 }}>
          <Descriptions.Item label="Payable days">{show(days.payable_days)}</Descriptions.Item>
          <Descriptions.Item label="Paid days">{show(days.paid_days)}</Descriptions.Item>
          <Descriptions.Item label="Loss of pay days">{show(days.lop_days)}</Descriptions.Item>
          <Descriptions.Item label="Unpaid days">{show(days.unpaid_days)}</Descriptions.Item>
        </Descriptions>
      </Card>

      <PayslipLines title="Earnings" lines={payslip.earnings} testId="payslip-earnings" />
      <PayslipLines title="Deductions" lines={payslip.deductions} testId="payslip-deductions" />
      <PayslipLines title="Reimbursements" lines={payslip.reimbursements} testId="payslip-reimbursements" />
      <PayslipLines title="Benefits" lines={payslip.benefits} testId="payslip-benefits" />

      <Card size="small" title="Totals" data-testid="payslip-totals">
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="Gross earnings">{formatMoney(totals.gross_earnings)}</Descriptions.Item>
          <Descriptions.Item label="Total deductions">{formatMoney(totals.total_deductions)}</Descriptions.Item>
          <Descriptions.Item label="Total reimbursements">
            {formatMoney(totals.total_reimbursements)}
          </Descriptions.Item>
          <Descriptions.Item label="Total benefits">{formatMoney(totals.total_benefits)}</Descriptions.Item>
          <Descriptions.Item label={<Text strong>Net pay</Text>}>
            <Text strong>{formatMoney(totals.net_pay)}</Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}

export default PayslipPage;
