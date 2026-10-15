import PropTypes from 'prop-types';
import { Card, Row, Col, Typography, Table, Tag, Space, Alert, Button, Statistic, theme } from 'antd';
import { PrinterOutlined, BankOutlined, UserOutlined, CheckCircleOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { formatAmount } from '../claims/claimLabels';

const { Title, Text } = Typography;

const QUARTER_PERIODS = {
  Q1: '1-Apr to 30-Jun',
  Q2: '1-Jul to 30-Sep',
  Q3: '1-Oct to 31-Dec',
  Q4: '1-Jan to 31-Mar',
};

/**
 * Mask PAN to standard format (e.g., ABCDE****F).
 * If already masked or invalid length, return as is.
 */
function maskPan(pan) {
  if (!pan || typeof pan !== 'string') return '-';
  const clean = pan.trim();
  if (clean.length === 10 && !clean.includes('*')) {
    return `${clean.slice(0, 5)}****${clean.slice(9)}`;
  }
  return clean;
}

/**
 * Component presenting the Form 16 Part B annual tax statement (W-36.4, W-47).
 * Conforms to Indian statutory format under Rule 31(1)(a) of the Income-tax Rules.
 */
export function Form16StatementView({ statement }) {
  const { token } = theme.useToken();

  if (!statement) {
    return null;
  }

  const {
    financialYear,
    assessmentYear,
    final: isFinal,
    deductor,
    employee,
    regime,
    quarters = [],
    totalDeducted = 0,
    annualTax = 0,
    balance = 0,
    breakdown,
    breakdownNote,
    generatedAt,
  } = statement;

  const handlePrint = () => {
    window.print();
  };

  const quarterColumns = [
    {
      title: 'Quarter',
      dataIndex: 'quarter',
      key: 'quarter',
      width: 120,
      render: (q) => <Text strong>{q}</Text>,
    },
    {
      title: 'Period',
      dataIndex: 'period',
      key: 'period',
      render: (_, record) => QUARTER_PERIODS[record.quarter] || record.quarter,
    },
    {
      title: 'TDS Deducted (₹)',
      dataIndex: 'amountDeducted',
      key: 'amountDeducted',
      align: 'right',
      render: (amt) => <Text strong>₹{formatAmount(amt)}</Text>,
    },
  ];

  const quarterData = (quarters || []).map((q, idx) => ({
    key: q.quarter || `q-${idx}`,
    quarter: q.quarter,
    amountDeducted: q.amountDeducted,
  }));

  const breakdownRows = breakdown
    ? [
        { key: '1', item: '1. Gross Total Income', amount: breakdown.grossTotalIncome, isDeduction: false },
        { key: '2', item: '2. Less: HRA Exemption (u/s 10(13A))', amount: breakdown.hraExemption, isDeduction: true },
        { key: '3', item: '3. Less: Standard Deduction (u/s 16(ia))', amount: breakdown.standardDeduction, isDeduction: true },
        { key: '4', item: '4. Less: Professional Tax (u/s 16(iii))', amount: breakdown.professionalTax, isDeduction: true },
        { key: '5', item: '5. Income / (Loss) from House Property', amount: breakdown.housePropertyIncome, isDeduction: false },
        { key: '6', item: '6. Income from Other Sources', amount: breakdown.otherIncome, isDeduction: false },
        { key: '7', item: '7. Less: Chapter VI-A Deductions', amount: breakdown.chapterVia, isDeduction: true },
        { key: '8', item: '8. Total Taxable Income', amount: breakdown.taxableIncome, isTotal: true },
        { key: '9', item: '9. Tax on Total Income', amount: breakdown.taxBeforeRebate, isDeduction: false },
        { key: '10', item: '10. Less: Rebate u/s 87A', amount: breakdown.rebate, isDeduction: true },
        { key: '11', item: '11. Surcharge', amount: breakdown.surcharge, isDeduction: false },
        { key: '12', item: '12. Health & Education Cess (4%)', amount: breakdown.cess, isDeduction: false },
        { key: '13', item: '13. Less: Previous Employer TDS', amount: breakdown.prevEmployerTds, isDeduction: true },
        { key: '14', item: '14. Total Annual Tax Payable', amount: breakdown.annualTax, isTotal: true },
      ]
    : [];

  const breakdownColumns = [
    {
      title: 'Tax Computation Item (Part B)',
      dataIndex: 'item',
      key: 'item',
      render: (text, record) => (
        <span style={{ fontWeight: record.isTotal ? 600 : 400 }}>{text}</span>
      ),
    },
    {
      title: 'Amount (₹)',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      width: 200,
      render: (amt, record) => (
        <span style={{ fontWeight: record.isTotal ? 600 : 400 }}>
          {record.isDeduction && amt > 0 ? `(-) ₹${formatAmount(amt)}` : `₹${formatAmount(amt)}`}
        </span>
      ),
    },
  ];

  const balanceNumber = Number(balance) || 0;

  return (
    <div className="form-16-statement-container" data-testid="form16-statement">
      <style>
        {`
          @media print {
            body * { visibility: hidden; }
            .form-16-statement-container, .form-16-statement-container * { visibility: visible; }
            .form-16-statement-container { position: absolute; left: 0; top: 0; width: 100%; padding: 0; }
            .no-print { display: none !important; }
          }
        `}
      </style>

      {/* Header and Print action */}
      <Card style={{ marginBottom: token.marginMD, borderRadius: token.borderRadiusLG }}>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col xs={24} md={18}>
            <Space orientation="vertical" size={2}>
              <div style={{ display: 'flex', alignItems: 'center', gap: token.marginXS, flexWrap: 'wrap' }}>
                <Title level={3} style={{ margin: 0 }}>FORM NO. 16</Title>
                <Tag color={isFinal ? 'success' : 'warning'} icon={isFinal ? <CheckCircleOutlined /> : <ClockCircleOutlined />}>
                  {isFinal ? 'FINAL' : 'PROVISIONAL'}
                </Tag>
                <Tag color="blue">{regime || 'NEW'} REGIME</Tag>
                <Tag color="cyan">FY {financialYear}</Tag>
                <Tag color="purple">AY {assessmentYear}</Tag>
              </div>
              <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                [See rule 31(1)(a)] Certificate under section 203 of the Income-tax Act, 1961 for tax deducted at source on salary
              </Text>
            </Space>
          </Col>
          <Col xs={24} md={6} style={{ textAlign: 'right' }} className="no-print">
            <Button icon={<PrinterOutlined />} onClick={handlePrint} type="primary" ghost>
              Print Statement
            </Button>
          </Col>
        </Row>
      </Card>

      {/* Entity Details (Employer & Employee) */}
      <Row gutter={[16, 16]} style={{ marginBottom: token.marginMD }}>
        <Col xs={24} md={12}>
          <Card
            title={
              <Space>
                <BankOutlined />
                <span>Employer / Deductor Details</span>
              </Space>
            }
            bordered
            size="small"
            style={{ height: '100%', borderRadius: token.borderRadiusLG }}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: token.marginXS }}>
              <div><Text strong>Name:</Text> <Text>{deductor?.name || '-'}</Text></div>
              <div><Text strong>TAN:</Text> <Text code>{deductor?.tan || '-'}</Text></div>
              <div><Text strong>PAN:</Text> <Text code>{deductor?.pan || '-'}</Text></div>
              <div><Text strong>TDS Circle:</Text> <Text>{deductor?.tdsCircle || '-'}</Text></div>
              {deductor?.signatoryName && (
                <div>
                  <Text strong>Signatory:</Text>{' '}
                  <Text>{deductor.signatoryName} {deductor.signatoryDesignation ? `(${deductor.signatoryDesignation})` : ''}</Text>
                </div>
              )}
            </div>
          </Card>
        </Col>

        <Col xs={24} md={12}>
          <Card
            title={
              <Space>
                <UserOutlined />
                <span>Employee / Deductee Details</span>
              </Space>
            }
            bordered
            size="small"
            style={{ height: '100%', borderRadius: token.borderRadiusLG }}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: token.marginXS }}>
              <div><Text strong>Name:</Text> <Text>{employee?.name || '-'}</Text></div>
              <div><Text strong>PAN:</Text> <Text code>{maskPan(employee?.pan)}</Text></div>
              <div><Text strong>Designation:</Text> <Text>{employee?.designation || '-'}</Text></div>
              <div><Text strong>Tax Regime:</Text> <Tag color="blue">{regime || 'NEW'}</Tag></div>
              {generatedAt && (
                <div><Text strong>Generated On:</Text> <Text type="secondary">{new Date(generatedAt).toLocaleDateString()}</Text></div>
              )}
            </div>
          </Card>
        </Col>
      </Row>

      {/* Summary Metrics */}
      <Row gutter={[16, 16]} style={{ marginBottom: token.marginMD }}>
        <Col xs={24} sm={8}>
          <Card bordered size="small" style={{ borderRadius: token.borderRadiusLG, background: token.colorFillAlter }}>
            <Statistic
              title="Annual Tax Payable"
              value={`₹${formatAmount(annualTax)}`}
              styles={{ content: { color: token.colorTextHeading } }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered size="small" style={{ borderRadius: token.borderRadiusLG, background: token.colorFillAlter }}>
            <Statistic
              title="Total TDS Deducted"
              value={`₹${formatAmount(totalDeducted)}`}
              styles={{ content: { color: token.colorPrimary } }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered size="small" style={{ borderRadius: token.borderRadiusLG, background: token.colorFillAlter }}>
            <Statistic
              title={balanceNumber > 0 ? 'Balance Tax Payable' : balanceNumber < 0 ? 'Tax Refundable' : 'Balance Payable'}
              value={`₹${formatAmount(Math.abs(balanceNumber))}`}
              styles={{ content: { color: balanceNumber > 0 ? token.colorError : balanceNumber < 0 ? token.colorSuccess : token.colorTextSecondary } }}
            />
          </Card>
        </Col>
      </Row>

      {/* Quarterly TDS Deduction Summary */}
      <Card
        title="Summary of Tax Deducted at Source (Quarterly)"
        style={{ marginBottom: token.marginMD, borderRadius: token.borderRadiusLG }}
        bordered
      >
        <Table
          columns={quarterColumns}
          dataSource={quarterData}
          pagination={false}
          size="middle"
          summary={() => (
            <Table.Summary fixed>
              <Table.Summary.Row style={{ background: token.colorFillAlter }}>
                <Table.Summary.Cell index={0} colSpan={2}>
                  <Text strong>Total Tax Deducted</Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  <Text strong style={{ color: token.colorPrimary }}>₹{formatAmount(totalDeducted)}</Text>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )}
        />
      </Card>

      {/* Part B Statutory Breakdown */}
      <Card
        title="Part B — Details of Salary Paid and Tax Deducted"
        style={{ borderRadius: token.borderRadiusLG }}
        bordered
      >
        {breakdownNote === 'OFFICER_OVERRIDE' && (
          <Alert
            type="warning"
            showIcon
            message="Officer Adjustment"
            description="The annual tax computation was manually adjusted by a payroll officer. Standard breakdown values may differ from the net tax payable."
            style={{ marginBottom: token.marginMD }}
          />
        )}

        {breakdownRows.length > 0 ? (
          <Table
            columns={breakdownColumns}
            dataSource={breakdownRows}
            pagination={false}
            size="middle"
            rowClassName={(record) => (record.isTotal ? 'form-16-total-row' : '')}
          />
        ) : (
          <Text type="secondary">Detailed calculation breakdown is not available for this record.</Text>
        )}
      </Card>
    </div>
  );
}

Form16StatementView.propTypes = {
  statement: PropTypes.shape({
    financialYear: PropTypes.string,
    assessmentYear: PropTypes.string,
    final: PropTypes.bool,
    regime: PropTypes.string,
    totalDeducted: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    annualTax: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    balance: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    breakdownNote: PropTypes.string,
    generatedAt: PropTypes.string,
    deductor: PropTypes.shape({
      name: PropTypes.string,
      tan: PropTypes.string,
      pan: PropTypes.string,
      tdsCircle: PropTypes.string,
      signatoryName: PropTypes.string,
      signatoryDesignation: PropTypes.string,
    }),
    employee: PropTypes.shape({
      name: PropTypes.string,
      pan: PropTypes.string,
      designation: PropTypes.string,
    }),
    quarters: PropTypes.arrayOf(
      PropTypes.shape({
        quarter: PropTypes.string,
        amountDeducted: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
      })
    ),
    breakdown: PropTypes.object,
  }),
};
