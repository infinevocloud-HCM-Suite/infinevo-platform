import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
import { Card, Table, Button, Input, Select, Space, Typography, Alert, Spin, Tag } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  SaveOutlined,
  AuditOutlined,
  DollarCircleOutlined,
  IdcardOutlined,
} from '@ant-design/icons';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { declarationService } from './declarationService';
import {
  setItems,
  setSectionData,
  selectTaxSection,
  selectTaxFy,
  selectTaxItems,
  selectTaxItemsRegime,
} from './taxSlice';
import { readError, rowsNamedIn } from './apiError';

const { Text, Paragraph } = Typography;

// Kinds are the backend enums (PreTaxDeductionKind, PrevEmploymentKind). No limits here: every
// limit the employee sees comes from the server (§9).
const PRE_TAX_KINDS = [
  { value: 'VPF', label: 'Voluntary Provident Fund (VPF)' },
  { value: 'EMPLOYEE_PF', label: 'Employee PF (pre-tax)' },
  { value: 'NPS_EMPLOYEE', label: 'Employee NPS contribution' },
  { value: 'PROFESSIONAL_TAX', label: 'Professional Tax (PT)' },
];

const PREV_EMP_KINDS = [
  { value: 'INCOME', label: 'Gross salary from previous employer' },
  { value: 'INCOME_TAX_DEDUCTED', label: 'TDS deducted by previous employer' },
  { value: 'EMPLOYEE_PF', label: 'PF deducted by previous employer' },
  { value: 'PROFESSIONAL_TAX', label: 'Professional tax deducted' },
  { value: 'LEAVE_ENCASHMENT', label: 'Leave encashment' },
];

const amountText = (value) => (value === null || value === undefined ? '' : String(value));
const isAmount = (value) => {
  const text = String(value ?? '').trim();
  return text !== '' && Number.isFinite(Number(text)) && Number(text) >= 0;
};
const formatRupees = (value) => `₹ ${Number(value).toLocaleString('en-IN')}`;
const humanise = (code) =>
  String(code || 'OTHER')
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');

let rowSeq = 0;
const nextKey = () => {
  rowSeq += 1;
  return `row-${rowSeq}`;
};

const sixARowsFrom = (data) =>
  (data?.section6a || []).map((line) => ({
    key: line.id || nextKey(),
    section6a_item_id: line.section6a_item_id ?? null,
    section_code: line.section_code ?? '',
    name: line.name ?? '',
    description: line.description ?? '',
    amount: amountText(line.amount),
  }));

const preTaxRowsFrom = (data) =>
  (data?.pre_tax_deductions || []).map((row) => ({
    key: row.id || nextKey(),
    kind: row.kind ?? null,
    amount: amountText(row.amount),
  }));

const prevEmpRowsFrom = (data) =>
  (data?.previous_employment || []).map((row) => ({
    key: row.id || nextKey(),
    kind: row.kind ?? null,
    amount: amountText(row.amount),
    employer_name: row.employer_name ?? '',
    employer_tan: row.employer_tan ?? '',
    entered_by: row.entered_by ?? 'EMPLOYEE',
  }));

/** Row index → message, for the rows a 400 names by number. */
function numberedRowErrors(err) {
  const { status, message } = readError(err, '');
  if (status !== undefined && status !== 400) return {};
  const errors = {};
  rowsNamedIn(message, 'row').forEach((i) => {
    errors[i] = message;
  });
  return errors;
}

