import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Card,
  Result,
  Skeleton,
  Space,
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
import { formatDate, formatMonth } from '@shared/ui/format.js';
import { PayslipDocument } from './PayslipDocument.jsx';

const { Title, Text } = Typography;

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
              Payslip for {formatMonth(run.period)}
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
        }}
      >
        <PayslipDocument payslip={payslip} testIdPrefix="public-payslip" />

        {payslip.link?.expires_at && (
          <div className="no-print" style={{ textAlign: 'center', marginTop: 12 }}>
            <Text type="secondary" style={{ fontSize: 11 }}>
              This public download link is valid until {formatDate(payslip.link.expires_at)}.
            </Text>
          </div>
        )}
      </div>
    </div>
  );
}

PublicPayslip.propTypes = {
  token: PropTypes.string,
};

export default PublicPayslip;
