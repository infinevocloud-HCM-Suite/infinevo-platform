import * as Yup from 'yup';

const DECIMAL_REGEX = /^\d+(\.\d{1,4})?$/;

const decimalValidator = Yup.string()
  .matches(DECIMAL_REGEX, 'Must be a valid number with at most 4 decimal places')
  .required('Default value is required');

const optionalDecimalValidator = Yup.string()
  .matches(DECIMAL_REGEX, {
    message: 'Must be a valid number with at most 4 decimal places',
    excludeEmptyString: true,
  })
  .nullable();

export const CALCULATION_TYPES = [
  { label: 'Flat Amount', value: 'FLAT' },
  { label: 'Percentage', value: 'PERCENTAGE' },
];

export const PERCENTAGE_OF_OPTIONS = [
  { label: 'CTC', value: 'CTC' },
  { label: 'Basic', value: 'BASIC' },
  { label: 'Gross', value: 'GROSS' },
];

/**
 * Named earning types (D-37), copied verbatim from the legacy picker
 * (legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/salaryComponents/addComponents/addNewCustomEarning.js:74-87)
 * because the tax and payslip rules key on the name. `earning_type` is a free VARCHAR(32) on the API;
 * the longest name is exactly 32 characters.
 */
export const EARNING_TYPE_NAMES = [
  'Basic', 'House Rent Allowance', 'Dearness Allowance',
  'Conveyance Allowance', 'Bonus', 'Commission',
  'Children Education Allowance', 'Hostel Expenditure Allowance',
  'Transport Allowance', 'Helper Allowance', 'Travelling Allowance',
  'Uniform Allowance', 'Daily Allowance', 'City Compensatory Allowance',
  'Overtime Allowance', 'Telephone Allowance', 'Fixed Medical Allowance',
  'Project Allowance', 'Food Allowance', 'Holiday Allowance',
  'Entertainment Allowance', 'Custom Allowance', 'Food Coupon',
  'Gift Coupon', 'Research Allowance', 'Books and Periodicals Allowance',
  'Shift Allowance', 'Fuel Allowance', 'Driver Allowance',
  'Leave Travel Allowance', 'Vehicle Maintenance Allowance',
  'Telephone And Internet Allowance',
];

export const EARNING_TYPES = EARNING_TYPE_NAMES.map((name) => ({ label: name, value: name }));

/** The pre-D-37 earning types. Rows saved with them are mapped on read by {@link normaliseInitialValues}. */
export const OLD_EARNING_TYPES = ['FIXED', 'VARIABLE', 'ONE_TIME'];

export const EARNING_FREQUENCIES = [
  { label: 'Monthly', value: 'MONTHLY' },
  { label: 'Quarterly', value: 'QUARTERLY' },
  { label: 'Half Yearly', value: 'HALF_YEARLY' },
  { label: 'Yearly', value: 'YEARLY' },
];

/** EPF inclusion of an earning (D-38). `epf_inclusion_type` is a free VARCHAR(32) on the API. */
export const EPF_INCLUSION_OPTIONS = [
  { label: 'Always', value: 'ALWAYS' },
  { label: 'Only when PF wage is below 15,000', value: 'WHEN_PF_WAGE_BELOW_15000' },
  { label: 'Never', value: 'NEVER' },
];

/** What happens to an unclaimed reimbursement (D-38); legacy values at addNewReimbursement.js:56. */
export const CARRY_FORWARD_OPTIONS = [
  { label: 'Carry forward to next period', value: 'CARRY_FORWARD' },
  { label: 'Encash monthly', value: 'MONTHLY_ENCASH' },
];

const baseSchema = {
  code: Yup.string()
    .required('Code is required')
    .max(32, 'Code cannot exceed 32 characters')
    .matches(/^[A-Z0-9_]+$/, 'Code must contain uppercase letters, numbers, and underscores only'),
  name: Yup.string().required('Name is required').max(64, 'Name cannot exceed 64 characters'),
  displayName: Yup.string().max(64, 'Display name cannot exceed 64 characters').nullable(),
  calculationType: Yup.string().oneOf(['FLAT', 'PERCENTAGE']).required('Calculation type is required'),
  defaultValue: decimalValidator,
  percentageOf: Yup.string().when('calculationType', {
    is: 'PERCENTAGE',
    then: (schema) => schema.required('Percentage base is required').oneOf(['CTC', 'BASIC', 'GROSS']),
    otherwise: (schema) => schema.nullable(),
  }),
  maxLimit: optionalDecimalValidator,
};

