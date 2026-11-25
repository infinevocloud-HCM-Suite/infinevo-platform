import { describe, it, expect } from 'vitest';
import { itemStatus, proofStatus, sourceKindLabel, receiptProblem, formatBytes, MAX_RECEIPT_BYTES } from './proofLabels';

describe('proofLabels (D-152)', () => {
  it('reads an item status in the employee\'s words, by the proof it is on', () => {
    expect(itemStatus('PENDING', 'DRAFT').label).toBe('Not submitted');
    expect(itemStatus('PENDING', 'SUBMITTED').label).toBe('Under review');
    expect(itemStatus('APPROVED', 'APPROVED').label).toBe('Verified');
    expect(itemStatus('DISALLOWED', 'APPROVED').label).toBe('Rejected');
    expect(itemStatus('RETURNED', 'REJECTED').label).toMatch(/Returned/);
  });

  it('reads every proof status and source kind as words, never a raw code', () => {
    ['NOT_STARTED', 'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'].forEach((code) => {
      expect(proofStatus(code).label).not.toMatch(/_|^[A-Z]+$/);
    });
    expect(sourceKindLabel('HOME_LOAN_INTEREST')).toBe('Home loan interest');
    expect(sourceKindLabel('SOMETHING_NEW')).toBe('Something new');
  });

  it('accepts PDF, JPG and PNG up to 10 MB and names the problem otherwise', () => {
    expect(receiptProblem({ name: 'a.PDF', size: 10 })).toBeNull();
    expect(receiptProblem({ name: 'a.jpeg', size: MAX_RECEIPT_BYTES })).toBeNull();
    expect(receiptProblem({ name: 'a.docx', size: 10 })).toMatch(/not a PDF, JPG or PNG/);
    expect(receiptProblem({ name: 'a.png', size: MAX_RECEIPT_BYTES + 1 })).toMatch(/larger than 10 MB/);
    expect(receiptProblem({ name: 'a.png', size: 0 })).toMatch(/empty/);
  });

  it('formats sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2 KB');
    expect(formatBytes(3 * 1024 * 1024)).toBe('3.0 MB');
  });
});
