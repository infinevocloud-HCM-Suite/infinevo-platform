import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
import { Card, Table, Button, Input, Select, Space, Typography, Alert, Spin } from 'antd';
import { PlusOutlined, DeleteOutlined, SaveOutlined, DollarCircleOutlined } from '@ant-design/icons';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { declarationService } from './declarationService';
import { setSectionData, selectTaxSection, selectTaxFy } from './taxSlice';
import { readError, rowErrorsFrom } from './apiError';

const { Text, Paragraph } = Typography;

// The backend enum OtherIncomeKind; one row per kind.
const OTHER_INCOME_KINDS = [
  { value: 'SAVINGS_INTEREST', label: 'Savings Bank Account Interest' },
  { value: 'FD_INTEREST', label: 'Fixed / Term Deposit Interest' },
  { value: 'NSC_INTEREST', label: 'NSC Interest Accrued' },
  { value: 'OTHER', label: 'Other Taxable Income' },
];

const rowsFrom = (list) =>
  (Array.isArray(list) ? list : []).map((row) => ({
    kind: row.kind ?? null,
    description: row.description ?? '',
    amount: row.amount === null || row.amount === undefined ? '' : String(row.amount),
  }));

const isAmount = (value) => {
  const text = String(value ?? '').trim();
  return text !== '' && Number.isFinite(Number(text)) && Number(text) >= 0;
};

export function OtherIncomeSection({ fy, editable, onRefresh }) {
  const dispatch = useDispatch();
  const stored = useSelector(selectTaxSection('otherIncome'));
  const storedFy = useSelector(selectTaxFy);

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [rows, setRows] = useState(() => rowsFrom(stored));
  const [rowErrors, setRowErrors] = useState({});

  const loadOtherIncome = useCallback(
    async (financialYear) => {
      if (!financialYear) return;
      setLoading(true);
      setError(null);
      try {
        const list = (await declarationService.otherIncome(financialYear)) || [];
        setRows(rowsFrom(list));
        dispatch(setSectionData({ section: 'otherIncome', data: list }));
      } catch (err) {
        setError(readError(err, 'Failed to load other income declarations').message);
      } finally {
        setLoading(false);
      }
    },
    [dispatch],
  );

  // Loaded on first expand: the slice when it holds this FY, else the server.
  const needsLoad = stored == null || (storedFy != null && storedFy !== fy);
  useEffect(() => {
    if (needsLoad) loadOtherIncome(fy);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fy, loadOtherIncome]);

  const handleAddRow = () => {
    setRows((prev) => {
      const used = new Set(prev.map((r) => r.kind));
      const free = OTHER_INCOME_KINDS.find((k) => !used.has(k.value));
      return [...prev, { kind: free ? free.value : null, description: '', amount: '' }];
    });
  };

  const handleUpdateRow = (index, field, value) => {
    setRows((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
  };

  const handleRemoveRow = (index) => {
    setRows((prev) => prev.filter((_, i) => i !== index));
    setRowErrors({});
  };

  const handleSave = async () => {
    setError(null);
    const errors = {};
    rows.forEach((row, i) => {
      if (!row.kind) errors[i] = 'Pick an income source';
      else if (!isAmount(row.amount)) errors[i] = 'Enter an amount of 0 or more';
      else if (row.kind === 'OTHER' && !String(row.description || '').trim()) {
        errors[i] = 'Description is required for other income';
      }
    });
    setRowErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    try {
      const payload = rows.map((row) => ({
        kind: row.kind,
        description: String(row.description || '').trim(),
        amount: Number(row.amount),
      }));
      const saved = await declarationService.saveOtherIncome(fy, payload);
      if (Array.isArray(saved)) {
        setRows(rowsFrom(saved));
        dispatch(setSectionData({ section: 'otherIncome', data: saved }));
      }
      dispatch(setSectionData({ section: 'summary', data: null }));
      successMsg('Other Income Saved', 'Other income declarations saved.');
      if (onRefresh) onRefresh();
    } catch (err) {
      const info = readError(err, 'Failed to save other income');
      setRowErrors(rowErrorsFrom(err, 'row'));
      setError(info.message);
      errorMsg(info);
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: 'Income Source',
      key: 'kind',
      width: 280,
      render: (_, record, index) => {
        const used = new Set(rows.filter((_, i) => i !== index).map((r) => r.kind));
        return (
          <>
            <Select
              style={{ width: '100%' }}
              placeholder="Pick an income source"
              options={OTHER_INCOME_KINDS.map((k) => ({ ...k, disabled: used.has(k.value) }))}
              value={record.kind ?? undefined}
              disabled={!editable}
              status={rowErrors[index] ? 'error' : undefined}
              onChange={(val) => handleUpdateRow(index, 'kind', val)}
              virtual={false}
              data-testid={`select-other-income-kind-${index}`}
            />
            {rowErrors[index] && (
              <Text type="danger" style={{ fontSize: 12 }} data-testid={`other-income-row-error-${index}`}>
                {rowErrors[index]}
              </Text>
            )}
          </>
        );
      },
    },
    {
      title: 'Description / Particulars',
      key: 'description',
      render: (_, record, index) => (
        <Input
          placeholder="e.g. HDFC Bank Savings Interest"
          value={record.description}
          disabled={!editable}
          onChange={(e) => handleUpdateRow(index, 'description', e.target.value)}
          data-testid={`input-other-income-desc-${index}`}
        />
      ),
    },
    {
      title: 'Declared Amount (₹)',
      key: 'amount',
      width: 220,
      render: (_, record, index) => (
        <Input
          inputMode="decimal"
          value={record.amount}
          disabled={!editable}
          onChange={(e) => handleUpdateRow(index, 'amount', e.target.value)}
          data-testid={`input-other-income-amount-${index}`}
        />
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 60,
      render: (_, __, index) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          disabled={!editable}
          onClick={() => handleRemoveRow(index)}
          aria-label="Remove row"
          data-testid={`delete-other-income-row-${index}`}
        />
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      {error && (
        <Alert message="Error" description={error} type="error" showIcon style={{ marginBottom: 16 }} />
      )}

      <Card
        type="inner"
        title={
          <Space align="center">
            <DollarCircleOutlined style={{ color: '#1677ff' }} />
            <Text strong>Income from Other Sources</Text>
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={handleAddRow}
              disabled={!editable}
              data-testid="add-other-income-btn"
            >
              Add Income Source
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saving}
              disabled={!editable}
              onClick={handleSave}
              data-testid="save-other-income-btn"
            >
              Save Other Income
            </Button>
          </Space>
        }
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          Declare interest from savings accounts and deposits, and any other income liable to tax.
        </Paragraph>

        <Table
          dataSource={rows.map((r, i) => ({ ...r, key: i }))}
          columns={columns}
          pagination={false}
          locale={{ emptyText: 'No other income sources declared. Click "Add Income Source" to declare.' }}
          size="middle"
        />
      </Card>
    </Spin>
  );
}

OtherIncomeSection.propTypes = {
  fy: PropTypes.string.isRequired,
  editable: PropTypes.bool,
  onRefresh: PropTypes.func,
};

export default OtherIncomeSection;