export const componentSchemas = {
  earnings: Yup.object().shape({
    ...baseSchema,
    earningType: Yup.string()
      .nullable()
      .required('Earning type is required')
      .oneOf(EARNING_TYPE_NAMES, 'Choose an earning type from the list'),
    earningFrequency: Yup.string()
      .nullable()
      .when('variable', {
        is: true,
        then: (schema) => schema.required('Frequency is required'),
      }),
    proRata: Yup.boolean(),
    includedInCtc: Yup.boolean(),
    includedInSalaryStructure: Yup.boolean(),
    taxable: Yup.boolean(),
    variable: Yup.boolean(),
    oneTime: Yup.boolean(),
    fbpComponent: Yup.boolean(),
    includedInEpf: Yup.boolean(),
    epfInclusionType: Yup.string()
      .nullable()
      .oneOf([...EPF_INCLUSION_OPTIONS.map((o) => o.value), null]),
    includedInEsi: Yup.boolean(),
    showInPayslip: Yup.boolean(),
  }),

  deductions: Yup.object().shape({
    ...baseSchema,
    deductionType: Yup.string().required('Deduction type is required'),
    recurring: Yup.boolean(),
    preTax: Yup.boolean(),
    emiType: Yup.string().max(32, 'EMI type cannot exceed 32 characters').nullable(),
    perquisiteInterestRate: optionalDecimalValidator,
    emiInterestRate: optionalDecimalValidator,
  }),

  benefits: Yup.object().shape({
    ...baseSchema,
    benefitPlan: Yup.string().nullable(),
    benefitCategory: Yup.string().nullable(),
    preTax: Yup.boolean(),
    oneTime: Yup.boolean(),
    proRata: Yup.boolean(),
    superannuation: Yup.boolean(),
    includedInCtc: Yup.boolean(),
    includedInSalaryStructure: Yup.boolean(),
    allowsEmployerContribution: Yup.boolean(),
    allowsEmployeeContribution: Yup.boolean(),
    taxExemptSection: Yup.string().max(16, 'Tax exempt section cannot exceed 16 characters').nullable(),
    taxExemptionSubType: Yup.string()
      .max(32, 'Tax exemption sub type cannot exceed 32 characters')
      .nullable(),
  }),

  reimbursements: Yup.object().shape({
    ...baseSchema,
    reimbursementType: Yup.string().required('Reimbursement type is required'),
    carryForwardOption: Yup.string().nullable(),
    includedInCtc: Yup.boolean(),
    includedInSalaryStructure: Yup.boolean(),
    fbpComponent: Yup.boolean(),
    optIn: Yup.boolean(),
  }),
};

export const defaultInitialValues = {
  earnings: {
    code: '',
    name: '',
    displayName: '',
    earningType: null,
    calculationType: 'FLAT',
    defaultValue: '',
    percentageOf: null,
    maxLimit: '',
    earningFrequency: 'MONTHLY',
    proRata: true,
    includedInCtc: true,
    includedInSalaryStructure: true,
    taxable: true,
    variable: false,
    oneTime: false,
    fbpComponent: false,
    includedInEpf: false,
    epfInclusionType: 'NEVER',
    includedInEsi: false,
    showInPayslip: true,
  },
  deductions: {
    code: '',
    name: '',
    displayName: '',
    deductionType: 'POST_TAX',
    calculationType: 'FLAT',
    defaultValue: '',
    percentageOf: null,
    maxLimit: '',
    recurring: true,
    preTax: false,
    emiType: null,
    perquisiteInterestRate: '',
    emiInterestRate: '',
  },
  benefits: {
    code: '',
    name: '',
    displayName: '',
    benefitPlan: '',
    benefitCategory: '',
    calculationType: 'FLAT',
    defaultValue: '',
    percentageOf: null,
    maxLimit: '',
    preTax: false,
    oneTime: false,
    proRata: true,
    superannuation: false,
    includedInCtc: true,
    includedInSalaryStructure: true,
    allowsEmployerContribution: false,
    allowsEmployeeContribution: false,
    taxExemptSection: '',
    taxExemptionSubType: '',
  },
  reimbursements: {
    code: '',
    name: '',
    displayName: '',
    reimbursementType: 'GENERAL',
    calculationType: 'FLAT',
    defaultValue: '',
    percentageOf: null,
    maxLimit: '',
    carryForwardOption: null,
    includedInCtc: true,
    includedInSalaryStructure: true,
    fbpComponent: false,
    optIn: false,
  },
};

/**
 * Fields each kind sends, matching the backend request records
 * (code/backend/payroll/src/main/java/com/infinevo/payroll/component/{Earning,Deduction,Benefit,Reimbursement}Request.java).
 * Anything else on the form values (id, active, audit columns) is left out of the payload.
 */
