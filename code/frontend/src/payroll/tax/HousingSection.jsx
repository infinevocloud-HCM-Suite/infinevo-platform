import { useState, useEffect, useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Table,
  Button,
  Input,
  InputNumber,
  Switch,
  Space,
  Typography,
  Alert,
  Spin,
  Row,
  Col,
  message,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  SaveOutlined,
  HomeOutlined,
  BankOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { declarationService } from './declarationService';
import { store } from '@shell/store';
import { setSectionData } from './taxSlice';

const { Text } = Typography;

export function HousingSection({ fy, header, editable, onRefresh }) {
  const [loading, setLoading] = useState(false);
  const [savingRent, setSavingRent] = useState(false);
  const [savingLoan, setSavingLoan] = useState(false);
  const [savingLetOut, setSavingLetOut] = useState(false);
  const [error, setError] = useState(null);

  // House Rent Lines
  const [rentRows, setRentRows] = useState([]);

  // Home Loans
  const [homeLoans, setHomeLoans] = useState([]);

  // Let Out Properties
  const [letOutProperties, setLetOutProperties] = useState([]);

  const loadHousing = useCallback(async (financialYear) => {
    if (!financialYear) return;
    setLoading(true);
    setError(null);
    try {
      const data = await declarationService.housing(financialYear);
      setRentRows(data?.house_rent || []);
      setHomeLoans(data?.home_loans || []);
      setLetOutProperties(data?.let_out_properties || []);
      store.dispatch(setSectionData({ section: 'housing', data: data || null }));
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load housing declarations');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHousing(fy);
  }, [fy, loadHousing]);

  // House Rent calculations using actual from_month..to_month inclusive month span
  const countMonthsInclusive = (fromMonth, toMonth) => {
    if (!fromMonth || !toMonth) return 0;
    const [fromY, fromM] = String(fromMonth).split('-').map(Number);
    const [toY, toM] = String(toMonth).split('-').map(Number);
    if (!fromY || !fromM || !toY || !toM) return 0;
    const diff = (toY - fromY) * 12 + (toM - fromM) + 1;
    return diff > 0 ? diff : 0;
  };

  const totalAnnualRent = useMemo(() => {
    return rentRows.reduce((acc, row) => {
      const monthly = Number(row.amount_per_month) || 0;
      const months = countMonthsInclusive(row.from_month, row.to_month);
      return acc + monthly * months;
    }, 0);
  }, [rentRows]);

  const panThreshold =
    typeof header?.rent_pan_threshold === 'number'
      ? header.rent_pan_threshold
      : typeof header?.pan_required_for_rent_over_threshold === 'number'
        ? header.pan_required_for_rent_over_threshold
        : 100000;
  const isPanRequired =
    header?.pan_required_for_rent_over_threshold !== false && totalAnnualRent > panThreshold;

  // ── House Rent Handlers ──
  const handleAddRentRow = () => {
    const startYear = parseInt(fy.slice(0, 4), 10) || 2026;
    setRentRows((prev) => [
      ...prev,
      {
        from_month: `${startYear}-04`,
        to_month: `${startYear + 1}-03`,
        address: '',
        landlord_name: '',
        landlord_pan: '',
        is_metro: false,
        amount_per_month: 0,
      },
    ]);
  };

  const handleUpdateRentRow = (index, field, value) => {
    setRentRows((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  };

  const handleRemoveRentRow = (index) => {
    setRentRows((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSaveRent = async () => {
    setSavingRent(true);
    try {
      const payload = rentRows.map((row) => ({
        from_month: row.from_month,
        to_month: row.to_month,
        address: row.address || '',
        landlord_name: row.landlord_name || '',
        landlord_pan: (row.landlord_pan || '').toUpperCase().trim(),
        is_metro: Boolean(row.is_metro),
        amount_per_month: Number(row.amount_per_month) || 0,
      }));
      await declarationService.saveHouseRent(fy, payload);
      message.success('House rent declaration saved successfully');
      if (onRefresh) onRefresh();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to save house rent');
    } finally {
      setSavingRent(false);
    }
  };

  // ── Home Loan Handlers ──
  const handleAddHomeLoan = () => {
    setHomeLoans((prev) => [
      ...prev,
      {
        lender_name: '',
        lender_pan: '',
        principal_paid: 0,
        interest_paid: 0,
        is_first_time_buyer: false,
        loan_sanctioned_on: null,
      },
    ]);
  };

  const handleUpdateHomeLoan = (index, field, value) => {
    setHomeLoans((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  };

  const handleRemoveHomeLoan = (index) => {
    setHomeLoans((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSaveHomeLoans = async () => {
    setSavingLoan(true);
    try {
      const payload = homeLoans.map((loan) => ({
        lender_name: loan.lender_name || '',
        lender_pan: (loan.lender_pan || '').toUpperCase().trim(),
        principal_paid: Number(loan.principal_paid) || 0,
        interest_paid: Number(loan.interest_paid) || 0,
        is_first_time_buyer: Boolean(loan.is_first_time_buyer),
        loan_sanctioned_on: loan.loan_sanctioned_on || null,
      }));
      await declarationService.saveHomeLoan(fy, payload);
      message.success('Home loan declaration saved successfully');
      if (onRefresh) onRefresh();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to save home loans');
    } finally {
      setSavingLoan(false);
    }
  };

  // ── Let Out Property Handlers ──
  const handleAddLetOut = () => {
    setLetOutProperties((prev) => [
      ...prev,
      {
        property_name: `Property #${prev.length + 1}`,
        address: '',
        gross_rent: 0,
        municipal_tax: 0,
        interest: 0,
      },
    ]);
  };

  const handleUpdateLetOut = (index, field, value) => {
    setLetOutProperties((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  };

  const handleRemoveLetOut = (index) => {
    setLetOutProperties((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSaveLetOut = async () => {
    setSavingLetOut(true);
    try {
      const payload = letOutProperties.map((prop) => {
        const lines = [];
        if (prop.gross_rent) {
          lines.push({ line_type: 'GROSS_RENT_RECEIVED', amount: Number(prop.gross_rent) });
        }
        if (prop.municipal_tax) {
          lines.push({ line_type: 'MUNICIPAL_TAX', amount: Number(prop.municipal_tax) });
        }
        if (prop.interest) {
          lines.push({ line_type: 'HOME_LOAN_INTEREST', amount: Number(prop.interest) });
        }
        return {
          property_name: prop.property_name || 'Let Out Property',
          address: prop.address || '',
          lines,
        };
      });
      await declarationService.saveLetOut(fy, payload);
      message.success('Let-out property declarations saved successfully');
      if (onRefresh) onRefresh();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to save let-out property');
    } finally {
      setSavingLetOut(false);
    }
  };

  const rentColumns = [
    {
      title: 'Period (From - To)',
      key: 'period',
      width: 220,
      render: (_, record, index) => (
        <Space direction="vertical" size={2}>
          <Input
            size="small"
            placeholder="From (YYYY-MM)"
            value={record.from_month}
            disabled={!editable}
            onChange={(e) => handleUpdateRentRow(index, 'from_month', e.target.value)}
          />
          <Input
            size="small"
            placeholder="To (YYYY-MM)"
            value={record.to_month}
            disabled={!editable}
            onChange={(e) => handleUpdateRentRow(index, 'to_month', e.target.value)}
          />
        </Space>
      ),
    },
    {
      title: 'Monthly Rent (₹)',
      key: 'amount',
      width: 150,
      render: (_, record, index) => (
        <InputNumber
          style={{ width: '100%' }}
          min={0}
          step={1000}
          value={record.amount_per_month}
          disabled={!editable}
          formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
          parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
          onChange={(val) => handleUpdateRentRow(index, 'amount_per_month', val)}
          data-testid={`rent-amount-${index}`}
        />
      ),
    },
    {
      title: 'Landlord Details',
      key: 'landlord',
      render: (_, record, index) => (
        <Space direction="vertical" style={{ width: '100%' }} size={4}>
          <Input
            placeholder="Landlord Name"
            value={record.landlord_name}
            disabled={!editable}
            onChange={(e) => handleUpdateRentRow(index, 'landlord_name', e.target.value)}
          />
          <Input
            placeholder="Landlord PAN (ABCDE1234F)"
            value={record.landlord_pan}
            disabled={!editable}
            status={isPanRequired && !record.landlord_pan ? 'error' : ''}
            onChange={(e) => handleUpdateRentRow(index, 'landlord_pan', e.target.value)}
          />
        </Space>
      ),
    },
    {
      title: 'Rental Address',
      key: 'address',
      render: (_, record, index) => (
        <Input
          placeholder="Rental premises address"
          value={record.address}
          disabled={!editable}
          onChange={(e) => handleUpdateRentRow(index, 'address', e.target.value)}
        />
      ),
    },
    {
      title: 'Metro',
      key: 'is_metro',
      width: 90,
      render: (_, record, index) => (
        <Switch
          checked={record.is_metro}
          disabled={!editable}
          checkedChildren="Yes"
          unCheckedChildren="No"
          onChange={(chk) => handleUpdateRentRow(index, 'is_metro', chk)}
        />
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 50,
      render: (_, __, index) =>
        editable && (
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleRemoveRentRow(index)}
          />
        ),
    },
  ];

  return (
    <Spin spinning={loading}>
      {error && (
        <Alert message="Error" description={error} type="error" showIcon style={{ marginBottom: 16 }} />
      )}

      {/* ── Section 1: House Rent (HRA) ── */}
      <Card
        type="inner"
        title={
          <Space align="center">
            <HomeOutlined style={{ color: '#1677ff' }} />
            <Text strong>House Rent (HRA Exemption)</Text>
            {header?.is_staying_in_rented_house && <Tag color="green">Opted in Header</Tag>}
          </Space>
        }
        extra={
          editable && (
            <Space>
              <Button icon={<PlusOutlined />} onClick={handleAddRentRow} data-testid="add-rent-row-btn">
                Add Rent Period
              </Button>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={savingRent}
                onClick={handleSaveRent}
                data-testid="save-rent-btn"
              >
                Save House Rent
              </Button>
            </Space>
          )
        }
        style={{ marginBottom: 24 }}
      >
        {isPanRequired && (
          <Alert
            message="Landlord PAN Required"
            description={`Your annualized rent declaration exceeds ₹${panThreshold.toLocaleString(
              'en-IN',
            )}. Section 10(13A) mandates furnishing landlord PAN.`}
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        <Table
          dataSource={rentRows.map((r, i) => ({ ...r, key: i }))}
          columns={rentColumns}
          pagination={false}
          locale={{ emptyText: 'No rent declaration rows added. Click "Add Rent Period" to add.' }}
          size="small"
        />
      </Card>

      {/* ── Section 2: Home Loans ── */}
      <Card
        type="inner"
        title={
          <Space align="center">
            <BankOutlined style={{ color: '#52c41a' }} />
            <Text strong>Home Loan Declarations (Section 24b & 80C)</Text>
            {header?.is_repaying_self_occupied_loan && <Tag color="green">Opted in Header</Tag>}
          </Space>
        }
        extra={
          editable && (
            <Space>
              <Button icon={<PlusOutlined />} onClick={handleAddHomeLoan} data-testid="add-home-loan-btn">
                Add Home Loan
              </Button>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={savingLoan}
                onClick={handleSaveHomeLoans}
                data-testid="save-loan-btn"
              >
                Save Home Loans
              </Button>
            </Space>
          )
        }
        style={{ marginBottom: 24 }}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          Interest paid on home loan is eligible for tax deduction under Section 24(b) (up to ₹2,00,000 for self-occupied property). Principal repayment is eligible under Section 80C.
        </Text>

        {homeLoans.length === 0 ? (
          <Text type="secondary">No home loans declared.</Text>
        ) : (
          homeLoans.map((loan, idx) => (
            <Card
              key={idx}
              size="small"
              type="inner"
              title={`Loan #${idx + 1}: ${loan.lender_name || 'Lender Details'}`}
              extra={
                editable && (
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => handleRemoveHomeLoan(idx)}
                  />
                )
              }
              style={{ marginBottom: 12 }}
            >
              <Row gutter={[16, 12]}>
                <Col xs={24} sm={12} md={6}>
                  <Text strong>Lender Name:</Text>
                  <Input
                    placeholder="e.g. State Bank of India"
                    value={loan.lender_name}
                    disabled={!editable}
                    onChange={(e) => handleUpdateHomeLoan(idx, 'lender_name', e.target.value)}
                  />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Text strong>Lender PAN:</Text>
                  <Input
                    placeholder="e.g. AAACS1234K"
                    value={loan.lender_pan}
                    disabled={!editable}
                    onChange={(e) => handleUpdateHomeLoan(idx, 'lender_pan', e.target.value)}
                  />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Text strong>Interest Paid (₹):</Text>
                  <InputNumber
                    style={{ width: '100%' }}
                    min={0}
                    value={loan.interest_paid}
                    disabled={!editable}
                    formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                    parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
                    onChange={(val) => handleUpdateHomeLoan(idx, 'interest_paid', val)}
                    data-testid={`loan-interest-${idx}`}
                  />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Text strong>Principal Paid (₹):</Text>
                  <InputNumber
                    style={{ width: '100%' }}
                    min={0}
                    value={loan.principal_paid}
                    disabled={!editable}
                    formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                    parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
                    onChange={(val) => handleUpdateHomeLoan(idx, 'principal_paid', val)}
                    data-testid={`loan-principal-${idx}`}
                  />
                </Col>
              </Row>
            </Card>
          ))
        )}
      </Card>

      {/* ── Section 3: Let-Out Property ── */}
      <Card
        type="inner"
        title={
          <Space align="center">
            <ThunderboltOutlined style={{ color: '#fa8c16' }} />
            <Text strong>Let-Out Property Income / Loss</Text>
            {header?.has_let_out_property && <Tag color="green">Opted in Header</Tag>}
          </Space>
        }
        extra={
          editable && (
            <Space>
              <Button icon={<PlusOutlined />} onClick={handleAddLetOut} data-testid="add-letout-btn">
                Add Let-Out Property
              </Button>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={savingLetOut}
                onClick={handleSaveLetOut}
                data-testid="save-letout-btn"
              >
                Save Let-Out Properties
              </Button>
            </Space>
          )
        }
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          Standard deduction of 30% is automatically applied to Net Annual Value (Gross Rent - Municipal Taxes).
        </Text>

        {letOutProperties.length === 0 ? (
          <Text type="secondary">No let-out properties declared.</Text>
        ) : (
          letOutProperties.map((prop, idx) => (
            <Card
              key={idx}
              size="small"
              type="inner"
              title={prop.property_name}
              extra={
                editable && (
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => handleRemoveLetOut(idx)}
                  />
                )
              }
              style={{ marginBottom: 12 }}
            >
              <Row gutter={[16, 12]}>
                <Col xs={24} sm={12} md={8}>
                  <Text strong>Gross Rent Received (₹):</Text>
                  <InputNumber
                    style={{ width: '100%' }}
                    min={0}
                    value={prop.gross_rent}
                    disabled={!editable}
                    onChange={(val) => handleUpdateLetOut(idx, 'gross_rent', val)}
                  />
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Text strong>Municipal Taxes Paid (₹):</Text>
                  <InputNumber
                    style={{ width: '100%' }}
                    min={0}
                    value={prop.municipal_tax}
                    disabled={!editable}
                    onChange={(val) => handleUpdateLetOut(idx, 'municipal_tax', val)}
                  />
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Text strong>Home Loan Interest (₹):</Text>
                  <InputNumber
                    style={{ width: '100%' }}
                    min={0}
                    value={prop.interest}
                    disabled={!editable}
                    onChange={(val) => handleUpdateLetOut(idx, 'interest', val)}
                  />
                </Col>
              </Row>
            </Card>
          ))
        )}
      </Card>
    </Spin>
  );
}

HousingSection.propTypes = {
  fy: PropTypes.string.isRequired,
  header: PropTypes.object,
  editable: PropTypes.bool,
  onRefresh: PropTypes.func,
};

export default HousingSection;
