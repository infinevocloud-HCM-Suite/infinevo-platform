import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Button,
  Tag,
  Typography,
  Space,
  Empty,
  Spin,
  Alert,
} from 'antd';
import {
  ReloadOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  DownloadOutlined,
  EyeOutlined,
  RightOutlined,
  FilePdfOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { MoneyText } from '../../shared/ui/MoneyText.jsx';
import { mobilePayslipService } from './mobilePayslipService.js';
import { MobilePayslipDetailSheet } from './MobilePayslipDetailSheet.jsx';
import { successMsg, errorMsg } from '../../shared/ui/msgHelper.js';

const { Title, Text } = Typography;

function formatMonthTitle(period) {
  if (!period) return 'N/A';
  const text = String(period).trim();
  const d = dayjs(/^\d{4}-\d{2}$/.test(text) ? `${text}-01` : text);
  return d.isValid() ? d.format('MMMM YYYY') : text;
}

function formatDate(date) {
  if (!date) return 'N/A';
  const d = dayjs(date);
  return d.isValid() ? d.format('DD MMM YYYY') : String(date);
}

/**
 * Mobile Payslips Screen (W-98.4 §4.2)
 * Clean touch-first dashboard featuring:
 * 1. Latest payslip showcase banner with 1-tap download & details.
 * 2. Chronological monthly history list with itemized drawers.
 * 3. Pull-to-refresh and native PDF export.
 */
export function MobilePayslipsScreen() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [payslips, setPayslips] = useState([]);
  const [latest, setLatest] = useState(null);
  const [errorBanner, setErrorBanner] = useState(null);

  // Selected payslip for bottom detail sheet
  const [selectedPayrunId, setSelectedPayrunId] = useState(null);
  const [detailSheetOpen, setDetailSheetOpen] = useState(false);
  const [downloadingId, setDownloadingId] = useState(null);

  const loadData = useCallback(async () => {
    try {
      setErrorBanner(null);
      const res = await mobilePayslipService.getPayslips(0, 24);
      setPayslips(res.rows || []);
      setLatest(res.latest || null);
    } catch (err) {
      setErrorBanner(err?.message || 'Failed to load payslips');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRefresh = () => {
    setRefreshing(true);
    loadData();
  };

  const handleOpenDetail = (payrunId) => {
    setSelectedPayrunId(payrunId);
    setDetailSheetOpen(true);
  };

  const handleDownloadAndShare = async (e, payslipItem) => {
    e.stopPropagation();
    const id = payslipItem.payrunId;
    setDownloadingId(id);
    try {
      await mobilePayslipService.downloadAndSharePayslip({
        payrunId: id,
        period: payslipItem.period,
      });
      successMsg('Payslip ready to view & share');
    } catch (err) {
      errorMsg(err?.message || 'Failed to download payslip');
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <div className="mobile-payslips-container" data-testid="mobile-payslips-screen">
      {/* Screen Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0, fontWeight: 700 }}>
            My Payslips
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Official Monthly Salary Slips
          </Text>
        </div>
        <Button
          type="text"
          shape="circle"
          icon={<ReloadOutlined spin={refreshing} />}
          onClick={handleRefresh}
          aria-label="Refresh payslips"
        />
      </div>

      {errorBanner && (
        <Alert
          type="error"
          showIcon
          message={errorBanner}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" type="primary" onClick={handleRefresh}>
              Retry
            </Button>
          }
        />
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }} data-testid="payslips-loading">
          <Spin size="large" />
          <div style={{ marginTop: 12, color: '#64748b', fontSize: 13 }}>Loading payslips...</div>
        </div>
      ) : payslips.length === 0 ? (
        <Card style={{ borderRadius: 14, textAlign: 'center', padding: '40px 16px' }}>
          <Empty
            description={
              <div>
                <Text strong style={{ fontSize: 15, display: 'block', marginBottom: 4 }}>
                  No Payslips Published Yet
                </Text>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Your monthly salary slips will appear here as soon as approved and disbursed.
                </Text>
              </div>
            }
          />
        </Card>
      ) : (
        <>
          {/* Latest Payslip Showcase Card */}
          {latest && (
            <Card
              className="mobile-payslip-showcase"
              data-testid="latest-payslip-card"
              style={{
                borderRadius: 16,
                marginBottom: 20,
                background: 'linear-gradient(135deg, #1890ff 0%, #096dd9 100%)',
                color: '#ffffff',
                boxShadow: '0 6px 16px rgba(24, 144, 255, 0.25)',
                border: 'none',
              }}
              styles={{ body: { padding: '20px' } }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <Tag
                  color="#ffffff"
                  style={{
                    color: '#096dd9',
                    fontWeight: 700,
                    borderRadius: 6,
                    padding: '2px 8px',
                    border: 'none',
                  }}
                >
                  LATEST PAYSLIP
                </Tag>
                <Tag
                  color="success"
                  icon={<CheckCircleOutlined />}
                  style={{ borderRadius: 6, border: 'none' }}
                >
                  Paid
                </Tag>
              </div>

              <div style={{ fontSize: 20, fontWeight: 700, color: '#ffffff', marginBottom: 4 }}>
                {formatMonthTitle(latest.period)}
              </div>

              <div style={{ fontSize: 12, color: 'rgba(255, 255, 255, 0.85)', marginBottom: 12 }}>
                <CalendarOutlined style={{ marginRight: 6 }} />
                Disbursed on: {formatDate(latest.paidOn)}
              </div>

              <div style={{ marginBottom: 16 }}>
                <div style={{ fontSize: 11, color: 'rgba(255, 255, 255, 0.8)', textTransform: 'uppercase' }}>
                  Net Take-Home Pay
                </div>
                <div
                  style={{ fontSize: 32, fontWeight: 800, color: '#ffffff' }}
                  data-testid="latest-net-pay"
                >
                  <MoneyText value={latest.netPay} />
                </div>
              </div>

              <div style={{ display: 'flex', gap: 10 }}>
                <Button
                  type="default"
                  icon={<EyeOutlined />}
                  style={{
                    flex: 1,
                    borderRadius: 8,
                    fontWeight: 600,
                    background: 'rgba(255, 255, 255, 0.15)',
                    color: '#ffffff',
                    border: '1px solid rgba(255, 255, 255, 0.3)',
                  }}
                  onClick={() => handleOpenDetail(latest.payrunId)}
                  data-testid="view-latest-btn"
                >
                  Breakdown
                </Button>
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  style={{
                    flex: 1,
                    borderRadius: 8,
                    fontWeight: 600,
                    backgroundColor: '#ffffff',
                    color: '#096dd9',
                    border: 'none',
                  }}
                  loading={downloadingId === latest.payrunId}
                  onClick={(e) => handleDownloadAndShare(e, latest)}
                  data-testid="download-latest-btn"
                >
                  PDF
                </Button>
              </div>
            </Card>
          )}

          {/* Past Months History List */}
          <div style={{ marginBottom: 12 }}>
            <Title level={5} style={{ margin: 0, fontWeight: 600, color: '#1e293b' }}>
              Past Payslips ({payslips.length})
            </Title>
          </div>

          <div className="mobile-payslips-history-list" data-testid="payslips-history-list">
            <Space direction="vertical" style={{ width: '100%' }} size={10}>
              {payslips.map((item) => (
                <Card
                  key={item.payrunId}
                  className="mobile-payslip-history-card"
                  hoverable
                  style={{
                    borderRadius: 12,
                    border: '1px solid #e2e8f0',
                    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)',
                  }}
                  styles={{ body: { padding: '14px 16px' } }}
                  onClick={() => handleOpenDetail(item.payrunId)}
                  data-testid={`payslip-item-${item.payrunId}`}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <div
                        style={{
                          width: 40,
                          height: 40,
                          borderRadius: 10,
                          backgroundColor: '#f0f5ff',
                          color: '#2f54eb',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 18,
                        }}
                      >
                        <FilePdfOutlined />
                      </div>
                      <div>
                        <Text strong style={{ fontSize: 14, display: 'block', color: '#0f172a' }}>
                          {formatMonthTitle(item.period)}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          Paid: {formatDate(item.paidOn)}
                        </Text>
                      </div>
                    </div>

                    <div style={{ textAlign: 'right', display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div>
                        <div style={{ fontSize: 15, fontWeight: 700, color: '#0f172a' }}>
                          <MoneyText value={item.netPay} />
                        </div>
                        <Tag color="success" style={{ fontSize: 10, borderRadius: 4, margin: 0 }}>
                          Paid
                        </Tag>
                      </div>
                      <RightOutlined style={{ color: '#94a3b8', fontSize: 12 }} />
                    </div>
                  </div>
                </Card>
              ))}
            </Space>
          </div>
        </>
      )}

      {/* Slide-up detailed salary breakdown drawer */}
      <MobilePayslipDetailSheet
        open={detailSheetOpen}
        onClose={() => setDetailSheetOpen(false)}
        payrunId={selectedPayrunId}
      />
    </div>
  );
}
