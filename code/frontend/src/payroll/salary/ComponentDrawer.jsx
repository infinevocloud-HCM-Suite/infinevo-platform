import React from 'react';
import PropTypes from 'prop-types';
import { useDispatch } from 'react-redux';
import {
  Drawer,
  Form,
  Input,
  Select,
  Checkbox,
  Radio,
  Collapse,
  Button,
  Space,
  Row,
  Col,
  Divider,
} from 'antd';
import { Formik } from 'formik';
import {
  componentSchemas,
  defaultInitialValues,
  normaliseInitialValues,
  buildPayload,
  STATUTORY_FIELDS,
  CALCULATION_TYPES,
  PERCENTAGE_OF_OPTIONS,
  EARNING_TYPES,
  EARNING_FREQUENCIES,
  EPF_INCLUSION_OPTIONS,
  CARRY_FORWARD_OPTIONS,
} from './componentFields.js';
import { componentService } from './componentService.js';
import { invalidateComponents } from './salarySlice.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const STATUTORY_KEY = 'statutory';

function asString(value) {
  return value != null ? String(value) : '';
}

/**
 * The kind's statutory and tax options (D-38): EPF inclusion for earnings, loan terms for
 * deductions, superannuation and exemption for benefits, carry-forward and opt-in for reimbursements.
 */
function StatutoryFields({ kind, values, errors, touched, handleChange, handleBlur, setFieldValue }) {
  const fieldError = (name) => (touched[name] && errors[name] ? errors[name] : undefined);

  if (kind === 'earnings') {
    return (
      <Form.Item label="EPF inclusion">
        <Radio.Group
          aria-label="EPF inclusion"
          value={values.epfInclusionType}
          onChange={(e) => {
            setFieldValue('epfInclusionType', e.target.value);
            setFieldValue('includedInEpf', e.target.value !== 'NEVER');
          }}
        >
          <Space direction="vertical">
            {EPF_INCLUSION_OPTIONS.map((o) => (
              <Radio key={o.value} value={o.value}>
                {o.label}
              </Radio>
            ))}
          </Space>
        </Radio.Group>
      </Form.Item>
    );
  }

  if (kind === 'deductions') {
    return (
      <>
        <Form.Item
          label="EMI Type"
          validateStatus={fieldError('emiType') ? 'error' : ''}
          help={fieldError('emiType')}
        >
          <Input
            name="emiType"
            value={values.emiType || ''}
            placeholder="e.g. REDUCING_BALANCE"
            onChange={handleChange}
            onBlur={handleBlur}
          />
        </Form.Item>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="Perquisite Interest Rate (%)"
              validateStatus={fieldError('perquisiteInterestRate') ? 'error' : ''}
              help={fieldError('perquisiteInterestRate')}
            >
              <Input
                name="perquisiteInterestRate"
                value={values.perquisiteInterestRate || ''}
                placeholder="Perquisite rate"
                onChange={handleChange}
                onBlur={handleBlur}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="EMI Interest Rate (%)"
              validateStatus={fieldError('emiInterestRate') ? 'error' : ''}
              help={fieldError('emiInterestRate')}
            >
              <Input
                name="emiInterestRate"
                value={values.emiInterestRate || ''}
                placeholder="EMI rate"
                onChange={handleChange}
                onBlur={handleBlur}
              />
            </Form.Item>
          </Col>
        </Row>
      </>
    );
  }

  if (kind === 'benefits') {
    return (
      <>
        <Space direction="vertical" style={{ width: '100%', marginBottom: 12 }}>
          <Checkbox
            checked={values.superannuation}
            onChange={(e) => setFieldValue('superannuation', e.target.checked)}
          >
            Consider this a superannuation fund
          </Checkbox>
          <Checkbox
            checked={values.oneTime}
            onChange={(e) => setFieldValue('oneTime', e.target.checked)}
          >
            One-time benefit
          </Checkbox>
          <Checkbox
            checked={values.allowsEmployerContribution}
            onChange={(e) => setFieldValue('allowsEmployerContribution', e.target.checked)}
          >
            Allows employer contribution
          </Checkbox>
          <Checkbox
            checked={values.allowsEmployeeContribution}
            onChange={(e) => setFieldValue('allowsEmployeeContribution', e.target.checked)}
          >
            Allows employee contribution
          </Checkbox>
        </Space>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="Tax Exempt Section"
              validateStatus={fieldError('taxExemptSection') ? 'error' : ''}
              help={fieldError('taxExemptSection')}
            >
              <Input
                name="taxExemptSection"
                value={values.taxExemptSection || ''}
                placeholder="e.g. 80C"
                onChange={handleChange}
                onBlur={handleBlur}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="Exemption Sub Type"
              validateStatus={fieldError('taxExemptionSubType') ? 'error' : ''}
              help={fieldError('taxExemptionSubType')}
            >
              <Input
                name="taxExemptionSubType"
                value={values.taxExemptionSubType || ''}
                placeholder="Optional"
                onChange={handleChange}
                onBlur={handleBlur}
              />
            </Form.Item>
          </Col>
        </Row>
      </>
    );
  }

  return (
    <>
      <Form.Item label="Unclaimed amount">
        <Radio.Group
          aria-label="Unclaimed amount"
          value={values.carryForwardOption}
          onChange={(e) => setFieldValue('carryForwardOption', e.target.value)}
        >
          <Space direction="vertical">
            {CARRY_FORWARD_OPTIONS.map((o) => (
              <Radio key={o.value} value={o.value}>
                {o.label}
              </Radio>
            ))}
          </Space>
        </Radio.Group>
      </Form.Item>
      <Checkbox checked={values.optIn} onChange={(e) => setFieldValue('optIn', e.target.checked)}>
        Employees opt in to this reimbursement
      </Checkbox>
    </>
  );
}

