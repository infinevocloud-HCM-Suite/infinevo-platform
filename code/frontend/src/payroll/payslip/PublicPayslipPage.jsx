import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Button, Card, Descriptions, Result, Skeleton, Space, Table, Typography, theme } from 'antd';
import { DollarOutlined, PrinterOutlined } from '@ant-design/icons';
import { publicPayslipService } from './publicPayslipService.js';
import { EMPTY, formatDate, formatMoney } from '../../shared/ui/format.js';

const { Title, Text } = Typography;

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
  if (!lines || lines.length === 0) return null;
  return (
    <Card size="small" title={title} data-testid={testId}>
      <Table
        size="small"
        rowKey={(line, index) => line.code || `${title}-${index}`}
        columns={LINE_COLUMNS}
        dataSource={lines}
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
 * Public payslip viewer reachable without authentication via signed email link (W-36.2 / Defect #7 R-03).
 * URL: /public/payslips?t={token}
 */
export function PublicPayslipPage() {
  const { token: antdToken } = theme.useToken();
  const [token] = useState(() => {
    if (typeof window === 'undefined') return '';
    try {
      const params = new URLSearchParams(window.location.search);
      return params.get('t') || params.get('token') || '';
    } catch {
      return '';
    }
  });

  const [payslip, setPayslip] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    if (!token) {
      setLoading(false);
      setError('This link is not valid or has expired');
      return;
    }

    setLoading(true);
    publicPayslipService
      .getOpenPayslip(token)
      .then((data) => {
        if (active) {
          setPayslip(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err?.response?.data?.message || err?.message || 'This link is not valid or has expired');
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [token]);

  if (loading) {
    return (
      <div style={{ maxWidth: 900, margin: '40px auto', padding: '0 24px' }}>
        <Card data-testid="public-payslip-loading">
          <Skeleton active paragraph={{ rows: 10 }} />
        </Card>
      </div>
    );
  }

  if (error || !payslip) {
    return (
      <div style={{ maxWidth: 700, margin: '60px auto', padding: '0 24px' }}>
        <Card data-testid="public-payslip-error">
          <Result
            status="404"
            title="404"
            subTitle="This link is not valid or has expired"
          />
        </Card>
      </div>
    );
  }

  const run = payslip.run || {};
  const employer = payslip.employer || {};
  const employee = payslip.employee || {};
  const days = payslip.days || {};
  const totals = payslip.totals || {};

  return (
    <div
      style={{
        maxWidth: 900,
        margin: '32px auto',
        padding: '0 24px',
      }}
      data-testid="public-payslip-page"
    >
      <style>{`
        @media print {
          .no-print {
            display: none !important;
          }
          body {
            background: #fff !important;
          }
        }
      `}</style>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card style={{ borderRadius: antdToken.borderRadiusLG }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Space align="center" style={{ marginBottom: antdToken.marginMD }}>
              <DollarOutlined style={{ color: antdToken.colorPrimary, fontSize: 28 }} />
              <div>
                <Title level={3} style={{ margin: 0 }}>
                  Payslip for {formatPeriod(run.period)}
                </Title>
                <Text type="secondary" style={{ fontSize: 16 }}>
                  {show(employer.name)}
                </Text>
              </div>
            </Space>
            <Button
              icon={<PrinterOutlined />}
              onClick={() => window.print()}
              className="no-print"
              type="primary"
              ghost
              data-testid="print-payslip-button"
            >
              Print / Save PDF
            </Button>
          </div>

          <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
            <Descriptions.Item label="Employee">{show(employee.name)}</Descriptions.Item>
            <Descriptions.Item label="Employee #">{show(employee.number)}</Descriptions.Item>
            <Descriptions.Item label="Designation">{show(employee.designation)}</Descriptions.Item>
            <Descriptions.Item label="Department">{show(employee.department)}</Descriptions.Item>
            <Descriptions.Item label="Pay date">{formatDate(run.pay_date)}</Descriptions.Item>
            <Descriptions.Item label="Paid on">{formatDate(run.paid_on)}</Descriptions.Item>
          </Descriptions>
        </Card>

        <Card size="small" title="Days" data-testid="public-payslip-days">
          <Descriptions size="small" column={{ xs: 2, md: 4 }}>
            <Descriptions.Item label="Payable days">{show(days.payable_days)}</Descriptions.Item>
            <Descriptions.Item label="Paid days">{show(days.paid_days)}</Descriptions.Item>
            <Descriptions.Item label="Loss of pay days">{show(days.lop_days)}</Descriptions.Item>
            <Descriptions.Item label="Unpaid days">{show(days.unpaid_days)}</Descriptions.Item>
          </Descriptions>
        </Card>

        <PayslipLines title="Earnings" lines={payslip.earnings} testId="public-payslip-earnings" />
        <PayslipLines title="Deductions" lines={payslip.deductions} testId="public-payslip-deductions" />
        <PayslipLines title="Reimbursements" lines={payslip.reimbursements} testId="public-payslip-reimbursements" />
        <PayslipLines title="Benefits" lines={payslip.benefits} testId="public-payslip-benefits" />

        <Card size="small" title="Totals" data-testid="public-payslip-totals">
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="Gross earnings">{formatMoney(totals.gross_earnings)}</Descriptions.Item>
            <Descriptions.Item label="Total deductions">{formatMoney(totals.total_deductions)}</Descriptions.Item>
            {totals.total_reimbursements ? (
              <Descriptions.Item label="Total reimbursements">
                {formatMoney(totals.total_reimbursements)}
              </Descriptions.Item>
            ) : null}
            {totals.total_benefits ? (
              <Descriptions.Item label="Total benefits">{formatMoney(totals.total_benefits)}</Descriptions.Item>
            ) : null}
            <Descriptions.Item label={<Text strong>Net pay</Text>}>
              <Text strong style={{ fontSize: 18, color: antdToken.colorPrimary }}>
                {formatMoney(totals.net_pay)}
              </Text>
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </Space>
    </div>
  );
}

export default PublicPayslipPage;
