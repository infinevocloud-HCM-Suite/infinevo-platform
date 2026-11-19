import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Drawer,
  Card,
  Button,
  Typography,
  Space,
  Tag,
  Divider,
  Skeleton,
  Alert,
} from 'antd';
import {
  ShareAltOutlined,
  CloseOutlined,
  CheckCircleOutlined,
  CalendarOutlined,
  UserOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { MoneyText } from '../../shared/ui/MoneyText.jsx';
import { mobilePayslipService } from './mobilePayslipService.js';
import { successMsg, errorMsg } from '../../shared/ui/msgHelper.js';

const { Title, Text } = Typography;

function formatFullPeriod(period) {
  if (!period) return 'N/A';
  const text = String(period).trim();
  const d = dayjs(/^\d{4}-\d{2}$/.test(text) ? `${text}-01` : text);
  return d.isValid() ? d.format('MMMM YYYY') : text;
}

/**
 * Mobile Payslip Detail Sheet (W-98.4 §4.2)
 * Slide-up bottom sheet rendering itemized earnings, deductions,
 * attendance days, and 1-tap PDF sharing.
 */
export function MobilePayslipDetailSheet({
  open,
  onClose,
  payrunId,
  onShare = null,
}) {
  const [loading, setLoading] = useState(false);
  const [payslip, setPayslip] = useState(null);
  const [error, setError] = useState(null);
  const [sharing, setSharing] = useState(false);

  const fetchDetail = useCallback(async () => {
    if (!payrunId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await mobilePayslipService.getPayslipDetail(payrunId);
      setPayslip(data);
    } catch (err) {
      setError(err?.message || 'Failed to load payslip details');
    } finally {
      setLoading(false);
    }
  }, [payrunId]);

  useEffect(() => {
    if (open && payrunId) {
      fetchDetail();
    } else if (!open) {
      setPayslip(null);
      setError(null);
    }
  }, [open, payrunId, fetchDetail]);

  const handleShare = async () => {
    if (onShare) {
      onShare(payslip);
      return;
    }
    setSharing(true);
    try {
      await mobilePayslipService.downloadAndSharePayslip({
        payrunId,
        period: payslip?.run?.period,
      });
      successMsg('Payslip PDF ready to share');
    } catch (err) {
      errorMsg(err?.message || 'Failed to share payslip PDF');
    } finally {
      setSharing(false);
    }
  };

  const run = payslip?.run || {};
  const employer = payslip?.employer || {};
  const employee = payslip?.employee || {};
  const days = payslip?.days || {};
  const totals = payslip?.totals || {};
  const earnings = Array.isArray(payslip?.earnings) ? payslip.earnings : [];
  const deductions = Array.isArray(payslip?.deductions) ? payslip.deductions : [];
  const reimbursements = Array.isArray(payslip?.reimbursements) ? payslip.reimbursements : [];

  return (
    <Drawer
      open={open}
      onClose={onClose}
      placement="bottom"
      height="88%"
      closable={false}
      className="mobile-payslip-detail-drawer"
      data-testid="mobile-payslip-detail-sheet"
      styles={{
        body: { padding: '16px 20px', paddingBottom: 32 },
        header: { display: 'none' },
      }}
    >
      <div className="mobile-drawer-handle" aria-hidden="true" />

      {/* Top Bar with title and close */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0, fontWeight: 700 }}>
            {formatFullPeriod(run.period)}
          </Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {employer.name || 'Infinevo HCM'}
          </Text>
        </div>
        <Button
          type="text"
          shape="circle"
          icon={<CloseOutlined />}
          onClick={onClose}
          aria-label="Close"
        />
      </div>

      {loading && (
        <div data-testid="payslip-sheet-loading">
          <Skeleton active paragraph={{ rows: 8 }} />
        </div>
      )}

      {error && !loading && (
        <Alert
          type="error"
          showIcon
          message="Unable to load payslip"
          description={error}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" type="primary" onClick={fetchDetail}>
              Retry
            </Button>
          }
        />
      )}

      {!loading && !error && payslip && (
        <div className="mobile-payslip-sheet-content">
          {/* Employee & Run Info Card */}
          <Card
            size="small"
            style={{
              borderRadius: 12,
              marginBottom: 16,
              background: '#f8fafc',
              border: '1px solid #e2e8f0',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div
                style={{
                  width: 38,
                  height: 38,
                  borderRadius: '50%',
                  backgroundColor: '#e6f4ff',
                  color: '#1890ff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 600,
                }}
              >
                <UserOutlined />
              </div>
              <div style={{ flex: 1 }}>
                <Text strong style={{ fontSize: 14 }}>
                  {employee.name || 'Employee'}
                </Text>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    ID: {employee.number || 'N/A'} • {employee.designation || 'Staff'}
                  </Text>
                </div>
              </div>
              <Tag color="success" icon={<CheckCircleOutlined />}>
                Paid
              </Tag>
            </div>
          </Card>

          {/* Prominent Net Pay Card */}
          <Card
            style={{
              borderRadius: 14,
              marginBottom: 16,
              background: 'linear-gradient(135deg, #0958d9 0%, #1677ff 100%)',
              color: '#ffffff',
              boxShadow: '0 4px 12px rgba(22, 119, 255, 0.25)',
            }}
            styles={{ body: { padding: '16px 20px' } }}
          >
            <div style={{ color: 'rgba(255, 255, 255, 0.85)', fontSize: 12, marginBottom: 4 }}>
              NET TAKE-HOME PAY
            </div>
            <div
              style={{
                fontSize: 28,
                fontWeight: 700,
                color: '#ffffff',
                marginBottom: 8,
              }}
              data-testid="detail-net-pay"
            >
              <MoneyText value={totals.net_pay ?? 0} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'rgba(255, 255, 255, 0.9)' }}>
              <span>
                <CalendarOutlined style={{ marginRight: 4 }} />
                Paid on: {run.paid_on || run.pay_date || 'N/A'}
              </span>
              <span>
                Payable: {days.payable_days ?? days.paid_days ?? 0} days
              </span>
            </div>
          </Card>

          {/* Attendance Metrics */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: 8,
              marginBottom: 16,
              textAlign: 'center',
            }}
          >
            <div style={{ background: '#f8fafc', padding: '10px 4px', borderRadius: 10, border: '1px solid #e2e8f0' }}>
              <div style={{ fontSize: 11, color: '#64748b' }}>Payable Days</div>
              <div style={{ fontSize: 16, fontWeight: 700, color: '#0f172a' }}>
                {days.payable_days ?? days.paid_days ?? 0}
              </div>
            </div>
            <div style={{ background: '#f8fafc', padding: '10px 4px', borderRadius: 10, border: '1px solid #e2e8f0' }}>
              <div style={{ fontSize: 11, color: '#64748b' }}>Paid Days</div>
              <div style={{ fontSize: 16, fontWeight: 700, color: '#52c41a' }}>
                {days.paid_days ?? days.payable_days ?? 0}
              </div>
            </div>
            <div style={{ background: '#f8fafc', padding: '10px 4px', borderRadius: 10, border: '1px solid #e2e8f0' }}>
              <div style={{ fontSize: 11, color: '#64748b' }}>Loss of Pay</div>
              <div style={{ fontSize: 16, fontWeight: 700, color: '#cf1322' }}>
                {days.lop_days ?? 0}
              </div>
            </div>
          </div>

          {/* Earnings Breakdown */}
          <Card
            title={
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>Earnings</span>
                <span style={{ color: '#52c41a', fontWeight: 600 }}>
                  <MoneyText value={totals.gross_earnings ?? 0} />
                </span>
              </div>
            }
            size="small"
            style={{ borderRadius: 12, marginBottom: 16 }}
            data-testid="earnings-section"
          >
            {earnings.length === 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                No earnings components listed.
              </Text>
            )}
            {earnings.map((line, idx) => (
              <div
                key={line.code || line.name || idx}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '6px 0',
                  borderBottom: idx < earnings.length - 1 ? '1px solid #f0f0f0' : 'none',
                }}
              >
                <Text style={{ fontSize: 13 }}>{line.name || line.code}</Text>
                <Text strong style={{ fontSize: 13 }}>
                  <MoneyText value={line.amount ?? 0} />
                </Text>
              </div>
            ))}
          </Card>

          {/* Deductions Breakdown */}
          <Card
            title={
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>Deductions</span>
                <span style={{ color: '#cf1322', fontWeight: 600 }}>
                  <MoneyText value={totals.total_deductions ?? 0} />
                </span>
              </div>
            }
            size="small"
            style={{ borderRadius: 12, marginBottom: 16 }}
            data-testid="deductions-section"
          >
            {deductions.length === 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                No deduction components.
              </Text>
            )}
            {deductions.map((line, idx) => (
              <div
                key={line.code || line.name || idx}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '6px 0',
                  borderBottom: idx < deductions.length - 1 ? '1px solid #f0f0f0' : 'none',
                }}
              >
                <Text style={{ fontSize: 13 }}>{line.name || line.code}</Text>
                <Text strong style={{ fontSize: 13, color: '#cf1322' }}>
                  <MoneyText value={line.amount ?? 0} />
                </Text>
              </div>
            ))}
          </Card>

          {/* Reimbursements (if any) */}
          {reimbursements.length > 0 && (
            <Card
              title={
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>Reimbursements</span>
                  <span style={{ color: '#1890ff', fontWeight: 600 }}>
                    <MoneyText value={totals.total_reimbursements ?? 0} />
                  </span>
                </div>
              }
              size="small"
              style={{ borderRadius: 12, marginBottom: 16 }}
            >
              {reimbursements.map((line, idx) => (
                <div
                  key={line.code || line.name || idx}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    padding: '6px 0',
                    borderBottom: idx < reimbursements.length - 1 ? '1px solid #f0f0f0' : 'none',
                  }}
                >
                  <Text style={{ fontSize: 13 }}>{line.name || line.code}</Text>
                  <Text strong style={{ fontSize: 13 }}>
                    <MoneyText value={line.amount ?? 0} />
                  </Text>
                </div>
              ))}
            </Card>
          )}

          <Divider style={{ margin: '16px 0' }} />

          {/* Action buttons */}
          <Space direction="vertical" style={{ width: '100%' }}>
            <Button
              type="primary"
              icon={<ShareAltOutlined />}
              block
              size="large"
              loading={sharing}
              onClick={handleShare}
              style={{ borderRadius: 10, fontWeight: 600 }}
              data-testid="share-payslip-button"
            >
              Download / Share PDF
            </Button>
            <Button
              block
              size="large"
              onClick={onClose}
              style={{ borderRadius: 10 }}
            >
              Close
            </Button>
          </Space>
        </div>
      )}
    </Drawer>
  );
}

MobilePayslipDetailSheet.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  payrunId: PropTypes.string,
  onShare: PropTypes.func,
};
