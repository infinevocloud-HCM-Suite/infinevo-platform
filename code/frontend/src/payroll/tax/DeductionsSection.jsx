import { useState, useEffect, useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Table,
  Button,
  Input,
  InputNumber,
  Space,
  Typography,
  Alert,
  Spin,
  Row,
  Col,
  Tag,
  message,
  Tabs,
  Collapse,
} from 'antd';
import {
  SaveOutlined,
  AuditOutlined,
  DollarCircleOutlined,
  IdcardOutlined,
} from '@ant-design/icons';
import { declarationService } from './declarationService';
import { store } from '@shell/store';
import { setItems, setSectionData } from './taxSlice';

const { Text, Paragraph } = Typography;

const PRE_TAX_KINDS = [
  { kind: 'VPF', label: 'Voluntary Provident Fund (VPF)', description: 'Voluntary extra PF contributions' },
  { kind: 'EMPLOYEE_PF', label: 'Employee PF (Pre-tax)', description: 'Statutory employee PF deduction' },
  { kind: 'NPS_EMPLOYEE', label: 'Employee NPS (Sec 80CCD 1B)', description: 'Additional NPS contribution up to ₹50,000' },
  { kind: 'PROFESSIONAL_TAX', label: 'Professional Tax (PT)', description: 'State professional tax deductions' },
];

const PREV_EMP_KINDS = [
  { kind: 'INCOME', label: 'Gross Salary from Previous Employer' },
  { kind: 'INCOME_TAX_DEDUCTED', label: 'TDS Deducted by Previous Employer' },
  { kind: 'EMPLOYEE_PF', label: 'PF Deducted by Previous Employer' },
  { kind: 'PROFESSIONAL_TAX', label: 'Professional Tax Deducted' },
];

function extractFieldErrors(err) {
  const data = err?.response?.data || err?.data;
  const map = data?.errors || data?.fieldErrors || data?.field_errors;
  if (map && typeof map === 'object' && !Array.isArray(map)) {
    return map;
  }
  return {};
}

