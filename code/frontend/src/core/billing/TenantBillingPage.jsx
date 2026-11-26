import { useEffect, useState, useCallback } from 'react';
import { Card, Table, Tag, Button, Space, Typography, Row, Col, Skeleton, Drawer, Descriptions, Badge, Empty, theme } from 'antd';
import { DownloadOutlined, EyeOutlined, CheckCircleOutlined, ExclamationCircleOutlined, RocketOutlined } from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan, NotEntitled } from '@shell/screens';
import { formatMoney, formatDate, statusTag } from '@shared/ui/format.js';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';
import { billingService } from './billingService.js';
import { employeeService } from '../employee/employeeService.js';
import { tenantService } from '../admin/tenantService.js';
import { PLAN_LABELS, SUPPORT_PLAN_LABELS, employeesText, overLimit } from '../admin/platform.js';
import { useSelector } from 'react-redux';

const { Text, Title } = Typography;

/**
 * Tenant Admin billing dashboard at `/settings/billing` (W-94.2).
 * Shows current subscription plan, support tier, active headcount vs limit,
 * and history of GST tax invoices with PDF download.
 */
export function TenantBillingPage() {
  const { token } = theme.useToken();
  const canTenantRead = useCan('core.tenant.read');
  const canTenantView = useCan('core.tenant.view');
  const canRead = canTenantRead || canTenantView;
  const impersonationTenantId = useSelector((state) => state.impersonation?.session?.tenantId);

  const [loading, setLoading] = useState(true);
  const [invoices, setInvoices] = useState([]);
  const [subscription, setSubscription] = useState(null);
  const [limitData, setLimitData] = useState({ limit: null, used: 0 });
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [downloadingId, setDownloadingId] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      // 1. Fetch invoices
      const invList = await billingService.listInvoices().catch((err) => {
        console.error('Failed to load invoices', err);
        return [];
      });
      const resolvedInvoices = Array.isArray(invList) ? invList : [];
      setInvoices(resolvedInvoices);

      // 2. Fetch headcount vs limit
      const lim = await employeeService.limit().catch(() => ({ limit: null, used: 0 }));
      setLimitData(lim || { limit: null, used: 0 });

      // 3. Resolve tenantId and fetch subscription if available
      const tenantId = impersonationTenantId || resolvedInvoices[0]?.tenantId;
      if (tenantId) {
        const sub = await tenantService.subscription(tenantId).catch(() => null);
        if (sub) setSubscription(sub);
      }
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [impersonationTenantId]);

  useEffect(() => {
    if (canRead) {
      loadData();
    }
  }, [canRead, loadData]);

  if (!canRead) {
    return <NotEntitled />;
  }

  const handleDownloadPdf = async (record) => {
    setDownloadingId(record.id);
    try {
      await billingService.downloadInvoicePdf(record);
      successMsg('Invoice downloaded', `Tax invoice ${record.invoiceNumber} downloaded.`);
    } catch (err) {
      errorMsg(err);
    } finally {
      setDownloadingId(null);
    }
  };

  const handleViewInvoice = (record) => {
    setSelectedInvoice(record);
    setDrawerOpen(true);
  };

  // Determine active plan values
  const latestInvoice = invoices[0];
  const activePlanKey = subscription?.plan || latestInvoice?.planTier || 'STARTER';
  const planLabel = PLAN_LABELS[activePlanKey] || activePlanKey;
  const supportPlanKey = subscription?.supportPlan || 'STANDARD';
  const supportLabel = SUPPORT_PLAN_LABELS[supportPlanKey] || supportPlanKey;
  const activeCount = limitData.used ?? latestInvoice?.activeHeadcount ?? 0;
  const empLimit = limitData.limit ?? subscription?.employeeLimit ?? null;
  const isOverLimit = overLimit(activeCount, empLimit);

  const columns = [
    {
      title: 'Invoice Number',
      dataIndex: 'invoiceNumber',
      key: 'invoiceNumber',
      render: (num, record) => (
        <a onClick={() => handleViewInvoice(record)} style={{ fontWeight: 600, fontFamily: 'monospace' }}>
          {num}
        </a>
      ),
    },
    {
      title: 'Billing Period',
      key: 'billingPeriod',
      render: (_, record) => (
        <Text>
          {formatDate(record.billingPeriodStart)} – {formatDate(record.billingPeriodEnd)}
        </Text>
      ),
    },
    {
      title: 'Amount',
      dataIndex: 'taxableAmount',
      key: 'taxableAmount',
      align: 'right',
      render: (amt, record) => <Text>{formatMoney(amt != null ? amt : record.subtotal)}</Text>,
    },
    {
      title: 'GST Tax',
      dataIndex: 'totalTax',
      key: 'totalTax',
      align: 'right',
      render: (tax) => <Text>{formatMoney(tax)}</Text>,
    },
    {
      title: 'Total',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      align: 'right',
      render: (total) => <Text strong>{formatMoney(total)}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (st) => {
        const { label, color } = statusTag(st);
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: 'Action',
      key: 'action',
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="primary"
            size="small"
            icon={<DownloadOutlined />}
            loading={downloadingId === record.id}
            onClick={() => handleDownloadPdf(record)}
          >
            Download PDF
          </Button>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewInvoice(record)}
          >
            View
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card variant="borderless">
      <PageHeader
        title="Subscription & Billing"
        backLabel="Settings"
        onBack={() => window.history.back()}
      />

      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <Space direction="vertical" size="large" style={{ display: 'flex', width: '100%' }}>
          {/* Current Plan Overview Card */}
          <Card title="Current Subscription Plan" variant="outlined" style={{ background: token.colorFillAlter }}>
            <Row gutter={[24, 24]}>
              <Col xs={24} sm={8}>
                <Space direction="vertical" size={2}>
                  <Text type="secondary">Plan Tier</Text>
                  <Space>
                    <RocketOutlined style={{ color: token.colorPrimary, fontSize: 18 }} />
                    <Title level={4} style={{ margin: 0 }}>
                      {planLabel}
                    </Title>
                  </Space>
                </Space>
              </Col>
              <Col xs={24} sm={8}>
                <Space direction="vertical" size={2}>
                  <Text type="secondary">Support Plan</Text>
                  <Space>
                    <CheckCircleOutlined style={{ color: token.colorSuccess, fontSize: 18 }} />
                    <Title level={4} style={{ margin: 0 }}>
                      {supportLabel}
                    </Title>
                  </Space>
                </Space>
              </Col>
              <Col xs={24} sm={8}>
                <Space direction="vertical" size={2}>
                  <Text type="secondary">Active Headcount / Limit</Text>
                  <Space>
                    <Badge
                      count={employeesText(activeCount, empLimit)}
                      style={{
                        backgroundColor: isOverLimit ? token.colorError : token.colorSuccess,
                        fontWeight: 600,
                        fontSize: 14,
                        padding: '0 8px',
                      }}
                    />
                    {isOverLimit && (
                      <Tag color="error" icon={<ExclamationCircleOutlined />}>
                        Limit Exceeded
                      </Tag>
                    )}
                  </Space>
                </Space>
              </Col>
            </Row>
          </Card>

          {/* Invoices Table */}
          <Card title="GST Tax Invoices" variant="outlined">
            {invoices.length === 0 ? (
              <Empty description="No invoices issued yet." />
            ) : (
              <Table
                rowKey="id"
                dataSource={invoices}
                columns={columns}
                pagination={{ pageSize: 10, showSizeChanger: false }}
              />
            )}
          </Card>
        </Space>
      )}

      {/* Invoice Details Drawer */}
      <Drawer
        title={selectedInvoice ? `Tax Invoice ${selectedInvoice.invoiceNumber}` : 'Invoice Details'}
        placement="right"
        width={600}
        onClose={() => setDrawerOpen(false)}
        open={drawerOpen}
        extra={
          selectedInvoice && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => handleDownloadPdf(selectedInvoice)}
            >
              Download PDF
            </Button>
          )
        }
      >
        {selectedInvoice && (
          <Space direction="vertical" size="middle" style={{ display: 'flex', width: '100%' }}>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="Invoice Number" span={2}>
                <Text strong code>{selectedInvoice.invoiceNumber}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Invoice Date">
                {formatDate(selectedInvoice.invoiceDate)}
              </Descriptions.Item>
              <Descriptions.Item label="Due Date">
                {formatDate(selectedInvoice.dueDate)}
              </Descriptions.Item>
              <Descriptions.Item label="Billing Period" span={2}>
                {formatDate(selectedInvoice.billingPeriodStart)} – {formatDate(selectedInvoice.billingPeriodEnd)}
              </Descriptions.Item>
              <Descriptions.Item label="Plan Tier">
                {selectedInvoice.planTier} ({selectedInvoice.billingFrequency})
              </Descriptions.Item>
              <Descriptions.Item label="Active Headcount">
                {selectedInvoice.activeHeadcount} employees
              </Descriptions.Item>
              <Descriptions.Item label="SAC Code">
                {selectedInvoice.sacCode || '997331'}
              </Descriptions.Item>
              <Descriptions.Item label="Customer GSTIN">
                {selectedInvoice.customerGstin || 'Unregistered / B2C'}
              </Descriptions.Item>
              <Descriptions.Item label="Status" span={2}>
                {(() => {
                  const { label, color } = statusTag(selectedInvoice.status);
                  return <Tag color={color}>{label}</Tag>;
                })()}
              </Descriptions.Item>
            </Descriptions>

            <Title level={5}>Line Items</Title>
            <Table
              size="small"
              rowKey={(r, idx) => idx}
              dataSource={selectedInvoice.items || [
                {
                  description: `${selectedInvoice.planTier} Subscription (${selectedInvoice.billingFrequency})`,
                  quantity: selectedInvoice.activeHeadcount,
                  unitPrice: selectedInvoice.taxableAmount,
                  amount: selectedInvoice.taxableAmount,
                },
              ]}
              pagination={false}
              columns={[
                { title: 'Description', dataIndex: 'description', key: 'description' },
                { title: 'Qty', dataIndex: 'quantity', key: 'quantity', width: 60, align: 'center' },
                {
                  title: 'Rate',
                  dataIndex: 'unitPrice',
                  key: 'unitPrice',
                  width: 90,
                  align: 'right',
                  render: (v) => formatMoney(v),
                },
                {
                  title: 'Amount',
                  dataIndex: 'amount',
                  key: 'amount',
                  width: 100,
                  align: 'right',
                  render: (v) => formatMoney(v),
                },
              ]}
            />

            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="Subtotal">
                {formatMoney(selectedInvoice.subtotal)}
              </Descriptions.Item>
              {Number(selectedInvoice.discountAmount) > 0 && (
                <Descriptions.Item label="Discount">
                  - {formatMoney(selectedInvoice.discountAmount)}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="Taxable Amount">
                {formatMoney(selectedInvoice.taxableAmount)}
              </Descriptions.Item>
              {Number(selectedInvoice.cgstAmount) > 0 && (
                <Descriptions.Item label="CGST (9%)">
                  {formatMoney(selectedInvoice.cgstAmount)}
                </Descriptions.Item>
              )}
              {Number(selectedInvoice.sgstAmount) > 0 && (
                <Descriptions.Item label="SGST (9%)">
                  {formatMoney(selectedInvoice.sgstAmount)}
                </Descriptions.Item>
              )}
              {Number(selectedInvoice.igstAmount) > 0 && (
                <Descriptions.Item label="IGST (18%)">
                  {formatMoney(selectedInvoice.igstAmount)}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="Total GST Tax">
                {formatMoney(selectedInvoice.totalTax)}
              </Descriptions.Item>
              <Descriptions.Item label="Total Amount">
                <Text strong style={{ fontSize: 16 }}>{formatMoney(selectedInvoice.totalAmount)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Amount Paid">
                {formatMoney(selectedInvoice.amountPaid)}
              </Descriptions.Item>
              <Descriptions.Item label="Balance Due">
                <Text type="danger" strong>
                  {formatMoney(Math.max(0, (selectedInvoice.totalAmount || 0) - (selectedInvoice.amountPaid || 0)))}
                </Text>
              </Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </Drawer>
    </Card>
  );
}
