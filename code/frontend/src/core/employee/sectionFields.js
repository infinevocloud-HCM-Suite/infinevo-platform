/**
 * Field metadata definitions for the 5 detail sections:
 * personal, contact, identification, employment, bank.
 *
 * Field types SectionTab renders: text, email, textarea, select, date, boolean, and
 * (D-40, D-41) number, time (HH:mm) and timezone (searchable IANA zone select).
 */

/** core.employee.gender — the backend's Gender enum (D-40). */
export const GENDER_OPTIONS = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' },
  { value: 'UNDISCLOSED', label: 'Prefer not to say' },
];

/** core.employee_employment.employment_type — the backend's EmploymentType enum (D-40). */
export const EMPLOYMENT_TYPE_OPTIONS = [
  { value: 'PERMANENT', label: 'Permanent' },
  { value: 'CONTRACT', label: 'Contract' },
  { value: 'PART_TIME', label: 'Part-time' },
  { value: 'INTERN', label: 'Intern' },
  { value: 'PROBATION', label: 'Probation' },
  { value: 'CONSULTANT', label: 'Consultant' },
];

/** Inclusive bounds the backend enforces on noticePeriodDays (EmploymentTerms). */
export const NOTICE_PERIOD_MIN = 0;
export const NOTICE_PERIOD_MAX = 365;

export const sectionFields = {
  personal: {
    title: 'Personal Details',
    permission: 'core.employee.update',
    fields: [
      { name: 'dateOfBirth', label: 'Date of Birth', type: 'date' },
      {
        name: 'maritalStatus',
        label: 'Marital Status',
        type: 'select',
        options: [
          { value: 'SINGLE', label: 'Single' },
          { value: 'MARRIED', label: 'Married' },
          { value: 'DIVORCED', label: 'Divorced' },
          { value: 'WIDOWED', label: 'Widowed' },
        ],
      },
      { name: 'nationality', label: 'Nationality', type: 'text' },
      { name: 'ethnicity', label: 'Ethnicity', type: 'text' },
      { name: 'fatherName', label: "Father's Name", type: 'text' },
      { name: 'differentlyAbledType', label: 'Differently Abled Type', type: 'text' },
      { name: 'eligibleForFullTaxExemption', label: 'Eligible for Full Tax Exemption', type: 'boolean' },
    ],
  },

  contact: {
    title: 'Contact Details',
    permission: 'core.employee.update',
    fields: [
      { name: 'personalEmail', label: 'Personal Email', type: 'email' },
      { name: 'alternateMobile', label: 'Alternate Mobile', type: 'text' },
      // Present Address
      { name: 'addressLine1', label: 'Present Address Line 1', type: 'text' },
      { name: 'addressLine2', label: 'Present Address Line 2', type: 'text' },
      { name: 'city', label: 'Present City', type: 'text' },
      { name: 'state', label: 'Present State', type: 'text' },
      { name: 'stateCode', label: 'Present State Code', type: 'text' },
      { name: 'zipCode', label: 'Present Zip Code', type: 'text' },
      // Permanent Address
      { name: 'permanentAddressLine1', label: 'Permanent Address Line 1', type: 'text' },
      { name: 'permanentAddressLine2', label: 'Permanent Address Line 2', type: 'text' },
      { name: 'permanentCity', label: 'Permanent City', type: 'text' },
      { name: 'permanentState', label: 'Permanent State', type: 'text' },
      { name: 'permanentStateCode', label: 'Permanent State Code', type: 'text' },
      { name: 'permanentZipCode', label: 'Permanent Zip Code', type: 'text' },
      // Emergency Contacts
      { name: 'emergencyContactName', label: 'Emergency Contact Name', type: 'text' },
      { name: 'emergencyContactNumber', label: 'Emergency Contact Phone', type: 'text' },
      { name: 'emergencyContactRelationship', label: 'Emergency Contact Relationship', type: 'text' },
      { name: 'secondaryEmergencyContactName', label: 'Secondary Emergency Contact Name', type: 'text' },
      { name: 'secondaryEmergencyContactNumber', label: 'Secondary Emergency Contact Phone', type: 'text' },
      { name: 'secondaryEmergencyContactRelationship', label: 'Secondary Emergency Contact Relationship', type: 'text' },
    ],
  },

  identification: {
    title: 'Identification Details',
    permission: 'core.employee_identification.update',
    fields: [
      { name: 'panNumber', label: 'PAN Number', type: 'text', masked: true },
      { name: 'aadhaarNumber', label: 'Aadhaar Number', type: 'text', masked: true },
      { name: 'immigrationStatus', label: 'Immigration Status', type: 'text' },
      { name: 'personalTaxId', label: 'Personal Tax ID', type: 'text' },
      { name: 'socialInsuranceNumber', label: 'Social Insurance Number', type: 'text' },
      { name: 'idProofType', label: 'ID Proof Type', type: 'text' },
      { name: 'idDocumentName', label: 'ID Document Name', type: 'text' },
      { name: 'idDocumentNumber', label: 'ID Document Number', type: 'text' },
      { name: 'addressProofType', label: 'Address Proof Type', type: 'text' },
      { name: 'addressDocumentName', label: 'Address Document Name', type: 'text' },
      { name: 'addressDocumentNumber', label: 'Address Document Number', type: 'text' },
    ],
  },

  employment: {
    title: 'Employment Details',
    permission: 'core.employee.update',
    fields: [
      {
        name: 'employmentType',
        label: 'Employment Type',
        type: 'select',
        options: EMPLOYMENT_TYPE_OPTIONS,
      },
      { name: 'probationEndDate', label: 'Probation End Date', type: 'date' },
      {
        name: 'noticePeriodDays',
        label: 'Notice Period (days)',
        type: 'number',
        min: NOTICE_PERIOD_MIN,
        max: NOTICE_PERIOD_MAX,
      },
      { name: 'payGrade', label: 'Pay Grade', type: 'text' },
      { name: 'workstationId', label: 'Workstation ID', type: 'text' },
      { name: 'timeZone', label: 'Time Zone', type: 'timezone' },
      { name: 'shiftStartTime', label: 'Shift Start Time', type: 'time' },
      { name: 'shiftEndTime', label: 'Shift End Time', type: 'time' },
      { name: 'note', label: 'Reporting Manager Notes', type: 'textarea' },
    ],
  },

  bank: {
    title: 'Bank Details',
    permission: 'core.employee_bank.update',
    fields: [
      {
        name: 'paymentMode',
        label: 'Payment Mode',
        type: 'select',
        options: [
          { value: 'BANK_TRANSFER', label: 'Bank Transfer' },
          { value: 'CHEQUE', label: 'Cheque' },
          { value: 'CASH', label: 'Cash' },
        ],
      },
      { name: 'accountHolderName', label: 'Account Holder Name', type: 'text' },
      { name: 'bankName', label: 'Bank Name', type: 'text' },
      { name: 'ifscCode', label: 'IFSC Code', type: 'text' },
      { name: 'bankAccountNumber', label: 'Bank Account Number', type: 'text' },
      {
        name: 'bankAccountType',
        label: 'Account Type',
        type: 'select',
        options: [
          { value: 'SAVINGS', label: 'Savings' },
          { value: 'CURRENT', label: 'Current' },
          { value: 'SALARY', label: 'Salary' },
        ],
      },
    ],
  },
};
