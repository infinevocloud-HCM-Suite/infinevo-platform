import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Result,
  Row,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
  theme,
} from 'antd';
import {
  DownloadOutlined,
  FileTextOutlined,
  LoginOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { publicPayslipService } from './publicPayslipService.js';
import { formatDate, formatMonth, formatMoney, orEmpty, statusTag } from '@shared/ui/format.js';

const { Title, Text, Paragraph } = Typography;

const LINE_COLUMNS = [
  {
    title: 'Component',
    dataIndex: 'name',
    key: 'name',
  },
  {
    title: 'Amount',
    dataIndex: 'amount',
    key: 'amount',
    align: 'right',
    render: (amount) => formatMoney(amount),
  },
];

function LineTable({ title, lines, totalAmount, testId }) {
  return (
    <Card
      size="small"
      title={<Text strong>{title}</Text>}
      extra={totalAmount !== undefined && <Text strong>{formatMoney(totalAmount)}</Text>}
      data-testid={testId}
      style={{ height: '100%' }}
    >
      <Table
        size="small"
        rowKey={(record) => record.code || record.name}
        columns={LINE_COLUMNS}
        dataSource={lines || []}
        pagination={false}
        locale={{ emptyText: 'No components' }}
      />
    </Card>
  );
}

LineTable.propTypes = {
  title: PropTypes.string.isRequired,
  lines: PropTypes.arrayOf(
    PropTypes.shape({
      code: PropTypes.string,
      name: PropTypes.string,
      amount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    }),
  ),
  totalAmount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  testId: PropTypes.string.isRequired,
};

/**
 * Public, unauthenticated payslip view screen (W-36.2 §4 / Defect #7).
 * Reads the token parameter ?t=<token> from the URL and fetches the payslip.
 * Allows viewing and downloading / printing the payslip without requiring sign-in.
 */
export function PublicPayslip({ token: propToken }) {
  const { token: antdThemeToken } = theme.useToken();
  const [payslip, setPayslip] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const resolveToken = useCallback(() => {
    if (propToken) return propToken;
    if (typeof window === 'undefined') return '';
    try {
      const params = new URLSearchParams(window.location.search);
      return params.get('t') || '';
    } catch {
      return '';
    }
  }, [propToken]);

  const loadPayslip = useCallback(async () => {
    const t = resolveToken();
    if (!t) {
      setLoading(false);
      setError('This link is not valid or has expired');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await publicPayslipService.getPayslip(t);
      setPayslip(data);
    } catch (err) {
      setError(err?.message || 'This link is not valid or has expired');
    } finally {
      setLoading(false);
    }
  }, [resolveToken]);

  useEffect(() => {
    loadPayslip();
  }, [loadPayslip]);

  const handlePrint = () => {
    if (typeof window !== 'undefined') {
      window.print();
    }
  };

  const handleSignIn = () => {
    if (typeof window !== 'undefined') {
      window.location.href = '/';
    }
  };

  if (loading) {
    return (
      <div style={{ maxWidth: 840, margin: '40px auto', padding: '0 16px' }} data-testid="public-payslip-loading">
        <Card>
          <Skeleton active paragraph={{ rows: 10 }} />
        </Card>
      </div>
    );
  }

  if (error || !payslip) {
    return (
      <div style={{ maxWidth: 600, margin: '60px auto', padding: '0 16px' }} data-testid="public-payslip-error">
        <Card style={{ borderRadius: antdThemeToken.borderRadiusLG, boxShadow: '0 4px 16px rgba(0,0,0,0.06)' }}>
          <Result
            status="warning"
            title="Unable to view payslip"
            subTitle={error || 'This link is not valid or has expired.'}
            extra={
              <Space>
                <Button icon={<ReloadOutlined />} onClick={loadPayslip}>
                  Retry
                </Button>
                <Button type="primary" icon={<LoginOutlined />} onClick={handleSignIn}>
                  Go to Sign In
                </Button>
              </Space>
            }
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
  const earnings = payslip.earnings || [];
  const deductions = payslip.deductions || [];
  const reimbursements = payslip.reimbursements || [];
  const benefits = payslip.benefits || [];
  const statusInfo = statusTag(run.status || 'PAID');

  return (
    <div
      style={{
        minHeight: '100vh',
        backgroundColor: '#f5f7fa',
        padding: '24px 16px 48px',
      }}
      data-testid="public-payslip-page"
    >
      <style>
        {`
          @media print {
            body {
              background: #fff !important;
            }
            .no-print {
              display: none !important;
            }
            .print-container {
              max-width: 100% !important;
              margin: 0 !important;
              padding: 0 !important;
              box-shadow: none !important;
            }
            .ant-card {
              box-shadow: none !important;
              border: 1px solid #e0e0e0 !important;
            }
          }
        `}
      </style>

      {/* Top Header Bar (hidden in print) */}
      <div
        className="no-print"
        style={{
          maxWidth: 900,
          margin: '0 auto 20px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <Space align="center">
          <FileTextOutlined style={{ fontSize: 24, color: antdThemeToken.colorPrimary }} />
          <div>
            <Title level={4} style={{ margin: 0, lineHeight: 1.2 }}>
              {employer.name || 'Infinevo'}
            </Title>
            <Text type="secondary" style={{ fontSize: 12 }}>
              HRMS & Payroll
            </Text>
          </div>
        </Space>
        <Space>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={handlePrint}
            data-testid="print-payslip-btn"
          >
            Download / Print PDF
          </Button>
          <Button icon={<LoginOutlined />} onClick={handleSignIn}>
            Sign In
          </Button>
        </Space>
      </div>

      {/* Payslip Document Container */}
      <div
        className="print-container"
        style={{
          maxWidth: 900,
          margin: '0 auto',
          backgroundColor: '#fff',
          borderRadius: antdThemeToken.borderRadiusLG,
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.07)',
          overflow: 'hidden',
        }}
      >
        {/* Document Header */}
        <div
          style={{
            padding: '24px 32px 16px',
            borderBottom: '1px solid #f0f0f0',
            backgroundColor: '#fafbfc',
          }}
        >
          <Row justify="space-between" align="middle" gutter={[16, 16]}>
            <Col xs={24} sm={16}>
              <Title level={3} style={{ margin: 0 }}>
                Payslip for {formatMonth(run.period)}
              </Title>
              <Text type="secondary" style={{ fontSize: 14 }}>
                {employer.name ? `${employer.name} • ` : ''}Pay Date: {formatDate(run.pay_date)}
              </Text>
            </Col>
            <Col xs={24} sm={8} style={{ textAlign: 'right' }}>
              <Tag color={statusInfo.color} style={{ fontSize: 13, padding: '4px 12px' }}>
                {statusInfo.label}
              </Tag>
              {run.paid_on && (
                <div style={{ marginTop: 4 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Paid on {formatDate(run.paid_on)}
                  </Text>
                </div>
              )}
            </Col>
          </Row>
        </div>

        <div style={{ padding: '24px 32px' }}>
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            {/* Employee Details */}
            <Card size="small" title={<Text strong>Employee Summary</Text>} data-testid="public-payslip-employee">
              <Descriptions size="small" bordered column={{ xs: 1, sm: 2, md: 3 }}>
                <Descriptions.Item label="Employee Name">{orEmpty(employee.name)}</Descriptions.Item>
                <Descriptions.Item label="Employee ID">{orEmpty(employee.number)}</Descriptions.Item>
                <Descriptions.Item label="Designation">{orEmpty(employee.designation)}</Descriptions.Item>
                <Descriptions.Item label="Department">{orEmpty(employee.department)}</Descriptions.Item>
                <Descriptions.Item label="Joining Date">{formatDate(employee.date_of_joining)}</Descriptions.Item>
                <Descriptions.Item label="Pay Period">{formatMonth(run.period)}</Descriptions.Item>
              </Descriptions>
            </Card>

            {/* Attendance & Days Summary */}
            <Card size="small" title={<Text strong>Attendance Summary</Text>} data-testid="public-payslip-days">
              <Descriptions size="small" column={{ xs: 2, sm: 4 }}>
                <Descriptions.Item label="Payable Days">{orEmpty(days.payable_days)}</Descriptions.Item>
                <Descriptions.Item label="Paid Days">{orEmpty(days.paid_days)}</Descriptions.Item>
                <Descriptions.Item label="Loss of Pay (LOP)">{orEmpty(days.lop_days)}</Descriptions.Item>
                <Descriptions.Item label="Unpaid Days">{orEmpty(days.unpaid_days)}</Descriptions.Item>
              </Descriptions>
            </Card>

            {/* Earnings & Deductions Tables */}
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <LineTable
                  title="Earnings"
                  lines={earnings}
                  totalAmount={totals.gross_earnings}
                  testId="public-payslip-earnings"
                />
              </Col>
              <Col xs={24} md={12}>
                <LineTable
                  title="Deductions"
                  lines={deductions}
                  totalAmount={totals.total_deductions}
                  testId="public-payslip-deductions"
                />
              </Col>
            </Row>

            {/* Reimbursements (if any) */}
            {reimbursements.length > 0 && (
              <LineTable
                title="Reimbursements"
                lines={reimbursements}
                totalAmount={totals.total_reimbursements}
                testId="public-payslip-reimbursements"
              />
            )}

            {/* Benefits (if any) */}
            {benefits.length > 0 && (
              <LineTable
                title="Benefits"
                lines={benefits}
                totalAmount={totals.total_benefits}
                testId="public-payslip-benefits"
              />
            )}

            {/* Summary / Totals */}
            <Card
              size="small"
              data-testid="public-payslip-totals"
              style={{
                backgroundColor: '#f9fbfd',
                borderColor: antdThemeToken.colorPrimaryBorder,
              }}
            >
              <Row gutter={[16, 16]} align="middle">
                <Col xs={24} sm={14}>
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="Gross Earnings">
                      <Text strong>{formatMoney(totals.gross_earnings)}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="Total Deductions">
                      <Text strong>{formatMoney(totals.total_deductions)}</Text>
                    </Descriptions.Item>
                    {totals.total_reimbursements ? (
                      <Descriptions.Item label="Total Reimbursements">
                        {formatMoney(totals.total_reimbursements)}
                      </Descriptions.Item>
                    ) : null}
                    {totals.total_benefits ? (
                      <Descriptions.Item label="Total Benefits">
                        {formatMoney(totals.total_benefits)}
                      </Descriptions.Item>
                    ) : null}
                  </Descriptions>
                </Col>
                <Col xs={24} sm={10} style={{ textAlign: 'right' }}>
                  <div
                    style={{
                      padding: '16px',
                      backgroundColor: '#fff',
                      borderRadius: antdThemeToken.borderRadius,
                      border: '1px solid #e8e8e8',
                      display: 'inline-block',
                      minWidth: 200,
                    }}
                  >
                    <Text type="secondary" style={{ fontSize: 13, textTransform: 'uppercase' }}>
                      Net Pay
                    </Text>
                    <Title level={2} style={{ margin: '4px 0 0', color: antdThemeToken.colorPrimary }}>
                      {formatMoney(totals.net_pay)}
                    </Title>
                  </div>
                </Col>
              </Row>
            </Card>

            <Divider style={{ margin: '8px 0' }} />

            {/* Legal / System Notice */}
            <div style={{ textAlign: 'center' }}>
              <Paragraph type="secondary" style={{ fontSize: 12, margin: 0 }}>
                This is a computer-generated payslip and does not require a physical signature.
              </Paragraph>
              {payslip.link?.expires_at && (
                <Paragraph type="secondary" style={{ fontSize: 11, margin: '4px 0 0' }}>
                  This public download link is valid until {formatDate(payslip.link.expires_at)}.
                </Paragraph>
              )}
            </div>
          </Space>
        </div>
      </div>
    </div>
  );
}

PublicPayslip.propTypes = {
  token: PropTypes.string,
};

export default PublicPayslip;
