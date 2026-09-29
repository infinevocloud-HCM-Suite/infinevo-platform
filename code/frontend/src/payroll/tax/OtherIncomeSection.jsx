import { useState, useEffect, useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Table,
  Button,
  Input,
  InputNumber,
  Select,
  Space,
  Typography,
  Alert,
  Spin,
  Row,
  Col,
  Tag,
  message,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  SaveOutlined,
  DollarCircleOutlined,
} from '@ant-design/icons';
import { declarationService } from './declarationService';

const { Text, Paragraph } = Typography;

const OTHER_INCOME_KINDS = [
  { value: 'SAVINGS_INTEREST', label: 'Savings Bank Account Interest' },
  { value: 'FD_INTEREST', label: 'Fixed / Term Deposit Interest' },
  { value: 'NSC_INTEREST', label: 'NSC Interest Accrued' },
  { value: 'OTHER', label: 'Other Taxable Income' },
];

export function OtherIncomeSection({ fy, editable, onRefresh }) {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [rows, setRows] = useState([]);

  const loadOtherIncome = useCallback(async (financialYear) => {
    if (!financialYear) return;
    setLoading(true);
    setError(null);
    try {
      const data = await declarationService.otherIncome(financialYear);
      setRows(data || []);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load other income declarations');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOtherIncome(fy);
  }, [fy, loadOtherIncome]);

  const totalOtherIncome = useMemo(() => {
    return rows.reduce((acc, row) => acc + (Number(row.amount) || 0), 0);
  }, [rows]);

  const handleAddRow = () => {
    setRows((prev) => [
      ...prev,
      {
        kind: 'SAVINGS_INTEREST',
        description: '',
        amount: 0,
      },
    ]);
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
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = rows.map((row) => ({
        kind: row.kind || 'OTHER',
        description: row.description || '',
        amount: Number(row.amount) || 0,
      }));

      await declarationService.saveOtherIncome(fy, payload);
      message.success('Other income declarations saved successfully');
      if (onRefresh) onRefresh();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to save other income');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: 'Income Source',
      key: 'kind',
      width: 280,
      render: (_, record, index) => (
        <Select
          style={{ width: '100%' }}
          options={OTHER_INCOME_KINDS}
          value={record.kind}
          disabled={!editable}
          onChange={(val) => handleUpdateRow(index, 'kind', val)}
          data-testid={`select-other-income-kind-${index}`}
        />
      ),
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
        <InputNumber
          style={{ width: '100%' }}
          min={0}
          step={1000}
          value={record.amount}
          disabled={!editable}
          formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
          parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
          onChange={(val) => handleUpdateRow(index, 'amount', val)}
          data-testid={`input-other-income-amount-${index}`}
        />
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 60,
      render: (_, __, index) =>
        editable && (
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleRemoveRow(index)}
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
            {editable && (
              <>
                <Button icon={<PlusOutlined />} onClick={handleAddRow} data-testid="add-other-income-btn">
                  Add Income Source
                </Button>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  loading={saving}
                  onClick={handleSave}
                  data-testid="save-other-income-btn"
                >
                  Save Other Income
                </Button>
              </>
            )}
          </Space>
        }
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          Furnish interest earned from savings bank accounts, fixed deposits, dividends, or any other income liable to income tax.
        </Paragraph>

        <Table
          dataSource={rows.map((r, i) => ({ ...r, key: i }))}
          columns={columns}
          pagination={false}
          locale={{ emptyText: 'No other income sources declared. Click "Add Income Source" to declare.' }}
          size="middle"
          footer={() => (
            <Row justify="space-between" align="middle">
              <Col>
                <Text strong>Total Other Income Declared:</Text>
              </Col>
              <Col>
                <Tag color={totalOtherIncome > 0 ? 'green' : 'default'} style={{ fontSize: 14, padding: '4px 10px' }}>
                  ₹ {totalOtherIncome.toLocaleString('en-IN')}
                </Tag>
              </Col>
            </Row>
          )}
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
