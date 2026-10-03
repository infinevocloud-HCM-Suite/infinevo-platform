/**
 * Run status and skip reason labels for the dashboard (W-47.5 §5). Values are `PayRunStatus`
 * and `SkipReason` (`payroll/.../payrun/PayRunStatus.java`, `SkipReason.java`); the colours match
 * the run list (`payrun/RunList.jsx:38-47`).
 */

export const RUN_STATUS = {
  DRAFT: { label: 'Draft', color: 'default' },
  LOCKED: { label: 'Locked', color: 'processing' },
  COMPUTING: { label: 'Computing', color: 'cyan' },
  COMPUTED: { label: 'Computed', color: 'blue' },
  FAILED: { label: 'Failed', color: 'error' },
  APPROVED: { label: 'Approved', color: 'orange' },
  PAID: { label: 'Paid', color: 'success' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export const SKIP_REASON = {
  NO_SALARY: 'No salary in force',
  NO_BANK_DETAILS: 'No bank details',
};

export function runStatus(code) {
  return RUN_STATUS[code] ?? { label: code ?? '-', color: 'default' };
}

export function skipReasonLabel(code) {
  return SKIP_REASON[code] ?? code;
}