StatutoryFields.propTypes = {
  kind: PropTypes.oneOf(['earnings', 'deductions', 'benefits', 'reimbursements']).isRequired,
  values: PropTypes.object.isRequired,
  errors: PropTypes.object.isRequired,
  touched: PropTypes.object.isRequired,
  handleChange: PropTypes.func.isRequired,
  handleBlur: PropTypes.func.isRequired,
  setFieldValue: PropTypes.func.isRequired,
};

export function ComponentDrawer({
  open,
  onClose,
  onSuccess,
  kind = 'earnings',
  initialData = null,
  initialValues: propInitialValues = null,
}) {
  const dispatch = useDispatch();
  const effectiveInitial = initialData || propInitialValues;
  const isEdit = Boolean(effectiveInitial?.id);
  const schema = componentSchemas[kind];
  const [statutoryOpen, setStatutoryOpen] = React.useState(false);

  const initialValues = React.useMemo(() => {
    if (effectiveInitial) {
      const values = normaliseInitialValues(kind, effectiveInitial);
      return {
        ...values,
        defaultValue: asString(effectiveInitial.defaultValue),
        maxLimit: asString(effectiveInitial.maxLimit),
        perquisiteInterestRate: asString(effectiveInitial.perquisiteInterestRate),
        emiInterestRate: asString(effectiveInitial.emiInterestRate),
      };
    }
    return defaultInitialValues[kind];
  }, [effectiveInitial, kind]);

  const handleSubmit = async (values, { setSubmitting }) => {
    try {
      const payload = buildPayload(kind, values);

      if (isEdit) {
        await componentService.update(kind, effectiveInitial.id, payload);
        await successMsg('Success', 'Component updated successfully');
      } else {
        await componentService.create(kind, payload);
        await successMsg('Success', 'Component created successfully');
      }
      dispatch(invalidateComponents());
      onSuccess?.();
      onClose?.();
    } catch (err) {
      errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title={isEdit ? `Edit ${kind.slice(0, -1)}` : `Add ${kind.slice(0, -1)}`}
      width={560}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <Formik
        initialValues={initialValues}
        validationSchema={schema}
        enableReinitialize
        onSubmit={handleSubmit}
      >
        {({
          values,
          errors,
          touched,
          handleChange,
          handleBlur,
          handleSubmit: formikSubmit,
          isSubmitting,
          setFieldValue,
          setFieldTouched,
          submitCount,
        }) => {
          // A failed submit opens the group when one of its fields is in error, so the message is seen.
          const statutoryHasError =
            submitCount > 0 && STATUTORY_FIELDS[kind].some((field) => Boolean(errors[field]));
          return (
            <Form layout="vertical" onFinish={formikSubmit}>
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    label="Code"
                    required
                    validateStatus={touched.code && errors.code ? 'error' : ''}
                    help={touched.code && errors.code}
                  >
                    <Input
                      name="code"
                      value={values.code}
                      disabled={isEdit}
                      placeholder="e.g. BASIC"
                      onChange={handleChange}
                      onBlur={handleBlur}
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    label="Name"
                    required
                    validateStatus={touched.name && errors.name ? 'error' : ''}
                    help={touched.name && errors.name}
                  >
                    <Input
                      name="name"
                      value={values.name}
                      placeholder="e.g. Basic Salary"
                      onChange={handleChange}
                      onBlur={handleBlur}
                    />
                  </Form.Item>
                </Col>
              </Row>
  
              <Form.Item
                label="Display Name"
                validateStatus={touched.displayName && errors.displayName ? 'error' : ''}
                help={touched.displayName && errors.displayName}
              >
                <Input
                  name="displayName"
                  value={values.displayName || ''}
                  placeholder="Name shown on payslips"
                  onChange={handleChange}
                  onBlur={handleBlur}
                />
              </Form.Item>
  
              {kind === 'earnings' && (
                <>
                  <Form.Item
                    label="Earning Type"
                    required
                    validateStatus={touched.earningType && errors.earningType ? 'error' : ''}
                    help={touched.earningType && errors.earningType}
                  >
                    <Select
                      aria-label="Earning type"
                      showSearch
                      optionFilterProp="label"
                      placeholder="Search earning types"
                      value={values.earningType || undefined}
                      options={EARNING_TYPES}
                      onChange={(val) => setFieldValue('earningType', val)}
                      onBlur={() => setFieldTouched('earningType', true)}
                    />
                  </Form.Item>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Space direction="vertical" style={{ marginBottom: 12 }}>
                        <Checkbox
                          checked={values.variable}
                          onChange={(e) => {
                            setFieldValue('variable', e.target.checked);
                            if (!e.target.checked) setFieldValue('oneTime', false);
                          }}
                        >
                          Variable
                        </Checkbox>
                        {values.variable && (
                          <Checkbox
                            checked={values.oneTime}
                            onChange={(e) => setFieldValue('oneTime', e.target.checked)}
                          >
                            One-time payment
                          </Checkbox>
                        )}
                      </Space>
                    </Col>
                    {values.variable && (
                      <Col span={12}>
                        <Form.Item
                          label="Frequency"
                          required
                          validateStatus={errors.earningFrequency && submitCount > 0 ? 'error' : ''}
                          help={submitCount > 0 && errors.earningFrequency}
                        >
                          <Select
                            aria-label="Frequency"
                            value={values.earningFrequency}
                            options={EARNING_FREQUENCIES}
                            onChange={(val) => setFieldValue('earningFrequency', val)}
                          />
                        </Form.Item>
                      </Col>
                    )}
                  </Row>
                </>
              )}
  
              {kind === 'deductions' && (
                <Form.Item label="Deduction Type" required>
                  <Select
                    value={values.deductionType}
                    options={[
                      { label: 'Post-Tax', value: 'POST_TAX' },
                      { label: 'Pre-Tax', value: 'PRE_TAX' },
                    ]}
                    onChange={(val) => setFieldValue('deductionType', val)}
                  />
                </Form.Item>
              )}
  
              {kind === 'benefits' && (
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item label="Benefit Plan">
                      <Input
                        name="benefitPlan"
                        value={values.benefitPlan || ''}
                        placeholder="e.g. Health"
                        onChange={handleChange}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item label="Benefit Category">
                      <Input
                        name="benefitCategory"
                        value={values.benefitCategory || ''}
                        placeholder="e.g. Medical"
                        onChange={handleChange}
                      />
                    </Form.Item>
                  </Col>
                </Row>
              )}
  
              {kind === 'reimbursements' && (
                <Form.Item label="Reimbursement Type" required>
                  <Input
                    name="reimbursementType"
                    value={values.reimbursementType}
                    placeholder="e.g. TRAVEL, MEDICAL"
                    onChange={handleChange}
                  />
                </Form.Item>
              )}
  
              <Divider style={{ margin: '12px 0' }} />
  
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    label="Calculation Type"
                    required
                    validateStatus={touched.calculationType && errors.calculationType ? 'error' : ''}
                    help={touched.calculationType && errors.calculationType}
                  >
                    <Select
                      value={values.calculationType}
                      options={CALCULATION_TYPES}
                      onChange={(val) => {
                        setFieldValue('calculationType', val);
                        if (val !== 'PERCENTAGE') {
                          setFieldValue('percentageOf', null);
                        }
                      }}
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    label="Default Value"
                    required
                    validateStatus={touched.defaultValue && errors.defaultValue ? 'error' : ''}
                    help={touched.defaultValue && errors.defaultValue}
                  >
                    <Input
                      name="defaultValue"
                      value={values.defaultValue}
                      placeholder="0.0000"
                      onChange={handleChange}
                      onBlur={handleBlur}
                    />
                  </Form.Item>
                </Col>
              </Row>
  
              {values.calculationType === 'PERCENTAGE' && (
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item
                      label="Percentage Of"
                      required
                      validateStatus={touched.percentageOf && errors.percentageOf ? 'error' : ''}
                      help={touched.percentageOf && errors.percentageOf}
                    >
                      <Select
                        value={values.percentageOf}
                        options={PERCENTAGE_OF_OPTIONS}
                        placeholder="Select base figure"
                        onChange={(val) => setFieldValue('percentageOf', val)}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      label="Maximum Limit"
                      validateStatus={touched.maxLimit && errors.maxLimit ? 'error' : ''}
                      help={touched.maxLimit && errors.maxLimit}
                    >
                      <Input
                        name="maxLimit"
                        value={values.maxLimit || ''}
                        placeholder="Optional cap"
                        onChange={handleChange}
                        onBlur={handleBlur}
                      />
                    </Form.Item>
                  </Col>
                </Row>
              )}
  
              <Divider style={{ margin: '12px 0' }} />
  
              {/* Kind-specific checkboxes */}
              {kind === 'earnings' && (
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Checkbox
                    checked={values.includedInCtc}
                    onChange={(e) => setFieldValue('includedInCtc', e.target.checked)}
                  >
                    Include in CTC
                  </Checkbox>
                  <Checkbox
                    checked={values.includedInSalaryStructure}
                    onChange={(e) => setFieldValue('includedInSalaryStructure', e.target.checked)}
                  >
                    Include in Salary Structure
                  </Checkbox>
                  <Checkbox
                    checked={values.taxable}
                    onChange={(e) => setFieldValue('taxable', e.target.checked)}
                  >
                    Taxable Component
                  </Checkbox>
                  <Checkbox
                    checked={values.proRata}
                    onChange={(e) => setFieldValue('proRata', e.target.checked)}
                  >
                    Calculate Pro-Rata on Attendance/LOP
                  </Checkbox>
                  <Checkbox
                    checked={values.fbpComponent}
                    onChange={(e) => setFieldValue('fbpComponent', e.target.checked)}
                  >
                    Flexible Benefit Plan (FBP) Component
                  </Checkbox>
                  <Checkbox
                    checked={values.includedInEsi}
                    onChange={(e) => setFieldValue('includedInEsi', e.target.checked)}
                  >
                    Consider for ESI Contribution
                  </Checkbox>
                  <Checkbox
                    checked={values.showInPayslip}
                    onChange={(e) => setFieldValue('showInPayslip', e.target.checked)}
                  >
                    Show in Employee Payslip
                  </Checkbox>
                </Space>
              )}
  
              {kind === 'deductions' && (
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Checkbox
                    checked={values.recurring}
                    onChange={(e) => setFieldValue('recurring', e.target.checked)}
                  >
                    Recurring Deduction
                  </Checkbox>
                  <Checkbox
                    checked={values.preTax}
                    onChange={(e) => setFieldValue('preTax', e.target.checked)}
                  >
                    Pre-Tax Deduction
                  </Checkbox>
                </Space>
              )}
  
              {kind === 'benefits' && (
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Checkbox
                    checked={values.includedInCtc}
                    onChange={(e) => setFieldValue('includedInCtc', e.target.checked)}
                  >
                    Include in CTC
                  </Checkbox>
                  <Checkbox
                    checked={values.includedInSalaryStructure}
                    onChange={(e) => setFieldValue('includedInSalaryStructure', e.target.checked)}
                  >
                    Include in Salary Structure
                  </Checkbox>
                  <Checkbox
                    checked={values.preTax}
                    onChange={(e) => setFieldValue('preTax', e.target.checked)}
                  >
                    Pre-Tax Benefit
                  </Checkbox>
                  <Checkbox
                    checked={values.proRata}
                    onChange={(e) => setFieldValue('proRata', e.target.checked)}
                  >
                    Pro-Rata Calculation
                  </Checkbox>
                </Space>
              )}
  
              {kind === 'reimbursements' && (
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Checkbox
                    checked={values.includedInCtc}
                    onChange={(e) => setFieldValue('includedInCtc', e.target.checked)}
                  >
                    Include in CTC
                  </Checkbox>
                  <Checkbox
                    checked={values.includedInSalaryStructure}
                    onChange={(e) => setFieldValue('includedInSalaryStructure', e.target.checked)}
                  >
                    Include in Salary Structure
                  </Checkbox>
                  <Checkbox
                    checked={values.fbpComponent}
                    onChange={(e) => setFieldValue('fbpComponent', e.target.checked)}
                  >
                    Flexible Benefit Plan (FBP) Component
                  </Checkbox>
                </Space>
              )}
  
              <Collapse
                style={{ marginTop: 16 }}
                activeKey={statutoryOpen || statutoryHasError ? [STATUTORY_KEY] : []}
                onChange={(keys) => setStatutoryOpen([].concat(keys).includes(STATUTORY_KEY))}
                items={[
                  {
                    key: STATUTORY_KEY,
                    label: 'Statutory & tax',
                    children: (
                      <StatutoryFields
                        kind={kind}
                        values={values}
                        errors={errors}
                        touched={touched}
                        handleChange={handleChange}
                        handleBlur={handleBlur}
                        setFieldValue={setFieldValue}
                      />
                    ),
                  },
                ]}
              />
  
              <div style={{ marginTop: 24, textAlign: 'right' }}>
                <Space>
                  <Button onClick={onClose} disabled={isSubmitting}>
                    Cancel
                  </Button>
                  <Button
                    id="btn-save-component"
                    type="primary"
                    htmlType="submit"
                    loading={isSubmitting}
                  >
                    {isEdit ? 'Save Changes' : 'Create Component'}
                  </Button>
                </Space>
              </div>
            </Form>
          );
        }}
      </Formik>
    </Drawer>
  );
}

ComponentDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
  kind: PropTypes.oneOf(['earnings', 'deductions', 'benefits', 'reimbursements']).isRequired,
  initialData: PropTypes.object,
  initialValues: PropTypes.object,
};
