import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Table, Tag, Button, Space, Typography, Row, Col, Statistic, Empty, Skeleton, Alert, theme } from 'antd';
import { PlusOutlined, DollarCircleOutlined, DownloadOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { formatMoney, formatDate, humanize, statusTag } from '@shared/ui/format.js';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { billingService } from '../billing/billingService.js';
import { RecordPaymentModal } from './RecordPaymentModal.jsx';
import { GenerateInvoiceModal } from './GenerateInvoiceModal.jsx';

const { Text } = Typography;

/**
 * Platform Admin billing tab inside TenantDetail.jsx (W-94.2).
 * Allows platform staff to view tenant invoices, generate new GST invoices,
 * and record customer payments (UTR, payment mode, date).
 */
export function PlatformBillingTab({ tenant, onUpdate }) {
  const { token } = theme.useToken();
  const canManageBilling = useCan('core.billing.manage');

  const [loading, setLoading] = useState(true);
  const [invoices, setInvoices] = useState([]);
  const [recordPaymentModalOpen, setRecordPaymentModalOpen] = useState(false);
  const [generateModalOpen, setGenerateModalOpen] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [downloadingId, setDownloadingId] = useState(null);

  const loadInvoices = useCallback(async () => {
    if (!tenant?.tenant_id) return;
    setLoading(true);
    try {
      const res = await billingService.listInvoices(
        { tenantId: tenant.tenant_id },
        { skipImpersonation: true }
      );
      setInvoices(Array.isArray(res) ? res : []);
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [tenant?.tenant_id]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  const handleOpenRecordPayment = (invoice) => {
    setSelectedInvoice(invoice);
    setRecordPaymentModalOpen(true);
  };

  const handleDownloadPdf = async (record) => {
    setDownloadingId(record.id);
    try {
      await billingService.downloadInvoicePdf(record);
    } catch (err) {
      errorMsg(err);
    } finally {
      setDownloadingId(null);
    }
  };

  const handlePaymentSuccess = () => {
    loadInvoices();
    if (onUpdate) onUpdate();
  };

  const handleGenerateSuccess = () => {
    loadInvoices();
    if (onUpdate) onUpdate();
  };

  // Metrics computation
  const totalBilled = invoices.reduce((sum, inv) => sum + Number(inv.totalAmount || 0), 0);
  const totalCollected = invoices.reduce((sum, inv) => sum + Number(inv.amountPaid || 0), 0);
  const totalOutstanding = Math.max(0, totalBilled - totalCollected);

  const columns = [
    {
      title: 'Invoice Number',
      dataIndex: 'invoiceNumber',
      key: 'invoiceNumber',
      render: (num) => <Text strong code>{num}</Text>,
    },
    {
      title: 'Invoice Date',
      dataIndex: 'invoiceDate',
      key: 'invoiceDate',
      render: (d) => formatDate(d),
    },
    {
      title: 'Due Date',
      dataIndex: 'dueDate',
      key: 'dueDate',
      render: (d) => formatDate(d),
    },
    {
      title: 'Billing Period',
      key: 'billingPeriod',
      render: (_, r) => (
        <Text>
          {formatDate(r.billingPeriodStart)} – {formatDate(r.billingPeriodEnd)}
        </Text>
      ),
    },
    {
      title: 'Frequency',
      dataIndex: 'billingFrequency',
      key: 'billingFrequency',
      render: (f) => humanize(f),
    },
    {
      title: 'Taxable Amount',
      dataIndex: 'taxableAmount',
      key: 'taxableAmount',
      align: 'right',
      render: (amt, r) => formatMoney(amt != null ? amt : r.subtotal),
    },
    {
      title: 'GST Tax',
      dataIndex: 'totalTax',
      key: 'totalTax',
      align: 'right',
      render: (tax) => formatMoney(tax),
    },
    {
      title: 'Total Amount',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      align: 'right',
      render: (t) => <Text strong>{formatMoney(t)}</Text>,
    },
    {
      title: 'Amount Paid',
      dataIndex: 'amountPaid',
      key: 'amountPaid',
      align: 'right',
      render: (p) => formatMoney(p),
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
      title: 'Actions',
      key: 'actions',
      align: 'center',
      render: (_, record) => {
        const isSettled = record.status === 'PAID' || record.status === 'CANCELLED';
        return (
          <Space size="small">
            <Button
              size="small"
              type="primary"
              icon={<DollarCircleOutlined />}
              disabled={!canManageBilling || isSettled}
              onClick={() => handleOpenRecordPayment(record)}
            >
              Record Payment
            </Button>
            <Button
              size="small"
              icon={<DownloadOutlined />}
              loading={downloadingId === record.id}
              onClick={() => handleDownloadPdf(record)}
            >
              Download PDF
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%', marginTop: 16 }}>
      {!canManageBilling && (
        <Alert
          type="warning"
          showIcon
          message="Read-only view. Generating invoices and recording payments requires core.billing.manage in the platform tenant."
        />
      )}

      {/* Summary Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}>
          <Card variant="outlined">
            <Statistic title="Invoices Issued" value={invoices.length} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="outlined">
            <Statistic title="Total Billed" value={formatMoney(totalBilled)} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="outlined">
            <Statistic
              title="Total Collected"
              value={formatMoney(totalCollected)}
              valueStyle={{ color: token.colorSuccess }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="outlined">
            <Statistic
              title="Outstanding Balance"
              value={formatMoney(totalOutstanding)}
              valueStyle={{ color: totalOutstanding > 0 ? token.colorError : token.colorSuccess }}
            />
          </Card>
        </Col>
      </Row>

      {/* Invoices List Card */}
      <Card
        title="Tenant Invoices"
        variant="outlined"
        extra={
          canManageBilling && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setGenerateModalOpen(true)}
            >
              Generate Invoice
            </Button>
          )
        }
      >
        {loading ? (
          <Skeleton active paragraph={{ rows: 5 }} />
        ) : invoices.length === 0 ? (
          <Empty description="No invoices found for this tenant." />
        ) : (
          <Table
            rowKey="id"
            dataSource={invoices}
            columns={columns}
            pagination={{ pageSize: 10 }}
          />
        )}
      </Card>

      {/* Modals */}
      <RecordPaymentModal
        open={recordPaymentModalOpen}
        onClose={() => setRecordPaymentModalOpen(false)}
        invoice={selectedInvoice}
        onSuccess={handlePaymentSuccess}
      />

      <GenerateInvoiceModal
        open={generateModalOpen}
        onClose={() => setGenerateModalOpen(false)}
        tenant={tenant}
        onSuccess={handleGenerateSuccess}
      />
    </Space>
  );
}

PlatformBillingTab.propTypes = {
  tenant: PropTypes.shape({
    tenant_id: PropTypes.string,
    name: PropTypes.string,
    status: PropTypes.string,
  }),
  onUpdate: PropTypes.func,
};
