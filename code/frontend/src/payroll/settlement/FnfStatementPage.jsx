import { useState, useEffect, useCallback } from 'react';
import { Card, Row, Col, Typography, Tag, Button, Space, Divider, Spin, message, Descriptions, theme } from 'antd';
import { ArrowLeftOutlined, CheckCircleOutlined, DollarOutlined, PrinterOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import { fnfService } from './fnfService.js';

const { Title, Text } = Typography;

export function FnfStatementPage() {
  const { token } = theme.useToken();
  const { id } = useParams();
  const navigate = useNavigate();
  const [settlement, setSettlement] = useState(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchSettlement = useCallback(async () => {
    try {
      setLoading(true);
      const data = await fnfService.getSettlement(id);
      setSettlement(data);
    } catch (err) {
      message.error('Failed to load settlement details');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) {
      fetchSettlement();
    }
  }, [id, fetchSettlement]);

  const handleApprove = async () => {
    try {
      setActionLoading(true);
      const updated = await fnfService.approveSettlement(id);
      message.success('Settlement approved successfully');
      setSettlement(updated);
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Approval failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handlePay = async () => {
    try {
      setActionLoading(true);
      const updated = await fnfService.paySettlement(id);
      message.success('Settlement marked as PAID');
      setSettlement(updated);
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Payment update failed');
    } finally {
      setActionLoading(false);
    }
  };

  const formatCurrency = (val) =>
    `₹${Number(val || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 60 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!settlement) {
    return (
      <div style={{ padding: 24 }}>
        <Card>
          <Text type="secondary">Settlement statement not found.</Text>
          <br />
          <Button style={{ marginTop: 16 }} onClick={() => navigate('/payroll/settlements')}>
            Back to Settlements
          </Button>
        </Card>
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      {/* Header & Actions */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/payroll/settlements')}>
            Back
          </Button>
          <Title level={3} style={{ margin: 0 }}>
            Full & Final Settlement Statement
          </Title>
        </Space>

        <Space>
          <Button icon={<PrinterOutlined />} onClick={() => window.print()}>
            Print Statement
          </Button>
          {(settlement.status === 'DRAFT' || settlement.status === 'UNDER_REVIEW') && (
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              loading={actionLoading}
              onClick={handleApprove}
            >
              Approve Settlement
            </Button>
          )}
          {settlement.status === 'APPROVED' && (
            <Button
              type="primary"
              style={{ backgroundColor: token.colorSuccess, borderColor: token.colorSuccess }}
              icon={<DollarOutlined />}
              loading={actionLoading}
              onClick={handlePay}
            >
              Mark as Paid
            </Button>
          )}
        </Space>
      </div>

      {/* Employee & Settlement Meta */}
      <Card style={{ marginBottom: 20 }}>
        <Descriptions title="Settlement Metadata" bordered column={{ xs: 1, sm: 2, md: 4 }}>
          <Descriptions.Item label="Employee ID">
            <span style={{ fontFamily: 'monospace' }}>{settlement.employeeId}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Status">
            <Tag color={settlement.status === 'PAID' ? 'green' : settlement.status === 'APPROVED' ? 'cyan' : 'blue'}>
              {settlement.status}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Resignation Date">{settlement.resignationDate || 'N/A'}</Descriptions.Item>
          <Descriptions.Item label="Exit Date">{settlement.exitDate}</Descriptions.Item>
          <Descriptions.Item label="Exit Reason">{settlement.exitReason || 'RESIGNATION'}</Descriptions.Item>
          <Descriptions.Item label="Working Days in Exit Month">
            {settlement.workingDaysInExitMonth} days
          </Descriptions.Item>
          <Descriptions.Item label="Approved By">{settlement.approvedBy || 'Pending'}</Descriptions.Item>
          <Descriptions.Item label="Paid Date">
            {settlement.paidAt ? new Date(settlement.paidAt).toLocaleDateString() : 'Pending'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* Earnings & Deductions Grid */}
      <Row gutter={24}>
        {/* Earnings Card */}
        <Col xs={24} md={12}>
          <Card
            title={<span style={{ color: token.colorSuccess }}>Gross Earnings (+)</span>}
            bordered
            style={{ marginBottom: 20 }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Pro-rated Exit Month Salary:</Text>
              <Text strong>{formatCurrency(settlement.exitMonthSalary)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Leave Encashment (W-90.1):</Text>
              <Text strong>{formatCurrency(settlement.leaveEncashmentAmount)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Gratuity (Payment of Gratuity Act):</Text>
              <Text strong>{formatCurrency(settlement.gratuityAmount)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Notice Pay Payout (W-90.2):</Text>
              <Text strong>{formatCurrency(settlement.noticePayAmount)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Other Ad-hoc Earnings:</Text>
              <Text strong>{formatCurrency(settlement.otherEarnings)}</Text>
            </div>
            <Divider style={{ margin: '12px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', fontSize: 16 }}>
              <Text strong style={{ color: token.colorSuccess }}>
                Total Gross Earnings:
              </Text>
              <Text strong style={{ color: token.colorSuccess }}>
                {formatCurrency(settlement.grossEarnings)}
              </Text>
            </div>
          </Card>
        </Col>

        {/* Deductions Card */}
        <Col xs={24} md={12}>
          <Card
            title={<span style={{ color: token.colorError }}>Gross Deductions & Recoveries (-)</span>}
            bordered
            style={{ marginBottom: 20 }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Notice Period Shortfall Recovery (W-90.2):</Text>
              <Text strong>{formatCurrency(settlement.noticeRecoveryAmount)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Employee Provident Fund (EPF):</Text>
              <Text strong>{formatCurrency(settlement.epfDeduction)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Employee State Insurance (ESI):</Text>
              <Text strong>{formatCurrency(settlement.esiDeduction)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Professional Tax (PT):</Text>
              <Text strong>{formatCurrency(settlement.ptDeduction)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Final Exit TDS:</Text>
              <Text strong>{formatCurrency(settlement.tdsDeduction)}</Text>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
              <Text>Other Deductions & Asset Recoveries:</Text>
              <Text strong>{formatCurrency(settlement.otherRecoveries)}</Text>
            </div>
            <Divider style={{ margin: '12px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', fontSize: 16 }}>
              <Text strong style={{ color: token.colorError }}>
                Total Gross Deductions:
              </Text>
              <Text strong style={{ color: token.colorError }}>
                {formatCurrency(settlement.grossDeductions)}
              </Text>
            </div>
          </Card>
        </Col>
      </Row>

      {/* Net Payable Summary Card */}
      <Card style={{ backgroundColor: token.colorSuccessBg, borderColor: token.colorSuccessBorder, textAlign: 'center' }}>
        <Text style={{ fontSize: 18, color: token.colorTextSecondary }}>Net Payable Amount (Earnings - Deductions)</Text>
        <div style={{ fontSize: 36, fontWeight: 'bold', color: token.colorSuccess, marginTop: 8 }}>
          {formatCurrency(settlement.netPayable)}
        </div>
        {settlement.remarks && (
          <div style={{ marginTop: 12 }}>
            <Text type="secondary">Notes: {settlement.remarks}</Text>
          </div>
        )}
      </Card>
    </div>
  );
}
