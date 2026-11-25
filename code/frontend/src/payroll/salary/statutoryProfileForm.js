/**
 * The employee's statutory profile as a form: the empty profile, the read from the server and the
 * `StatutoryProfileRequest` sent back. Shared by the salary setup drawer and the statutory profile
 * drawer (D-143), so the two cannot disagree on a field or a default.
 */

/** The ticks, in the order both drawers show them, with the label and the help line. */
export const STATUTORY_TICKS = [
  { name: 'eligibleForPf', label: "Employees' Provident Fund (EPF)" },
  { name: 'eligibleForEps', label: 'Employee Pension Scheme (EPS)' },
  { name: 'contributesEpsOnHigherWages', label: 'EPS on higher wages' },
  { name: 'eligibleForPt', label: 'Professional Tax (PT)' },
  { name: 'eligibleForEsi', label: 'Employee State Insurance (ESI)' },
  { name: 'eligibleForLwf', label: 'Labour Welfare Fund (LWF)' },
  { name: 'director', label: 'Director' },
  {
    name: 'tdsApplicable',
    label: 'TDS applicable',
    extra: 'Off: no TDS is deducted for this employee in pay runs',
  },
];

export const EMPTY_PROFILE = {
  eligibleForPf: false,
  eligibleForEps: false,
  contributesEpsOnHigherWages: false,
  eligibleForPt: false,
  eligibleForEsi: false,
  eligibleForLwf: false,
  director: false,
  tdsApplicable: true,
  pfAccountNumber: '',
  uan: '',
  esiNumber: '',
};

/** The form values for a profile read from the server; missing data gives the empty profile. */
export function profileFormValues(data) {
  if (!data) return { ...EMPTY_PROFILE };
  return {
    eligibleForPf: Boolean(data.eligibleForPf),
    eligibleForEps: Boolean(data.eligibleForEps),
    contributesEpsOnHigherWages: Boolean(data.contributesEpsOnHigherWages),
    eligibleForPt: Boolean(data.eligibleForPt),
    eligibleForEsi: Boolean(data.eligibleForEsi),
    eligibleForLwf: Boolean(data.eligibleForLwf),
    director: Boolean(data.director),
    // D-141: a profile saved before the flag existed reads as TDS applicable.
    tdsApplicable: data.tdsApplicable !== false,
    pfAccountNumber: data.pfAccountNumber || '',
    uan: data.uan || '',
    esiNumber: data.esiNumber || '',
  };
}

/** `StatutoryProfileRequest` from the form's ticks and numbers (W-76.2). */
export function profilePayload(statutory = {}) {
  const trimmed = (value) => (value && String(value).trim() ? String(value).trim() : null);
  return {
    eligibleForPf: Boolean(statutory.eligibleForPf),
    eligibleForPt: Boolean(statutory.eligibleForPt),
    eligibleForLwf: Boolean(statutory.eligibleForLwf),
    eligibleForEsi: Boolean(statutory.eligibleForEsi),
    eligibleForEps: Boolean(statutory.eligibleForEps),
    contributesEpsOnHigherWages: Boolean(statutory.contributesEpsOnHigherWages),
    director: Boolean(statutory.director),
    pfAccountNumber: trimmed(statutory.pfAccountNumber),
    uan: trimmed(statutory.uan),
    esiNumber: trimmed(statutory.esiNumber),
    // D-141: TDS is on unless switched off for this employee.
    tdsApplicable: statutory.tdsApplicable !== false,
  };
}

/** D-145: under the TDS tick while the company switch is off; the tick is greyed out. */
export const TDS_COMPANY_OFF_NOTE = 'Company TDS is off. This setting takes effect when it is turned on';

/** D-145: an employee with no saved profile is TDS applicable (D-141); the card says so. */
export const TDS_NEW_EMPLOYEE_HELP = 'New employees start as TDS applicable';
