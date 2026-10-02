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

export const EARNING_TYPES = [
  { label: 'Fixed', value: 'FIXED' },
  { label: 'Variable', value: 'VARIABLE' },
  { label: 'One Time', value: 'ONE_TIME' },
];

export const EARNING_FREQUENCIES = [
  { label: 'Monthly', value: 'MONTHLY' },
  { label: 'Quarterly', value: 'QUARTERLY' },
  { label: 'Half Yearly', value: 'HALF_YEARLY' },
  { label: 'Yearly', value: 'YEARLY' },
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
    earningType: Yup.string().required('Earning type is required'),
    earningFrequency: Yup.string().required('Frequency is required'),
    proRata: Yup.boolean(),
    includedInCtc: Yup.boolean(),
    includedInSalaryStructure: Yup.boolean(),
    taxable: Yup.boolean(),
    variable: Yup.boolean(),
    oneTime: Yup.boolean(),
    fbpComponent: Yup.boolean(),
    includedInEpf: Yup.boolean(),
    epfInclusionType: Yup.string().nullable(),
    includedInEsi: Yup.boolean(),
    showInPayslip: Yup.boolean(),
  }),

  deductions: Yup.object().shape({
    ...baseSchema,
    deductionType: Yup.string().required('Deduction type is required'),
    recurring: Yup.boolean(),
    preTax: Yup.boolean(),
    emiType: Yup.string().nullable(),
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
    taxExemptSection: Yup.string().nullable(),
    taxExemptionSubType: Yup.string().nullable(),
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
    earningType: 'FIXED',
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
    epfInclusionType: null,
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
