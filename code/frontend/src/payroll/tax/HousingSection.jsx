import { useState, useEffect, useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
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
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { declarationService } from './declarationService';
import { setSectionData, selectTaxSection, selectTaxFy } from './taxSlice';
import { readError, rowErrorsFrom } from './apiError';

const { Text } = Typography;

const LINE_TYPES = ['ANNUAL_RENT', 'MUNICIPAL_TAX', 'LOAN_INTEREST'];

const rupeeFormatter = (val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
const rupeeParser = (val) => val.replace(/₹\s?|(,*)/g, '');

/**
 * One let-out property from the server, with its lines keyed by `line_type`. Every field of a
 * line is kept - `lender_name` and `lender_pan` on a `LOAN_INTEREST` line included - so a save
 * sends back what it read.
 */
export function mapLetOutFromResponse(properties = []) {
  return properties.map((prop) => {
    const lines = {};
    (prop.lines || []).forEach((line) => {
      if (line?.line_type) {
        lines[line.line_type] = {
          amount: line.amount ?? null,
          lender_name: line.lender_name ?? '',
          lender_pan: line.lender_pan ?? '',
        };
      }
    });
    return {
      id: prop.id,
      property_name: prop.property_name ?? '',
      address: prop.address ?? '',
      net_income_loss: prop.net_income_loss,
      lines,
    };
  });
}

/** The let-out request body: a line per type that carries an amount or a lender. */
export function letOutToRequest(properties = []) {
  return properties.map((prop) => {
    const lines = [];
    LINE_TYPES.forEach((type) => {
      const line = prop.lines?.[type];
      if (!line) return;
      const amount = line.amount === null || line.amount === '' || line.amount === undefined
        ? null
        : Number(line.amount);
      const lenderName = (line.lender_name || '').trim();
      const lenderPan = (line.lender_pan || '').toUpperCase().trim();
      const hasLender = type === 'LOAN_INTEREST' && (lenderName !== '' || lenderPan !== '');
      if ((amount !== null && amount > 0) || hasLender) {
        const out = { line_type: type, amount: amount ?? 0 };
        if (type === 'LOAN_INTEREST') {
          out.lender_name = lenderName || null;
          out.lender_pan = lenderPan || null;
        }
        lines.push(out);
      }
    });
    return {
      property_name: prop.property_name || '',
      address: prop.address || '',
      lines,
    };
  });
}

/** The threshold the server returns on the header, or null when it returns none. */
function rentPanThreshold(header) {
  const raw = header?.rent_pan_threshold;
  if (raw === null || raw === undefined || raw === '' || typeof raw === 'boolean') return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

const countMonthsInclusive = (fromMonth, toMonth) => {
  if (!fromMonth || !toMonth) return 0;
  const [fromY, fromM] = String(fromMonth).split('-').map(Number);
  const [toY, toM] = String(toMonth).split('-').map(Number);
  if (!fromY || !fromM || !toY || !toM) return 0;
  const diff = (toY - fromY) * 12 + (toM - fromM) + 1;
  return diff > 0 ? diff : 0;
};

export function HousingSection({ fy, header, editable, onRefresh }) {
  const dispatch = useDispatch();
  const stored = useSelector(selectTaxSection('housing'));
  const storedFy = useSelector(selectTaxFy);

  const [loading, setLoading] = useState(false);
  const [savingRent, setSavingRent] = useState(false);
  const [savingLoan, setSavingLoan] = useState(false);
  const [savingLetOut, setSavingLetOut] = useState(false);
  const [error, setError] = useState(null);

  const [rentRows, setRentRows] = useState(() => stored?.house_rent || []);
  const [homeLoans, setHomeLoans] = useState(() => stored?.home_loans || []);
  const [letOutProperties, setLetOutProperties] = useState(() =>
    mapLetOutFromResponse(stored?.let_out_properties || []),
  );

  // Row-level errors from a 400, keyed by zero-based row index.
  const [rentErrors, setRentErrors] = useState({});
  const [loanErrors, setLoanErrors] = useState({});
  const [letOutErrors, setLetOutErrors] = useState({});

  const loadHousing = useCallback(
    async (financialYear) => {
      if (!financialYear) return;
      setLoading(true);
      setError(null);
      try {
        const data = await declarationService.housing(financialYear);
        setRentRows(data?.house_rent || []);
        setHomeLoans(data?.home_loans || []);
        setLetOutProperties(mapLetOutFromResponse(data?.let_out_properties || []));
        dispatch(setSectionData({ section: 'housing', data: data || null }));
      } catch (err) {
        setError(readError(err, 'Failed to load housing declarations').message);
      } finally {
        setLoading(false);
      }
    },
    [dispatch],
  );

  // Loaded on first expand: read from the server only when the slice holds nothing for this FY.
  const needsLoad = stored == null || (storedFy != null && storedFy !== fy);
  useEffect(() => {
    if (needsLoad) loadHousing(fy);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fy, loadHousing]);

  /** Stores a save response as the section and returns it. */
  const replaceSection = (data) => {
    if (data && typeof data === 'object' && 'house_rent' in data) {
      dispatch(setSectionData({ section: 'housing', data }));
    }
    // The summary's totals changed on the server; it re-reads them when next shown.
    dispatch(setSectionData({ section: 'summary', data: null }));
    return data;
  };

  // The one sum the browser makes: annual rent, to decide whether the landlord PAN is needed.
  const totalAnnualRent = useMemo(
    () =>
      rentRows.reduce((acc, row) => {
        const monthly = Number(row.amount_per_month) || 0;
        return acc + monthly * countMonthsInclusive(row.from_month, row.to_month);
      }, 0),
    [rentRows],
  );

  const panThreshold = rentPanThreshold(header);
  const isPanRequired =
    header?.pan_required_for_rent_over_threshold === true &&
    panThreshold !== null &&
    totalAnnualRent > panThreshold;

  // ── House Rent ──
  const handleAddRentRow = () => {
    const startYear = parseInt(String(fy).slice(0, 4), 10);
    setRentRows((prev) => [
      ...prev,
      {
        from_month: startYear ? `${startYear}-04` : '',
        to_month: startYear ? `${startYear + 1}-03` : '',
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
    setRentErrors({});
  };

  const handleSaveRent = async () => {
    if (isPanRequired) {
      const missing = {};
      rentRows.forEach((row, i) => {
        if (!(row.landlord_pan || '').trim()) {
          missing[i] = 'Landlord PAN is required: the annual rent is above the threshold.';
        }
      });
      if (Object.keys(missing).length > 0) {
        setRentErrors(missing);
        return;
      }
    }
    setSavingRent(true);
    setRentErrors({});
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
      const data = replaceSection(await declarationService.saveHouseRent(fy, payload));
      if (Array.isArray(data?.house_rent)) setRentRows(data.house_rent);
      successMsg('House Rent Saved', 'House rent declaration saved.');
      if (onRefresh) onRefresh();
    } catch (err) {
      setRentErrors(rowErrorsFrom(err, 'row'));
      errorMsg(readError(err, 'Failed to save house rent'));
    } finally {
      setSavingRent(false);
    }
  };

  // ── Home Loans ──
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
    setLoanErrors({});
  };

  const handleSaveHomeLoans = async () => {
    setSavingLoan(true);
    setLoanErrors({});
    try {
      const payload = homeLoans.map((loan) => ({
        lender_name: loan.lender_name || '',
        lender_pan: (loan.lender_pan || '').toUpperCase().trim(),
        principal_paid: Number(loan.principal_paid) || 0,
        interest_paid: Number(loan.interest_paid) || 0,
        is_first_time_buyer: Boolean(loan.is_first_time_buyer),
        loan_sanctioned_on: loan.loan_sanctioned_on || null,
      }));
      const data = replaceSection(await declarationService.saveHomeLoan(fy, payload));
      if (Array.isArray(data?.home_loans)) setHomeLoans(data.home_loans);
      successMsg('Home Loans Saved', 'Home loan declaration saved.');
      if (onRefresh) onRefresh();
    } catch (err) {
      setLoanErrors(rowErrorsFrom(err, 'row'));
      errorMsg(readError(err, 'Failed to save home loans'));
    } finally {
      setSavingLoan(false);
    }
  };

  // ── Let-Out Property ──
  const handleAddLetOut = () => {
    setLetOutProperties((prev) => [
      ...prev,
      { property_name: `Property #${prev.length + 1}`, address: '', lines: {} },
    ]);
  };

  const handleUpdateLetOut = (index, field, value) => {
    setLetOutProperties((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  };

  const handleUpdateLetOutLine = (index, type, field, value) => {
    setLetOutProperties((prev) => {
      const next = [...prev];
      const lines = { ...(next[index].lines || {}) };
      lines[type] = { ...(lines[type] || {}), [field]: value };
      next[index] = { ...next[index], lines };
      return next;
    });
  };

  const handleRemoveLetOut = (index) => {
    setLetOutProperties((prev) => prev.filter((_, i) => i !== index));
    setLetOutErrors({});
  };

  const handleSaveLetOut = async () => {
    setSavingLetOut(true);
    setLetOutErrors({});
    try {
      const data = replaceSection(
        await declarationService.saveLetOut(fy, letOutToRequest(letOutProperties)),
      );
      if (Array.isArray(data?.let_out_properties)) {
        setLetOutProperties(mapLetOutFromResponse(data.let_out_properties));
      }
      successMsg('Let-Out Property Saved', 'Let-out property declaration saved.');
      if (onRefresh) onRefresh();
    } catch (err) {
      setLetOutErrors(rowErrorsFrom(err, 'property'));
      errorMsg(readError(err, 'Failed to save let-out property'));
    } finally {
      setSavingLetOut(false);
    }
  };

  const rowError = (message, testId) =>
    message ? (
      <Text type="danger" style={{ fontSize: 12, display: 'block' }} data-testid={testId}>
        {message}
      </Text>
    ) : null;

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
            status={rentErrors[index] ? 'error' : ''}
            onChange={(e) => handleUpdateRentRow(index, 'from_month', e.target.value)}
          />
          <Input
            size="small"
            placeholder="To (YYYY-MM)"
            value={record.to_month}
            disabled={!editable}
            status={rentErrors[index] ? 'error' : ''}
            onChange={(e) => handleUpdateRentRow(index, 'to_month', e.target.value)}
          />
          {rowError(rentErrors[index], `rent-row-error-${index}`)}
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
          value={record.amount_per_month}
          disabled={!editable}
          status={rentErrors[index] ? 'error' : ''}
          formatter={rupeeFormatter}
          parser={rupeeParser}
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
            data-testid={`rent-pan-${index}`}
            aria-required={isPanRequired}
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
      render: (_, __, index) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          disabled={!editable}
          onClick={() => handleRemoveRentRow(index)}
          aria-label="Remove rent row"
        />
      ),
    },
  ];

  const errorCardStyle = (hasError) => ({
    marginBottom: 12,
    ...(hasError ? { borderColor: '#ff4d4f' } : {}),
  });

  return (
    <Spin spinning={loading}>
      {error && (
        <Alert message="Error" description={error} type="error" showIcon style={{ marginBottom: 16 }} />
      )}

      {/* ── House Rent (HRA) ── */}
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
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={handleAddRentRow}
              disabled={!editable}
              data-testid="add-rent-row-btn"
            >
              Add Rent Period
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingRent}
              disabled={!editable}
              onClick={handleSaveRent}
              data-testid="save-rent-btn"
            >
              Save House Rent
            </Button>
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        {isPanRequired && (
          <Alert
            message="Landlord PAN Required"
            description={`Your annual rent exceeds ₹${panThreshold.toLocaleString(
              'en-IN',
            )}, so the landlord's PAN is required on every rent row.`}
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            data-testid="rent-pan-required"
          />
        )}

        <Table
          dataSource={rentRows.map((r, i) => ({ ...r, key: i }))}
          columns={rentColumns}
          pagination={false}
          rowClassName={(_, index) => (rentErrors[index] ? 'tax-row-error' : '')}
          onRow={(_, index) => ({
            'data-testid': `rent-row-${index}`,
            'data-row-error': rentErrors[index] ? 'true' : 'false',
            style: rentErrors[index] ? { background: '#fff2f0' } : undefined,
          })}
          locale={{ emptyText: 'No rent declaration rows added. Click "Add Rent Period" to add.' }}
          size="small"
        />
      </Card>

      {/* ── Home Loans ── */}
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
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={handleAddHomeLoan}
              disabled={!editable}
              data-testid="add-home-loan-btn"
            >
              Add Home Loan
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingLoan}
              disabled={!editable}
              onClick={handleSaveHomeLoans}
              data-testid="save-loan-btn"
            >
              Save Home Loans
            </Button>
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          Interest paid on a home loan is claimed under Section 24(b), and principal repayment under
          Section 80C. The limits that apply are worked out by payroll from the declared amounts.
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
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  disabled={!editable}
                  onClick={() => handleRemoveHomeLoan(idx)}
                  aria-label="Remove home loan"
                />
              }
              style={errorCardStyle(Boolean(loanErrors[idx]))}
              data-testid={`loan-row-${idx}`}
              data-row-error={loanErrors[idx] ? 'true' : 'false'}
            >
              {rowError(loanErrors[idx], `loan-row-error-${idx}`)}
              <Row gutter={[16, 12]}>
                <Col xs={24} sm={12} md={6}>
                  <Text strong>Lender Name:</Text>
                  <Input
                    placeholder="e.g. State Bank of India"
                    value={loan.lender_name}
                    disabled={!editable}
                    status={loanErrors[idx] ? 'error' : ''}
                    onChange={(e) => handleUpdateHomeLoan(idx, 'lender_name', e.target.value)}
                  />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Text strong>Lender PAN:</Text>
                  <Input
                    placeholder="e.g. AAACS1234K"
                    value={loan.lender_pan}
                    disabled={!editable}
                    status={loanErrors[idx] ? 'error' : ''}
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
                    formatter={rupeeFormatter}
                    parser={rupeeParser}
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
                    formatter={rupeeFormatter}
                    parser={rupeeParser}
                    onChange={(val) => handleUpdateHomeLoan(idx, 'principal_paid', val)}
                    data-testid={`loan-principal-${idx}`}
                  />
                </Col>
              </Row>
            </Card>
          ))
        )}
      </Card>

      {/* ── Let-Out Property ── */}
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
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={handleAddLetOut}
              disabled={!editable}
              data-testid="add-letout-btn"
            >
              Add Let-Out Property
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingLetOut}
              disabled={!editable}
              onClick={handleSaveLetOut}
              data-testid="save-letout-btn"
            >
              Save Let-Out Properties
            </Button>
          </Space>
        }
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          The net income or loss is worked out by payroll after the standard deduction on the net
          annual value (gross rent less municipal taxes), and is shown once saved.
        </Text>

        {letOutProperties.length === 0 ? (
          <Text type="secondary">No let-out properties declared.</Text>
        ) : (
          letOutProperties.map((prop, idx) => {
            const line = (type) => prop.lines?.[type] || {};
            return (
              <Card
                key={idx}
                size="small"
                type="inner"
                title={
                  <Space>
                    <span>{prop.property_name}</span>
                    {prop.net_income_loss != null && (
                      <Tag color={Number(prop.net_income_loss) < 0 ? 'orange' : 'blue'}>
                        Net: ₹ {Number(prop.net_income_loss).toLocaleString('en-IN')}
                      </Tag>
                    )}
                  </Space>
                }
                extra={
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={!editable}
                    onClick={() => handleRemoveLetOut(idx)}
                    aria-label="Remove let-out property"
                  />
                }
                style={errorCardStyle(Boolean(letOutErrors[idx]))}
                data-testid={`letout-row-${idx}`}
                data-row-error={letOutErrors[idx] ? 'true' : 'false'}
              >
                {rowError(letOutErrors[idx], `letout-row-error-${idx}`)}
                <Row gutter={[16, 12]}>
                  <Col xs={24} sm={12}>
                    <Text strong>Property Name:</Text>
                    <Input
                      value={prop.property_name}
                      disabled={!editable}
                      status={letOutErrors[idx] ? 'error' : ''}
                      onChange={(e) => handleUpdateLetOut(idx, 'property_name', e.target.value)}
                      data-testid={`letout-name-${idx}`}
                    />
                  </Col>
                  <Col xs={24} sm={12}>
                    <Text strong>Address:</Text>
                    <Input
                      value={prop.address}
                      disabled={!editable}
                      onChange={(e) => handleUpdateLetOut(idx, 'address', e.target.value)}
                    />
                  </Col>
                  <Col xs={24} sm={12} md={8}>
                    <Text strong>Gross Rent Received (₹):</Text>
                    <InputNumber
                      style={{ width: '100%' }}
                      min={0}
                      value={line('ANNUAL_RENT').amount}
                      disabled={!editable}
                      onChange={(val) => handleUpdateLetOutLine(idx, 'ANNUAL_RENT', 'amount', val)}
                    />
                  </Col>
                  <Col xs={24} sm={12} md={8}>
                    <Text strong>Municipal Taxes Paid (₹):</Text>
                    <InputNumber
                      style={{ width: '100%' }}
                      min={0}
                      value={line('MUNICIPAL_TAX').amount}
                      disabled={!editable}
                      onChange={(val) => handleUpdateLetOutLine(idx, 'MUNICIPAL_TAX', 'amount', val)}
                    />
                  </Col>
                  <Col xs={24} sm={12} md={8}>
                    <Text strong>Home Loan Interest (₹):</Text>
                    <InputNumber
                      style={{ width: '100%' }}
                      min={0}
                      value={line('LOAN_INTEREST').amount}
                      disabled={!editable}
                      onChange={(val) => handleUpdateLetOutLine(idx, 'LOAN_INTEREST', 'amount', val)}
                    />
                  </Col>
                  <Col xs={24} sm={12}>
                    <Text strong>Loan Lender Name:</Text>
                    <Input
                      value={line('LOAN_INTEREST').lender_name}
                      disabled={!editable}
                      onChange={(e) =>
                        handleUpdateLetOutLine(idx, 'LOAN_INTEREST', 'lender_name', e.target.value)
                      }
                      data-testid={`letout-lender-name-${idx}`}
                    />
                  </Col>
                  <Col xs={24} sm={12}>
                    <Text strong>Loan Lender PAN:</Text>
                    <Input
                      value={line('LOAN_INTEREST').lender_pan}
                      disabled={!editable}
                      onChange={(e) =>
                        handleUpdateLetOutLine(idx, 'LOAN_INTEREST', 'lender_pan', e.target.value)
                      }
                      data-testid={`letout-lender-pan-${idx}`}
                    />
                  </Col>
                </Row>
              </Card>
            );
          })
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
