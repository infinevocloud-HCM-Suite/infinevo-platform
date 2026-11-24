import { EMPTY, humanize } from '@shared/ui/format.js';

/** What each declared line is, in words (W-34.1 §3 `source_kind`). */
const SOURCE_KINDS = {
  HOUSE_RENT: 'House rent (HRA)',
  HOME_LOAN_PRINCIPAL: 'Home loan principal',
  HOME_LOAN_INTEREST: 'Home loan interest',
  LET_OUT_PROPERTY: 'Let-out property',
  SECTION_6A: 'Chapter VI-A deduction',
  PREV_EMPLOYMENT: 'Previous employment',
};

export function sourceKindLabel(code) {
  return SOURCE_KINDS[code] || humanize(code);
}

/**
 * One item's status as the employee reads it (W-47.3c §3). `PENDING` means nothing is decided yet:
 * on a draft that reads "Not submitted", once the proof is submitted "Under review".
 *
 * @returns {{ label: string, color: string }}
 */
export function itemStatus(itemStatusCode, proofStatusCode) {
  switch (itemStatusCode) {
    case 'APPROVED':
      return { label: 'Verified', color: 'success' };
    case 'DISALLOWED':
      return { label: 'Rejected', color: 'error' };
    case 'RETURNED':
      return { label: 'Returned — replace the receipt', color: 'warning' };
    case 'PENDING':
      return proofStatusCode === 'SUBMITTED'
        ? { label: 'Under review', color: 'processing' }
        : { label: 'Not submitted', color: 'default' };
    default:
      return { label: itemStatusCode ? humanize(itemStatusCode) : EMPTY, color: 'default' };
  }
}

/** The whole proof's status (W-34.1 `ProofStatus`, plus W-34.3's reported `NOT_STARTED`). */
const PROOF_STATUSES = {
  NOT_STARTED: { label: 'Not started', color: 'default' },
  DRAFT: { label: 'Draft', color: 'default' },
  SUBMITTED: { label: 'Submitted', color: 'processing' },
  APPROVED: { label: 'Verified', color: 'success' },
  REJECTED: { label: 'Returned', color: 'warning' },
};

export function proofStatus(code) {
  return PROOF_STATUSES[code] || { label: code ? humanize(code) : EMPTY, color: 'default' };
}

export const PROOF_STATUS_OPTIONS = Object.entries(PROOF_STATUSES).map(([value, { label }]) => ({
  value,
  label,
}));

/** Receipts: PDF, JPG or PNG up to the W-21 store's 10 MB cap. The server checks the same again. */
export const MAX_RECEIPT_BYTES = 10 * 1024 * 1024;
export const ACCEPTED_RECEIPT_TYPES = '.pdf,.png,.jpg,.jpeg';
const ACCEPTED_EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg'];

/** Why a file cannot be a receipt, or null when it can. */
export function receiptProblem(file) {
  if (!file) return 'Choose a file';
  const name = String(file.name || '');
  const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : '';
  if (!ACCEPTED_EXTENSIONS.includes(ext)) return `${name || 'This file'} is not a PDF, JPG or PNG`;
  if (file.size > MAX_RECEIPT_BYTES) return `${name} is larger than 10 MB`;
  if (file.size === 0) return `${name} is empty`;
  return null;
}

export function formatBytes(bytes) {
  if (typeof bytes !== 'number' || !Number.isFinite(bytes)) return EMPTY;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
