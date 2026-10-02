import React from 'react';
import PropTypes from 'prop-types';
import { useDispatch } from 'react-redux';
import {
  Drawer,
  Form,
  Input,
  Select,
  Checkbox,
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
  CALCULATION_TYPES,
  PERCENTAGE_OF_OPTIONS,
  EARNING_TYPES,
  EARNING_FREQUENCIES,
} from './componentFields.js';
import { componentService } from './componentService.js';
import { invalidateComponents } from './salarySlice.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

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

  const initialValues = React.useMemo(() => {
    if (effectiveInitial) {
      return {
        ...defaultInitialValues[kind],
        ...effectiveInitial,
        defaultValue:
          effectiveInitial.defaultValue != null ? String(effectiveInitial.defaultValue) : '',
        maxLimit: effectiveInitial.maxLimit != null ? String(effectiveInitial.maxLimit) : '',
        perquisiteInterestRate:
          effectiveInitial.perquisiteInterestRate != null
            ? String(effectiveInitial.perquisiteInterestRate)
            : '',
        emiInterestRate:
          effectiveInitial.emiInterestRate != null ? String(effectiveInitial.emiInterestRate) : '',
      };
    }
    return defaultInitialValues[kind];
  }, [effectiveInitial, kind]);

  const handleSubmit = async (values, { setSubmitting }) => {
    try {
      const payload = { ...values };
      if (!payload.maxLimit) payload.maxLimit = null;
      if (payload.calculationType !== 'PERCENTAGE') {
        payload.percentageOf = null;
      }
      if (kind === 'deductions') {
        if (!payload.perquisiteInterestRate) payload.perquisiteInterestRate = null;
        if (!payload.emiInterestRate) payload.emiInterestRate = null;
      }

      if (isEdit) {
        await componentService.update(kind, initialData.id, payload);
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
        {({ values, errors, touched, handleChange, handleBlur, handleSubmit: formikSubmit, isSubmitting, setFieldValue }) => (
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
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item label="Earning Type" required>
                    <Select
                      value={values.earningType}
                      options={EARNING_TYPES}
                      onChange={(val) => setFieldValue('earningType', val)}
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="Frequency" required>
                    <Select
                      value={values.earningFrequency}
                      options={EARNING_FREQUENCIES}
                      onChange={(val) => setFieldValue('earningFrequency', val)}
                    />
                  </Form.Item>
                </Col>
              </Row>
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
                  checked={values.includedInEpf}
                  onChange={(e) => setFieldValue('includedInEpf', e.target.checked)}
                >
                  Consider for EPF Contribution
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
        )}
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