export const REQUEST_FIELDS = {
  earnings: [
    'code', 'name', 'displayName', 'earningType', 'calculationType', 'defaultValue', 'percentageOf',
    'maxLimit', 'earningFrequency', 'parentEarningId', 'proRata', 'includedInCtc',
    'includedInSalaryStructure', 'taxable', 'variable', 'oneTime', 'fbpComponent', 'includedInEpf',
    'epfInclusionType', 'includedInEsi', 'showInPayslip',
  ],
  deductions: [
    'code', 'name', 'displayName', 'deductionType', 'calculationType', 'defaultValue', 'percentageOf',
    'maxLimit', 'recurring', 'preTax', 'emiType', 'perquisiteInterestRate', 'emiInterestRate',
  ],
  benefits: [
    'code', 'name', 'displayName', 'benefitPlan', 'benefitCategory', 'calculationType', 'defaultValue',
    'percentageOf', 'maxLimit', 'preTax', 'oneTime', 'proRata', 'superannuation', 'includedInCtc',
    'includedInSalaryStructure', 'allowsEmployerContribution', 'allowsEmployeeContribution',
    'taxExemptSection', 'taxExemptionSubType',
  ],
  reimbursements: [
    'code', 'name', 'displayName', 'reimbursementType', 'calculationType', 'defaultValue', 'percentageOf',
    'maxLimit', 'carryForwardOption', 'includedInCtc', 'includedInSalaryStructure', 'fbpComponent', 'optIn',
  ],
};

/** Fields rendered inside the collapsed "Statutory & tax" group, per kind (D-38). */
export const STATUTORY_FIELDS = {
  earnings: ['epfInclusionType'],
  deductions: ['emiType', 'perquisiteInterestRate', 'emiInterestRate'],
  benefits: [
    'superannuation', 'oneTime', 'allowsEmployerContribution', 'allowsEmployeeContribution',
    'taxExemptSection', 'taxExemptionSubType',
  ],
  reimbursements: ['carryForwardOption', 'optIn'],
};

const EPF_ALIASES = {
  ALWAYS: 'ALWAYS',
  CONDITIONAL: 'WHEN_PF_WAGE_BELOW_15000',
  WHEN_PF_WAGE_BELOW_15000: 'WHEN_PF_WAGE_BELOW_15000',
  NEVER: 'NEVER',
};

function epfInclusionOnRead(row) {
  if (row.includedInEpf === false) return 'NEVER';
  const raw = row.epfInclusionType ? String(row.epfInclusionType).trim().toUpperCase() : '';
  if (EPF_ALIASES[raw]) return EPF_ALIASES[raw];
  return row.includedInEpf ? 'ALWAYS' : 'NEVER';
}

function carryForwardOnRead(value) {
  if (!value) return null;
  const upper = String(value).trim().toUpperCase();
  return CARRY_FORWARD_OPTIONS.some((o) => o.value === upper) ? upper : value;
}

/**
 * Maps a stored component onto the drawer's form values.
 * Earnings saved before D-37 carry FIXED / VARIABLE / ONE_TIME in `earningType`:
 * FIXED -> Variable unticked, VARIABLE -> ticked, ONE_TIME -> ticked and one-time. There is no
 * one-time frequency, so `oneTime` carries it. The old type is cleared so a named type is picked on save.
 */
export function normaliseInitialValues(kind, row) {
  const values = { ...defaultInitialValues[kind], ...row };
  if (kind === 'earnings') {
    const type = row.earningType ? String(row.earningType).trim().toUpperCase() : null;
    if (type && OLD_EARNING_TYPES.includes(type)) {
      values.earningType = null;
      values.variable = Boolean(row.variable) || type !== 'FIXED';
      values.oneTime = Boolean(row.oneTime) || type === 'ONE_TIME';
    } else {
      values.variable = Boolean(row.variable);
      values.oneTime = Boolean(row.oneTime);
    }
    if (!values.earningFrequency) values.earningFrequency = 'MONTHLY';
    values.epfInclusionType = epfInclusionOnRead(row);
  }
  if (kind === 'reimbursements') {
    values.carryForwardOption = carryForwardOnRead(row.carryForwardOption);
  }
  return values;
}

/** Builds the request body for a kind from the form values; sends only what the API accepts. */
export function buildPayload(kind, values) {
  const payload = {};
  REQUEST_FIELDS[kind].forEach((field) => {
    if (values[field] !== undefined) payload[field] = values[field];
  });
  if (!payload.maxLimit) payload.maxLimit = null;
  if (payload.calculationType !== 'PERCENTAGE') payload.percentageOf = null;
  if (kind === 'earnings') {
    if (!payload.variable) {
      payload.oneTime = false;
      payload.earningFrequency = 'MONTHLY';
    }
    payload.epfInclusionType = payload.epfInclusionType || 'NEVER';
    payload.includedInEpf = payload.epfInclusionType !== 'NEVER';
  }
  if (kind === 'deductions') {
    if (!payload.emiType) payload.emiType = null;
    if (!payload.perquisiteInterestRate) payload.perquisiteInterestRate = null;
    if (!payload.emiInterestRate) payload.emiInterestRate = null;
  }
  if (kind === 'benefits') {
    if (!payload.taxExemptSection) payload.taxExemptSection = null;
    if (!payload.taxExemptionSubType) payload.taxExemptionSubType = null;
  }
  return payload;
}
