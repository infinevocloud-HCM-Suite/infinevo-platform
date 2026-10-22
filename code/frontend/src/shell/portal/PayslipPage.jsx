import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { Button, Card, Result, Skeleton, Space, Typography, theme } from 'antd';
import { ArrowLeftOutlined, PrinterOutlined } from '@ant-design/icons';
import { portalService } from './portalService.js';
import { EMPTY, formatDate } from '../../shared/ui/format.js';
import { PayslipDocument } from '../../payroll/payslip/PayslipDocument.jsx';

const { Text } = Typography;

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

/**
 * One of the caller's paid payslips (D-74, D-102), read from GET /api/v1/me/payslips/{payrunId}.
 * Renders the full legacy payslip document with print and download capability.
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

  const handlePrint = () => {
    if (typeof window !== 'undefined') {
      window.print();
    }
  };

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

  return (
    <div style={{ width: '100%', padding: token.paddingLG }} data-testid="payslip-page">
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
          }
        `}
      </style>

      {/* Top Action Bar (hidden in print) */}
      <div
        className="no-print"
        style={{
          maxWidth: 900,
          margin: '0 auto 16px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <Space>
          {back}
          <Button
            type="primary"
            icon={<PrinterOutlined />}
            onClick={handlePrint}
            data-testid="print-payslip-btn"
          >
            Download / Print PDF
          </Button>
        </Space>
        <Space>
          {run.pay_date && (
            <Text type="secondary" style={{ fontSize: 13 }}>
              {formatDate(run.pay_date)}
            </Text>
          )}
          {run.paid_on && (
            <Text type="secondary" style={{ fontSize: 13 }}>
              {formatDate(run.paid_on)}
            </Text>
          )}
        </Space>
      </div>

      {/* Payslip Document */}
      <div className="print-container" style={{ maxWidth: 900, margin: '0 auto' }}>
        <PayslipDocument payslip={payslip} testIdPrefix="payslip" />
      </div>
    </div>
  );
}

export default PayslipPage;