export function DeductionsSection({ fy, editable, onRefresh }) {
  const [loading, setLoading] = useState(false);
  const [saving6a, setSaving6a] = useState(false);
  const [savingPreTax, setSavingPreTax] = useState(false);
  const [savingPrevEmp, setSavingPrevEmp] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  // 6A catalogue items & declarations
  const [catalogueItems, setCatalogueItems] = useState([]);
  const [declared6a, setDeclared6a] = useState({}); // map item_id -> { amount, description }
  const [activeCollapseKeys, setActiveCollapseKeys] = useState([]);

  // Pre-tax deductions map
  const [preTaxAmounts, setPreTaxAmounts] = useState({});

  // Previous employment details
  const [prevEmployerName, setPrevEmployerName] = useState('');
  const [prevEmployerTan, setPrevEmployerTan] = useState('');
  const [prevEmpAmounts, setPrevEmpAmounts] = useState({});
  const [prevEmpEnteredBy, setPrevEmpEnteredBy] = useState({});

  const loadData = useCallback(async (financialYear) => {
    if (!financialYear) return;
    setLoading(true);
    setError(null);
    setFieldErrors({});
    try {
      const [itemsRes, deductionsRes] = await Promise.all([
        declarationService.items(financialYear),
        declarationService.deductions(financialYear),
      ]);

      const items = itemsRes || [];
      setCatalogueItems(items);
      store.dispatch(setItems(items));
      store.dispatch(setSectionData({ section: 'deductions', data: deductionsRes || null }));
      const groups = [
        ...new Set(
          items.map((it) => it.category_group_code || it.section_code || 'OTHER'),
        ),
      ];
      setActiveCollapseKeys(groups);

      // Populate declared 6A
      const map6a = {};
      (deductionsRes?.section6a || []).forEach((line) => {
        const itemId = line.section6a_item_id || line.id;
        if (itemId) {
          map6a[itemId] = {
            amount: Number(line.amount) || 0,
            description: line.description || '',
          };
        }
      });
      setDeclared6a(map6a);

      // Populate pre-tax
      const mapPreTax = {};
      (deductionsRes?.pre_tax_deductions || []).forEach((pt) => {
        if (pt.kind) {
          mapPreTax[pt.kind] = Number(pt.amount) || 0;
        }
      });
      setPreTaxAmounts(mapPreTax);

      // Populate previous employment
      const mapPrevEmp = {};
      const mapEnteredBy = {};
      let empName = '';
      let empTan = '';
      (deductionsRes?.previous_employment || []).forEach((pe) => {
        if (pe.kind) {
          mapPrevEmp[pe.kind] = Number(pe.amount) || 0;
          if (pe.entered_by) {
            mapEnteredBy[pe.kind] = pe.entered_by;
          }
        }
        if (pe.employer_name && !empName) empName = pe.employer_name;
        if (pe.employer_tan && !empTan) empTan = pe.employer_tan;
      });
      setPrevEmployerName(empName);
      setPrevEmployerTan(empTan);
      setPrevEmpAmounts(mapPrevEmp);
      setPrevEmpEnteredBy(mapEnteredBy);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load deductions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData(fy);
  }, [fy, loadData]);

  // Group catalogue items by category_group_code (falling back to section_code or OTHER)
  const itemsByGroup = useMemo(() => {
    const groups = {};
    catalogueItems.forEach((item) => {
      const g = item.category_group_code || item.section_code || 'OTHER';
      if (!groups[g]) groups[g] = [];
      groups[g].push(item);
    });
    return groups;
  }, [catalogueItems]);

  const handleSaveError = (err, fallbackMsg) => {
    const code = err?.response?.data?.code || err?.code;
    const fErrors = extractFieldErrors(err);
    setFieldErrors(fErrors);
    let msg = err?.response?.data?.message || err?.message || fallbackMsg;
    if (code === 'OFFICER_ENTERED') {
      msg = err?.response?.data?.message || 'Officer-entered previous employment rows cannot be modified by employee.';
    } else if (code === 'NOT_EDITABLE') {
      msg = err?.response?.data?.message || 'Declaration is not editable right now.';
    }
    setError(msg);
    message.error(msg);
  };

  // Save 6A
  const handleSave6a = async () => {
    setSaving6a(true);
    setError(null);
    setFieldErrors({});
    try {
      const payload = Object.entries(declared6a)
        .filter(([, val]) => val && Number(val.amount) > 0)
        .map(([itemId, val]) => ({
          section6a_item_id: itemId,
          amount: Number(val.amount) || 0,
          description: val.description || '',
        }));

      await declarationService.save6a(fy, payload);
      message.success('Section 6A declarations saved successfully');
      if (onRefresh) onRefresh();
    } catch (err) {
      handleSaveError(err, 'Failed to save Section 6A');
    } finally {
      setSaving6a(false);
    }
  };

  // Save Pre-Tax
  const handleSavePreTax = async () => {
    setSavingPreTax(true);
    setError(null);
    setFieldErrors({});
    try {
      const payload = Object.entries(preTaxAmounts)
        .filter(([, amt]) => Number(amt) > 0)
        .map(([kind, amt]) => ({
          kind,
          amount: Number(amt) || 0,
        }));

      await declarationService.savePreTax(fy, payload);
      message.success('Pre-tax deductions saved successfully');
      if (onRefresh) onRefresh();
    } catch (err) {
      handleSaveError(err, 'Failed to save pre-tax deductions');
    } finally {
      setSavingPreTax(false);
    }
  };

  // Save Previous Employment
  const handleSavePrevEmployment = async () => {
    setSavingPrevEmp(true);
    setError(null);
    setFieldErrors({});
    try {
      const payload = Object.entries(prevEmpAmounts)
        .filter(([kind, amt]) => Number(amt) > 0 && prevEmpEnteredBy[kind] !== 'OFFICER')
        .map(([kind, amt]) => ({
          kind,
          amount: Number(amt) || 0,
          employer_name: prevEmployerName || '',
          employer_tan: (prevEmployerTan || '').toUpperCase().trim(),
        }));

      await declarationService.savePrevEmployment(fy, payload);
      message.success('Previous employment details saved successfully');
      if (onRefresh) onRefresh();
    } catch (err) {
      handleSaveError(err, 'Failed to save previous employment');
    } finally {
      setSavingPrevEmp(false);
    }
  };

  const handleAmountChange6a = (itemId, amount) => {
    setDeclared6a((prev) => ({
      ...prev,
      [itemId]: { ...(prev[itemId] || {}), amount },
    }));
  };

  const collapseItems = Object.entries(itemsByGroup).map(([group, items]) => {
    return {
      key: group,
      label: (
        <Row justify="space-between" align="middle" style={{ width: '100%', paddingRight: 16 }}>
          <Col>
            <Space>
              <Text strong style={{ fontSize: 15 }}>
                Group {group}
              </Text>
            </Space>
          </Col>
        </Row>
      ),
      children: (
        <Table
          dataSource={items.map((it) => ({ ...it, key: it.id }))}
          pagination={false}
          size="small"
          columns={[
            {
              title: 'Deduction Item',
              key: 'name',
              render: (_, record) => (
                <div>
                  <Space size="small">
                    <Text strong>{record.name || record.section_code || record.code}</Text>
                    {record.section_code && (
                      <Tag>{record.section_code}</Tag>
                    )}
                    {record.max_limit != null && (
                      <Tag color="blue" data-testid={`item-cap-${record.section_code || record.code || record.id}`}>
                        Max Limit: ₹ {Number(record.max_limit).toLocaleString('en-IN')}
                      </Tag>
                    )}
                  </Space>
                  {record.description && (
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {record.description}
                      </Text>
                    </div>
                  )}
                </div>
              ),
            },
            {
              title: 'Declared Amount (₹)',
              key: 'amount',
              width: 220,
              render: (_, record) => {
                const rowErr =
                  fieldErrors[record.id] ||
                  fieldErrors[record.section_code] ||
                  fieldErrors[`items.${record.id}`];
                return (
                  <div>
                    <InputNumber
                      style={{ width: '100%' }}
                      min={0}
                      step={5000}
                      status={rowErr ? 'error' : undefined}
                      value={declared6a[record.id]?.amount || 0}
                      disabled={!editable}
                      formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                      parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
                      onChange={(val) => handleAmountChange6a(record.id, val)}
                      data-testid={`input-6a-${record.section_code || record.code || record.id}`}
                    />
                    {rowErr && (
                      <Text type="danger" style={{ fontSize: 12 }}>
                        {rowErr}
                      </Text>
                    )}
                  </div>
                );
              },
            },
          ]}
        />
      ),
    };
  });

  const subTabs = [
    {
      key: 'sec6a',
      label: (
        <span>
          <AuditOutlined /> Chapter VI-A (80C, 80D, etc.)
        </span>
      ),
      children: (
        <Card
          type="inner"
          extra={
            editable && (
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving6a}
                onClick={handleSave6a}
                data-testid="save-6a-btn"
              >
                Save Chapter VI-A
              </Button>
            )
          }
        >
          <Paragraph type="secondary" style={{ marginBottom: 16 }}>
            Declare investments under Section 80C (PPF, EPF, ELSS, Life Insurance), 80D (Health Insurance), 80CCD, and other eligible categories.
          </Paragraph>
          <Collapse activeKey={activeCollapseKeys} onChange={setActiveCollapseKeys} items={collapseItems} />
        </Card>
      ),
    },
    {
      key: 'preTax',
      label: (
        <span>
          <DollarCircleOutlined /> Pre-Tax Deductions
        </span>
      ),
      children: (
        <Card
          type="inner"
          extra={
            editable && (
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={savingPreTax}
                onClick={handleSavePreTax}
                data-testid="save-pretax-btn"
              >
                Save Pre-Tax Deductions
              </Button>
            )
          }
        >
          <Paragraph type="secondary" style={{ marginBottom: 20 }}>
            Declare pre-tax deductions that reduce gross taxable income prior to tax computation.
          </Paragraph>
          <Row gutter={[24, 16]}>
            {PRE_TAX_KINDS.map((pt) => (
              <Col xs={24} sm={12} key={pt.kind}>
                <Card size="small" type="inner">
                  <Text strong>{pt.label}</Text>
                  <div style={{ marginBottom: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {pt.description}
                    </Text>
                  </div>
                  <InputNumber
                    style={{ width: '100%' }}
                    min={0}
                    step={1000}
                    value={preTaxAmounts[pt.kind] || 0}
                    disabled={!editable}
                    formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                    parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
                    onChange={(val) =>
                      setPreTaxAmounts((prev) => ({ ...prev, [pt.kind]: val }))
                    }
                    data-testid={`input-pretax-${pt.kind}`}
                  />
                </Card>
              </Col>
            ))}
          </Row>
        </Card>
      ),
    },
    {
      key: 'prevEmp',
      label: (
        <span>
          <IdcardOutlined /> Previous Employment
        </span>
      ),
      children: (
        <Card
          type="inner"
          extra={
            editable && (
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={savingPrevEmp}
                onClick={handleSavePrevEmployment}
                data-testid="save-prevemp-btn"
              >
                Save Previous Employment
              </Button>
            )
          }
        >
          <Paragraph type="secondary" style={{ marginBottom: 20 }}>
            If you joined during the current financial year, furnish details of income received and tax deducted by your previous employer (from Form 12B).
          </Paragraph>

          <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
            <Col xs={24} sm={12}>
              <Text strong>Previous Employer Name:</Text>
              <Input
                placeholder="e.g. Acme Corporation Pvt Ltd"
                value={prevEmployerName}
                disabled={!editable}
                onChange={(e) => setPrevEmployerName(e.target.value)}
                data-testid="prev-emp-name-input"
              />
            </Col>
            <Col xs={24} sm={12}>
              <Text strong>Employer TAN:</Text>
              <Input
                placeholder="e.g. MUMB12345A"
                value={prevEmployerTan}
                disabled={!editable}
                onChange={(e) => setPrevEmployerTan(e.target.value)}
                data-testid="prev-emp-tan-input"
              />
            </Col>
          </Row>

          <Row gutter={[24, 16]}>
            {PREV_EMP_KINDS.map((pe) => {
              const isOfficerEntered = prevEmpEnteredBy[pe.kind] === 'OFFICER';
              const rowErr = fieldErrors[pe.kind] || fieldErrors[`previous_employment.${pe.kind}`];
              return (
                <Col xs={24} sm={12} key={pe.kind}>
                  <Card size="small" type="inner">
                    <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Text strong>{pe.label}</Text>
                      {isOfficerEntered && (
                        <Tag color="purple" data-testid={`officer-badge-${pe.kind}`}>
                          Entered by Officer
                        </Tag>
                      )}
                    </Space>
                    <InputNumber
                      style={{ width: '100%', marginTop: 8 }}
                      min={0}
                      step={5000}
                      status={rowErr ? 'error' : undefined}
                      value={prevEmpAmounts[pe.kind] || 0}
                      disabled={!editable || isOfficerEntered}
                      formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                      parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
                      onChange={(val) =>
                        setPrevEmpAmounts((prev) => ({ ...prev, [pe.kind]: val }))
                      }
                      data-testid={`input-prevemp-${pe.kind}`}
                    />
                    {rowErr && (
                      <Text type="danger" style={{ fontSize: 12 }}>
                        {rowErr}
                      </Text>
                    )}
                  </Card>
                </Col>
              );
            })}
          </Row>
        </Card>
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      {error && (
        <Alert
          message="Error"
          description={
            Object.keys(fieldErrors).length > 0 ? (
              <div>
                <div>{error}</div>
                <ul style={{ margin: '8px 0 0 16px', padding: 0 }}>
                  {Object.entries(fieldErrors).map(([field, msg]) => (
                    <li key={field} data-testid={`field-error-${field}`}>
                      <strong>{field}:</strong> {msg}
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              error
            )
          }
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}
      <Tabs defaultActiveKey="sec6a" items={subTabs} />
    </Spin>
  );
}

DeductionsSection.propTypes = {
  fy: PropTypes.string.isRequired,
  editable: PropTypes.bool,
  onRefresh: PropTypes.func,
};

export default DeductionsSection;