export function DeductionsSection({ fy, editable, onRefresh, regime }) {
  const dispatch = useDispatch();
  const stored = useSelector(selectTaxSection('deductions'));
  const storedFy = useSelector(selectTaxFy);
  const catalogue = useSelector(selectTaxItems);
  const catalogueRegime = useSelector(selectTaxItemsRegime);

  const [loading, setLoading] = useState(false);
  const [itemsReady, setItemsReady] = useState(false);
  const [saving6a, setSaving6a] = useState(false);
  const [savingPreTax, setSavingPreTax] = useState(false);
  const [savingPrevEmp, setSavingPrevEmp] = useState(false);
  const [error, setError] = useState(null);
  const [officerNotice, setOfficerNotice] = useState(null);

  const [sixARows, setSixARows] = useState(() => sixARowsFrom(stored));
  const [preTaxRows, setPreTaxRows] = useState(() => preTaxRowsFrom(stored));
  const [prevEmpRows, setPrevEmpRows] = useState(() => prevEmpRowsFrom(stored));

  // { [rowIndex]: { item?, description?, amount?, row? } }
  const [sixAErrors, setSixAErrors] = useState({});
  const [preTaxErrors, setPreTaxErrors] = useState({});
  const [prevEmpErrors, setPrevEmpErrors] = useState({});

  const loadDeductions = useCallback(
    async (financialYear) => {
      setLoading(true);
      setError(null);
      try {
        const data = await declarationService.deductions(financialYear);
        setSixARows(sixARowsFrom(data));
        setPreTaxRows(preTaxRowsFrom(data));
        setPrevEmpRows(prevEmpRowsFrom(data));
        dispatch(setSectionData({ section: 'deductions', data: data || null }));
      } catch (err) {
        setError(readError(err, 'Failed to load deductions').message);
      } finally {
        setLoading(false);
      }
    },
    [dispatch],
  );

  const loadItems = useCallback(
    async (financialYear, forRegime) => {
      setItemsReady(false);
      try {
        const items = await declarationService.items(financialYear);
        dispatch(setItems({ items: items || [], regime: forRegime ?? null }));
        setItemsReady(true);
      } catch (err) {
        setError(readError(err, 'Failed to load the Section 6A items').message);
      }
    },
    [dispatch],
  );

  // Loaded on first expand: the section from the slice when it holds this FY, else the server.
  const needsLoad = stored == null || (storedFy != null && storedFy !== fy);
  useEffect(() => {
    if (fy && needsLoad) loadDeductions(fy);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fy, loadDeductions]);

  // The catalogue is filtered by regime on the server, so a regime switch re-reads it.
  const cachedItems = useRef({ catalogue, catalogueRegime, storedFy });
  cachedItems.current = { catalogue, catalogueRegime, storedFy };
  useEffect(() => {
    if (!fy) return;
    const cached = cachedItems.current;
    const fresh =
      cached.catalogue.length > 0 &&
      cached.catalogueRegime === (regime ?? null) &&
      (cached.storedFy == null || cached.storedFy === fy);
    if (fresh) {
      setItemsReady(true);
    } else {
      loadItems(fy, regime);
    }
  }, [fy, regime, loadItems]);

  const itemsById = useMemo(() => {
    const map = new Map();
    catalogue.forEach((item) => map.set(item.id, item));
    return map;
  }, [catalogue]);

  // Picker options grouped by the item's `category` (§5).
  const itemOptions = useMemo(() => {
    const groups = new Map();
    catalogue.forEach((item) => {
      const category = item.category || 'OTHER';
      if (!groups.has(category)) groups.set(category, []);
      groups.get(category).push({
        value: item.id,
        label: `${item.name || item.section_code}${item.section_code ? ` (${item.section_code})` : ''}`,
      });
    });
    return [...groups.entries()].map(([category, options]) => ({
      label: humanise(category),
      title: category,
      options,
    }));
  }, [catalogue]);

  const isDisallowed = (row) =>
    itemsReady && row.section6a_item_id != null && !itemsById.has(row.section6a_item_id);

  const replaceSection = (data) => {
    // The summary's totals changed on the server; it re-reads them when next shown.
    dispatch(setSectionData({ section: 'summary', data: null }));
    if (data && typeof data === 'object' && 'section6a' in data) {
      dispatch(setSectionData({ section: 'deductions', data }));
      return data;
    }
    return null;
  };

  const showError = (err, fallback) => {
    const info = readError(err, fallback);
    setError(info.message);
    errorMsg(info);
    return info;
  };

  // ── Section 6A ──
  const updateSixA = (index, field, value) => {
    setSixARows((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
    setSixAErrors((prev) => {
      if (!prev[index]) return prev;
      const next = { ...prev };
      delete next[index];
      return next;
    });
  };

  const addSixA = () => {
    setSixARows((prev) => [
      ...prev,
      { key: nextKey(), section6a_item_id: null, section_code: '', name: '', description: '', amount: '' },
    ]);
  };

  const removeSixA = (index) => {
    setSixARows((prev) => prev.filter((_, i) => i !== index));
    setSixAErrors({});
  };

  const validateSixA = () => {
    const errors = {};
    sixARows.forEach((row, i) => {
      const rowErrors = {};
      if (isDisallowed(row)) {
        rowErrors.row = `Not allowed under the ${regime || 'current'} regime. Remove this row to save.`;
      }
      if (!row.section6a_item_id) rowErrors.item = 'Pick an item';
      if (!String(row.description || '').trim()) rowErrors.description = 'Description is required';
      if (!isAmount(row.amount)) rowErrors.amount = 'Enter an amount of 0 or more';
      if (Object.keys(rowErrors).length > 0) errors[i] = rowErrors;
    });
    return errors;
  };

  /** Rows a 6A 400 names: by number, by section code, or by category group. */
  const sixAServerErrors = (err) => {
    const errors = {};
    Object.entries(numberedRowErrors(err)).forEach(([i, message]) => {
      errors[i] = { row: message };
    });
    const { status, message } = readError(err, '');
    if (status === 400 && typeof message === 'string') {
      const section = /for section\s+(\S+)\s+\(/i.exec(message)?.[1];
      const group = /for group\s+(\S+)\s+\(/i.exec(message)?.[1];
      sixARows.forEach((row, i) => {
        const item = itemsById.get(row.section6a_item_id);
        const code = item?.section_code || row.section_code;
        if ((section && code === section) || (group && item?.category_group_code === group)) {
          errors[i] = { row: message };
        }
      });
    }
    return errors;
  };

  const handleSave6a = async () => {
    setError(null);
    const errors = validateSixA();
    setSixAErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setSaving6a(true);
    try {
      const payload = sixARows.map((row) => ({
        section6a_item_id: row.section6a_item_id,
        description: String(row.description).trim(),
        amount: Number(row.amount),
      }));
      const data = replaceSection(await declarationService.save6a(fy, payload));
      if (data) setSixARows(sixARowsFrom(data));
      successMsg('Section 6A Saved', 'Chapter VI-A declarations saved.');
      if (onRefresh) onRefresh();
    } catch (err) {
      setSixAErrors(sixAServerErrors(err));
      showError(err, 'Failed to save Section 6A');
    } finally {
      setSaving6a(false);
    }
  };

  // ── Pre-tax ──
  const updatePreTax = (index, field, value) => {
    setPreTaxRows((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  };

  const handleSavePreTax = async () => {
    setError(null);
    const errors = {};
    preTaxRows.forEach((row, i) => {
      const rowErrors = {};
      if (!row.kind) rowErrors.kind = 'Pick a kind';
      if (!isAmount(row.amount)) rowErrors.amount = 'Enter an amount of 0 or more';
      if (Object.keys(rowErrors).length > 0) errors[i] = rowErrors;
    });
    setPreTaxErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setSavingPreTax(true);
    try {
      const payload = preTaxRows.map((row) => ({ kind: row.kind, amount: Number(row.amount) }));
      const data = replaceSection(await declarationService.savePreTax(fy, payload));
      if (data) setPreTaxRows(preTaxRowsFrom(data));
      successMsg('Pre-Tax Deductions Saved', 'Pre-tax deductions saved.');
      if (onRefresh) onRefresh();
    } catch (err) {
      const rowErrors = {};
      Object.entries(numberedRowErrors(err)).forEach(([i, message]) => {
        rowErrors[i] = { row: message };
      });
      setPreTaxErrors(rowErrors);
      showError(err, 'Failed to save pre-tax deductions');
    } finally {
      setSavingPreTax(false);
    }
  };

  // ── Previous employment ──
  const updatePrevEmp = (index, field, value) => {
    setPrevEmpRows((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  };

  const handleSavePrevEmployment = async () => {
    setError(null);
    setOfficerNotice(null);
    const own = prevEmpRows
      .map((row, index) => ({ row, index }))
      .filter(({ row }) => row.entered_by !== 'OFFICER');
    const errors = {};
    own.forEach(({ row, index }) => {
      const rowErrors = {};
      if (!row.kind) rowErrors.kind = 'Pick a kind';
      if (!isAmount(row.amount)) rowErrors.amount = 'Enter an amount of 0 or more';
      if (Object.keys(rowErrors).length > 0) errors[index] = rowErrors;
    });
    setPrevEmpErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setSavingPrevEmp(true);
    try {
      // Officer-entered rows are read-only here and never sent back.
      const payload = own.map(({ row }) => ({
        kind: row.kind,
        amount: Number(row.amount),
        employer_name: row.employer_name || '',
        employer_tan: (row.employer_tan || '').toUpperCase().trim(),
      }));
      const data = replaceSection(await declarationService.savePrevEmployment(fy, payload));
      if (data) setPrevEmpRows(prevEmpRowsFrom(data));
      successMsg('Previous Employment Saved', 'Previous employment details saved.');
      if (onRefresh) onRefresh();
    } catch (err) {
      const info = readError(err, 'Failed to save previous employment');
      if (info.code === 'OFFICER_ENTERED') {
        setOfficerNotice(
          info.message ||
            'Previous employment details entered by the payroll officer cannot be changed here.',
        );
      }
      // A 400 numbers the rows it was sent; map them back to the rows on screen.
      const rowErrors = {};
      Object.entries(numberedRowErrors(err)).forEach(([i, message]) => {
        const target = own[Number(i)];
        if (target) rowErrors[target.index] = { row: message };
      });
      setPrevEmpErrors(rowErrors);
      showError(err, 'Failed to save previous employment');
    } finally {
      setSavingPrevEmp(false);
    }
  };

  const fieldError = (message, testId) =>
    message ? (
      <Text type="danger" style={{ fontSize: 12, display: 'block' }} data-testid={testId}>
        {message}
      </Text>
    ) : null;

  const rowProps = (prefix, errors) => (_, index) => ({
    'data-testid': `${prefix}-row-${index}`,
    'data-row-error': errors[index] ? 'true' : 'false',
    style: errors[index]?.row ? { background: '#fff2f0' } : undefined,
  });

  const sixAColumns = [
    {
      title: 'Item',
      key: 'item',
      render: (_, row, index) => {
        const item = itemsById.get(row.section6a_item_id);
        const disallowed = isDisallowed(row);
        const errs = sixAErrors[index] || {};
        return (
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            <Space wrap>
              {disallowed ? (
                <Text delete data-testid={`input-6a-item-${index}`}>
                  {row.name || row.section_code || 'Unknown item'}
                  {row.section_code ? ` (${row.section_code})` : ''}
                </Text>
              ) : (
                <Select
                  style={{ minWidth: 260 }}
                  placeholder="Pick an item"
                  value={row.section6a_item_id ?? undefined}
                  options={itemOptions}
                  disabled={!editable}
                  status={errs.item ? 'error' : undefined}
                  onChange={(val) => updateSixA(index, 'section6a_item_id', val)}
                  virtual={false}
                  data-testid={`input-6a-item-${index}`}
                />
              )}
              {item &&
                (item.max_limit != null ? (
                  <Tag color="blue" data-testid={`item-cap-${index}`}>
                    Limit {formatRupees(item.max_limit)}
                  </Tag>
                ) : (
                  <Tag data-testid={`item-cap-${index}`}>No ceiling</Tag>
                ))}
              {disallowed && (
                <Tag color="red" data-testid={`disallowed-6a-${index}`}>
                  Not allowed under the {regime || 'current'} regime - remove
                </Tag>
              )}
            </Space>
            {fieldError(errs.item, `error-6a-item-${index}`)}
            {fieldError(errs.row, `error-6a-row-${index}`)}
          </Space>
        );
      },
    },
    {
      title: 'Description',
      key: 'description',
      width: 260,
      render: (_, row, index) => {
        const errs = sixAErrors[index] || {};
        return (
          <>
            <Input
              placeholder="e.g. policy number, institution"
              value={row.description}
              disabled={!editable || isDisallowed(row)}
              status={errs.description ? 'error' : undefined}
              onChange={(e) => updateSixA(index, 'description', e.target.value)}
              data-testid={`input-6a-desc-${index}`}
            />
            {fieldError(errs.description, `error-6a-desc-${index}`)}
          </>
        );
      },
    },
    {
      title: 'Amount (₹)',
      key: 'amount',
      width: 180,
      render: (_, row, index) => {
        const errs = sixAErrors[index] || {};
        return (
          <>
            <Input
              inputMode="decimal"
              value={row.amount}
              disabled={!editable || isDisallowed(row)}
              status={errs.amount || errs.row ? 'error' : undefined}
              onChange={(e) => updateSixA(index, 'amount', e.target.value)}
              data-testid={`input-6a-amount-${index}`}
            />
            {fieldError(errs.amount, `error-6a-amount-${index}`)}
          </>
        );
      },
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
          onClick={() => removeSixA(index)}
          aria-label="Remove row"
          data-testid={`delete-6a-row-${index}`}
        />
      ),
    },
  ];

  const usedKinds = (rows, exceptIndex) =>
    new Set(rows.filter((_, i) => i !== exceptIndex).map((r) => r.kind).filter(Boolean));

  const preTaxColumns = [
    {
      title: 'Kind',
      key: 'kind',
      render: (_, row, index) => {
        const used = usedKinds(preTaxRows, index);
        const errs = preTaxErrors[index] || {};
        return (
          <>
            <Select
              style={{ minWidth: 240 }}
              placeholder="Pick a kind"
              value={row.kind ?? undefined}
              options={PRE_TAX_KINDS.map((k) => ({ ...k, disabled: used.has(k.value) }))}
              disabled={!editable}
              status={errs.kind ? 'error' : undefined}
              onChange={(val) => updatePreTax(index, 'kind', val)}
              virtual={false}
              data-testid={`input-pretax-kind-${index}`}
            />
            {fieldError(errs.kind, `error-pretax-kind-${index}`)}
            {fieldError(errs.row, `error-pretax-row-${index}`)}
          </>
        );
      },
    },
    {
      title: 'Amount (₹)',
      key: 'amount',
      width: 200,
      render: (_, row, index) => {
        const errs = preTaxErrors[index] || {};
        return (
          <>
            <Input
              inputMode="decimal"
              value={row.amount}
              disabled={!editable}
              status={errs.amount ? 'error' : undefined}
              onChange={(e) => updatePreTax(index, 'amount', e.target.value)}
              data-testid={`input-pretax-amount-${index}`}
            />
            {fieldError(errs.amount, `error-pretax-amount-${index}`)}
          </>
        );
      },
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
          onClick={() => setPreTaxRows((prev) => prev.filter((_, i) => i !== index))}
          aria-label="Remove row"
        />
      ),
    },
  ];

  const prevEmpColumns = [
    {
      title: 'Kind',
      key: 'kind',
      render: (_, row, index) => {
        const officer = row.entered_by === 'OFFICER';
        const used = usedKinds(prevEmpRows, index);
        const errs = prevEmpErrors[index] || {};
        return (
          <Space direction="vertical" size={2}>
            <Space wrap>
              <Select
                style={{ minWidth: 240 }}
                placeholder="Pick a kind"
                value={row.kind ?? undefined}
                options={PREV_EMP_KINDS.map((k) => ({ ...k, disabled: used.has(k.value) }))}
                disabled={!editable || officer}
                status={errs.kind ? 'error' : undefined}
                onChange={(val) => updatePrevEmp(index, 'kind', val)}
                virtual={false}
                data-testid={`input-prevemp-kind-${index}`}
              />
              {officer && (
                <Tag color="purple" data-testid={`officer-badge-${index}`}>
                  Entered by officer
                </Tag>
              )}
            </Space>
            {fieldError(errs.kind, `error-prevemp-kind-${index}`)}
            {fieldError(errs.row, `error-prevemp-row-${index}`)}
          </Space>
        );
      },
    },
    {
      title: 'Employer',
      key: 'employer',
      render: (_, row, index) => {
        const officer = row.entered_by === 'OFFICER';
        return (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Input
              placeholder="Employer name"
              value={row.employer_name}
              disabled={!editable || officer}
              onChange={(e) => updatePrevEmp(index, 'employer_name', e.target.value)}
              data-testid={`input-prevemp-employer-${index}`}
            />
            <Input
              placeholder="Employer TAN (e.g. MUMB12345A)"
              value={row.employer_tan}
              disabled={!editable || officer}
              onChange={(e) => updatePrevEmp(index, 'employer_tan', e.target.value)}
              data-testid={`input-prevemp-tan-${index}`}
            />
          </Space>
        );
      },
    },
    {
      title: 'Amount (₹)',
      key: 'amount',
      width: 180,
      render: (_, row, index) => {
        const errs = prevEmpErrors[index] || {};
        return (
          <>
            <Input
              inputMode="decimal"
              value={row.amount}
              disabled={!editable || row.entered_by === 'OFFICER'}
              status={errs.amount ? 'error' : undefined}
              onChange={(e) => updatePrevEmp(index, 'amount', e.target.value)}
              data-testid={`input-prevemp-amount-${index}`}
            />
            {fieldError(errs.amount, `error-prevemp-amount-${index}`)}
          </>
        );
      },
    },
    {
      title: '',
      key: 'actions',
      width: 50,
      render: (_, row, index) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          disabled={!editable || row.entered_by === 'OFFICER'}
          onClick={() => setPrevEmpRows((prev) => prev.filter((_, i) => i !== index))}
          aria-label="Remove row"
        />
      ),
    },
  ];

  const hasDisallowed = sixARows.some(isDisallowed);

  return (
    <Spin spinning={loading}>
      {error && (
        <Alert
          message="Error"
          description={error}
          type="error"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
          data-testid="deductions-error"
        />
      )}

      {/* ── Section 6A ── */}
      <Card
        type="inner"
        title={
          <Space>
            <AuditOutlined />
            <Text strong>Chapter VI-A (80C, 80D and others)</Text>
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={addSixA}
              disabled={!editable}
              data-testid="add-6a-row-btn"
            >
              Add Row
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saving6a}
              disabled={!editable}
              onClick={handleSave6a}
              data-testid="save-6a-btn"
            >
              Save Chapter VI-A
            </Button>
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          One row per investment or payment. The same item may appear on more than one row; its
          limit applies to the rows together.
        </Paragraph>
        {hasDisallowed && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message={`Some rows are not allowed under the ${regime || 'current'} regime`}
            description="They are marked below. Remove them before saving this part."
            data-testid="disallowed-6a-banner"
          />
        )}
        <Table
          dataSource={sixARows}
          columns={sixAColumns}
          rowKey="key"
          pagination={false}
          size="small"
          onRow={rowProps('6a', sixAErrors)}
          locale={{ emptyText: 'No Chapter VI-A rows. Click "Add Row" to declare one.' }}
        />
      </Card>

      {/* ── Pre-tax ── */}
      <Card
        type="inner"
        title={
          <Space>
            <DollarCircleOutlined />
            <Text strong>Pre-Tax Deductions</Text>
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={() => setPreTaxRows((prev) => [...prev, { key: nextKey(), kind: null, amount: '' }])}
              disabled={!editable}
              data-testid="add-pretax-row-btn"
            >
              Add Row
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingPreTax}
              disabled={!editable}
              onClick={handleSavePreTax}
              data-testid="save-pretax-btn"
            >
              Save Pre-Tax Deductions
            </Button>
          </Space>
        }
        style={{ marginBottom: 24 }}
      >
        <Table
          dataSource={preTaxRows}
          columns={preTaxColumns}
          rowKey="key"
          pagination={false}
          size="small"
          onRow={rowProps('pretax', preTaxErrors)}
          locale={{ emptyText: 'No pre-tax deductions declared.' }}
        />
      </Card>

      {/* ── Previous employment ── */}
      <Card
        type="inner"
        title={
          <Space>
            <IdcardOutlined />
            <Text strong>Previous Employment</Text>
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={() =>
                setPrevEmpRows((prev) => [
                  ...prev,
                  {
                    key: nextKey(),
                    kind: null,
                    amount: '',
                    employer_name: '',
                    employer_tan: '',
                    entered_by: 'EMPLOYEE',
                  },
                ])
              }
              disabled={!editable}
              data-testid="add-prevemp-row-btn"
            >
              Add Row
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingPrevEmp}
              disabled={!editable}
              onClick={handleSavePrevEmployment}
              data-testid="save-prevemp-btn"
            >
              Save Previous Employment
            </Button>
          </Space>
        }
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          If you joined during this financial year, declare the income and deductions from your
          previous employer (Form 12B), one row per kind.
        </Paragraph>
        {officerNotice && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Entered by the payroll officer"
            description={officerNotice}
            data-testid="officer-entered-message"
          />
        )}
        <Table
          dataSource={prevEmpRows}
          columns={prevEmpColumns}
          rowKey="key"
          pagination={false}
          size="small"
          onRow={rowProps('prevemp', prevEmpErrors)}
          locale={{ emptyText: 'No previous employment declared.' }}
        />
      </Card>
    </Spin>
  );
}

DeductionsSection.propTypes = {
  fy: PropTypes.string.isRequired,
  editable: PropTypes.bool,
  onRefresh: PropTypes.func,
  regime: PropTypes.string,
};

export default DeductionsSection;
