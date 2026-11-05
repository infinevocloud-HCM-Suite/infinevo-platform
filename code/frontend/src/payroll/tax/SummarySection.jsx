import { useState, useEffect, useCallback, useRef } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
import {
  Card,
  Row,
  Col,
  Statistic,
  Typography,
  Table,
  Tag,
  Alert,
  Spin,
  Button,
  Space,
  theme,
} from 'antd';
import {
  ReloadOutlined,
  CalculatorOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { declarationService } from './declarationService';
import { formatFyDisplay } from './financialYear';
import { setSectionData, selectTaxSection, selectTaxFy } from './taxSlice';
import { readError } from './apiError';
import { formatDateTime, formatMoney } from '@shared/ui/format.js';
import { MoneyText } from '@shared/ui/MoneyText.jsx';

const { Title, Text } = Typography;

export function SummarySection({ fy }) {
  const { token } = theme.useToken();
  const dispatch = useDispatch();
  const summaryData = useSelector(selectTaxSection('summary'));
  const storedFy = useSelector(selectTaxFy);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const loadSummary = useCallback(
    async (financialYear) => {
      if (!financialYear) return;
      setLoading(true);
      setError(null);
      try {
        const data = await declarationService.summary(financialYear);
        dispatch(setSectionData({ section: 'summary', data: data || null }));
      } catch (err) {
        setError(readError(err, 'Failed to load tax summary').message);
      } finally {
        setLoading(false);
      }
    },
    [dispatch],
  );

  // Read on first expand, and again whenever a section save drops it from the slice: the
  // totals always come from the server, never summed here.
  const missing = summaryData == null;
  const loadedFy = useRef(missing || (storedFy != null && storedFy !== fy) ? null : fy);
  useEffect(() => {
    if (missing || loadedFy.current !== fy) {
      loadedFy.current = fy;
      loadSummary(fy);
    }
  }, [fy, missing, loadSummary]);

  const declared = summaryData?.declared || {};
  const computed = summaryData?.computed;

  const section6aGroups = Object.entries(declared.section6a_by_group || {}).map(
    ([group, amt]) => ({
      key: group,
      group: `Section ${group}`,
      amount: Number(amt) || 0,
    }),
  );

  const declaredItems = [
    { key: 'rent', category: 'House Rent (Annual)', amount: declared.house_rent_annual || 0 },
    { key: 'hl_principal', category: 'Home Loan Principal (80C)', amount: declared.home_loan_principal || 0 },
    { key: 'hl_interest', category: 'Home Loan Interest (Sec 24b)', amount: declared.home_loan_interest || 0 },
    { key: 'letout', category: 'Let-Out Property Net Income / (Loss)', amount: declared.let_out_net || 0 },
    { key: 'sec6a', category: 'Chapter VI-A Total Deductions', amount: declared.section6a_total || 0 },
    { key: 'pretax', category: 'Pre-Tax Deductions Total', amount: declared.pre_tax_total || 0 },
    { key: 'prevemp_inc', category: 'Previous Employer Gross Income', amount: declared.prev_employment_income || 0 },
    { key: 'prevemp_tax', category: 'Previous Employer TDS Deducted', amount: declared.prev_employment_tax || 0 },
    { key: 'other_inc', category: 'Income from Other Sources Total', amount: declared.other_income_total || 0 },
  ];

  return (
    <Spin spinning={loading}>
      {error && (
        <Alert message="Error" description={error} type="error" showIcon style={{ marginBottom: 16 }} />
      )}

      <Card
        type="inner"
        title={
          <Row justify="space-between" align="middle" wrap>
            <Col>
              <Space align="center">
                <CalculatorOutlined style={{ fontSize: 20, color: token.colorPrimary }} />
                <Text strong style={{ fontSize: 16 }}>
                  Tax Declaration & Computation Summary — FY {formatFyDisplay(fy)}
                </Text>
              </Space>
            </Col>
            <Col>
              <Space>
                <Tag color={summaryData?.tax_regime === 'NEW' ? 'cyan' : 'magenta'}>
                  {summaryData?.tax_regime === 'NEW' ? 'New Regime (115BAC)' : 'Old Regime'}
                </Tag>
                <Button icon={<ReloadOutlined />} onClick={() => loadSummary(fy)} data-testid="reload-summary-btn">
                  Refresh Summary
                </Button>
              </Space>
            </Col>
          </Row>
        }
      >
        {/* Top summary cards */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" style={{ background: token.colorSuccessBg, borderColor: token.colorSuccessBorder }}>
              <Statistic
                title="Total Section 6A Declared"
                value={Number(declared.section6a_total || 0)}
                prefix="₹"
                precision={0}
                valueStyle={{ color: token.colorSuccess, fontSize: 20 }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" style={{ background: token.colorInfoBg, borderColor: token.colorInfoBorder }}>
              <Statistic
                title="Annual Rent Declared"
                value={Number(declared.house_rent_annual || 0)}
                prefix="₹"
                precision={0}
                valueStyle={{ color: token.colorPrimary, fontSize: 20 }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" style={{ background: token.colorWarningBg, borderColor: token.colorWarningBorder }}>
              <Statistic
                title="Other Income Declared"
                value={Number(declared.other_income_total || 0)}
                prefix="₹"
                precision={0}
                valueStyle={{ color: token.colorWarning, fontSize: 20 }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card size="small" style={{ background: token.colorFillAlter, borderColor: token.colorBorderSecondary }}>
              <Statistic
                title="Pre-Tax Deductions"
                value={Number(declared.pre_tax_total || 0)}
                prefix="₹"
                precision={0}
                valueStyle={{ color: token.colorTextSecondary, fontSize: 20 }}
              />
            </Card>
          </Col>
        </Row>

        {/* Declared values breakdown table */}
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={14}>
            <Title level={5}>Declared Investment Breakdown</Title>
            <Table
              dataSource={declaredItems}
              pagination={false}
              size="small"
              columns={[
                {
                  title: 'Category',
                  dataIndex: 'category',
                  key: 'category',
                  render: (name) => <Text strong>{name}</Text>,
                },
                {
                  title: 'Declared Amount (₹)',
                  dataIndex: 'amount',
                  key: 'amount',
                  align: 'right',
                  render: (val) => (
                    <MoneyText value={val} />
                  ),
                },
              ]}
            />

            {section6aGroups.length > 0 && (
              <div style={{ marginTop: 20 }}>
                <Text strong>Chapter VI-A Breakdown by Section:</Text>
                <div style={{ marginTop: 8 }}>
                  <Space wrap>
                    {section6aGroups.map((g) => (
                      <Tag key={g.key} color="blue" style={{ fontSize: 13, padding: '4px 10px' }}>
                        {g.group}: {formatMoney(g.amount)}
                      </Tag>
                    ))}
                  </Space>
                </div>
              </div>
            )}
          </Col>

          {/* Computed Tax Forecast */}
          <Col xs={24} lg={10}>
            <Title level={5}>
              <Space>
                <span>Tax Computation Forecast</span>
                {computed && (
                  <Tag color={computed.taxable_income ? 'green' : 'default'}>
                    {computed.taxable_income ? 'Live Computed' : 'Draft Estimate'}
                  </Tag>
                )}
              </Space>
            </Title>

            {!computed ? (
              <Card size="small" type="inner" style={{ background: token.colorFillAlter }}>
                <Text type="secondary">computed after the tax calculator runs</Text>
              </Card>
            ) : (
              <Card size="small" type="inner" style={{ background: token.colorFillAlter }}>
                <Row justify="space-between" style={{ padding: '8px 0', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
                  <Text type="secondary">Gross Taxable Income:</Text>
                  <Text strong>{formatMoney(computed.taxable_income || 0)}</Text>
                </Row>
                <Row justify="space-between" style={{ padding: '8px 0', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
                  <Text type="secondary">Section 10 Exemption (HRA):</Text>
                  <Text>{formatMoney(computed.exemption_under_section10 || 0)}</Text>
                </Row>
                <Row justify="space-between" style={{ padding: '8px 0', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
                  <Text type="secondary">Chapter VI-A Allowed Deduction:</Text>
                  <Text>{formatMoney(computed.exemption_under_section6a || 0)}</Text>
                </Row>
                <Row justify="space-between" style={{ padding: '8px 0', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
                  <Text strong>Net Taxable Income:</Text>
                  <Text strong style={{ color: token.colorPrimary }}>
                    {formatMoney(computed.net_taxable_income || 0)}
                  </Text>
                </Row>
                <Row justify="space-between" style={{ padding: '8px 0', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
                  <Text type="secondary">Computed Income Tax:</Text>
                  <Text strong>{formatMoney(computed.tax_on_taxable_income || 0)}</Text>
                </Row>
                <Row justify="space-between" style={{ padding: '8px 0', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
                  <Text type="secondary">Tax Paid YTD (Payroll TDS):</Text>
                  <Text>{formatMoney(computed.tds_through_payroll || computed.tax_ytd_amount || 0)}</Text>
                </Row>
                <Row justify="space-between" style={{ padding: '8px 0', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
                  <Text type="secondary">Previous Employer TDS Credit:</Text>
                  <Text>{formatMoney(computed.tds_previous_employer || 0)}</Text>
                </Row>
                <Row justify="space-between" style={{ padding: '10px 0' }}>
                  <Text strong style={{ fontSize: 15 }}>Remaining Tax to be Deducted:</Text>
                  <Text strong style={{ fontSize: 16, color: token.colorError }}>
                    {formatMoney(computed.tax_to_be_paid || 0)}
                  </Text>
                </Row>

                {computed.remaining_months ? (
                  <Alert
                    type="info"
                    showIcon
                    icon={<InfoCircleOutlined />}
                    message={`Remaining Months for TDS Recovery: ${computed.remaining_months} month(s).`}
                    style={{ marginTop: 12 }}
                  />
                ) : null}

                {computed.computed_at && (
                  <div style={{ marginTop: 12, textAlign: 'right' }}>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      Calculated on {formatDateTime(computed.computed_at)}
                    </Text>
                  </div>
                )}
              </Card>
            )}
          </Col>
        </Row>
      </Card>
    </Spin>
  );
}

SummarySection.propTypes = {
  fy: PropTypes.string.isRequired,
};

export default SummarySection;
