import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MyProofPanel } from './MyProofPanel';
import { proofService } from './proofService';
import { openReceipt } from './openReceipt';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';

vi.mock('./proofService', () => ({
  proofService: {
    own: vi.fn(),
    updateItem: vi.fn(),
    attach: vi.fn(),
    detach: vi.fn(),
    ownReceipt: vi.fn(),
    submit: vi.fn(),
  },
}));
vi.mock('./openReceipt', () => ({ openReceipt: vi.fn() }));
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));

const RENT = {
  id: 'i-rent',
  source_kind: 'HOUSE_RENT',
  description: 'Rent, Flat 4B',
  declared_amount: 120000,
  claimed_amount: 120000,
  approved_amount: null,
  status: 'PENDING',
  employee_note: null,
  reviewer_note: null,
  documents: [
    { document_id: 'd1', file_name: 'rent.pdf', content_type: 'application/pdf', size_bytes: 2048, uploaded_at: '2027-01-10T10:00:00Z' },
  ],
};
const LOAN = {
  id: 'i-loan',
  source_kind: 'HOME_LOAN_INTEREST',
  description: 'Home loan interest',
  declared_amount: 50000,
  claimed_amount: null,
  approved_amount: null,
  status: 'RETURNED',
  employee_note: null,
  reviewer_note: 'The certificate is for last year',
  documents: [],
};

function proof(overrides = {}) {
  return {
    id: 'p1',
    status: 'DRAFT',
    proof_open: true,
    editable: true,
    due_date: '2027-01-31',
    attachment_mandatory: true,
    submitted_at: null,
    decided_at: null,
    reviewer_note: null,
    items: [RENT],
    ...overrides,
  };
}

describe('MyProofPanel (D-152, W-47.3c)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('says the declaration must be submitted first when the server answers NOT_SUBMITTED', async () => {
    proofService.own.mockRejectedValueOnce({ status: 409, code: 'NOT_SUBMITTED', message: 'not submitted' });
    render(<MyProofPanel fy="2026-27" />);
    expect(await screen.findByTestId('proof-not-submitted')).toBeTruthy();
    expect(screen.getByText('Submit your tax declaration first')).toBeTruthy();
    expect(errorMsg).not.toHaveBeenCalled();
  });

  it('shows one card per declared item with its status in words, receipts and the due date', async () => {
    proofService.own.mockResolvedValueOnce(proof());
    render(<MyProofPanel fy="2026-27" />);
    const card = await screen.findByTestId('proof-item-i-rent');
    expect(within(card).getByText('Rent, Flat 4B')).toBeTruthy();
    expect(within(card).getByTestId('proof-item-status').textContent).toBe('Not submitted');
    expect(within(card).getByText('rent.pdf')).toBeTruthy();
    expect(within(card).getByTestId('proof-dropzone')).toBeTruthy();
    expect(screen.getByText(/Due by/)).toBeTruthy();
    expect(screen.getByTestId('proof-submit')).toBeTruthy();
    expect(proofService.own).toHaveBeenCalledWith('2026-27');
  });

  it('uploads a dropped receipt and reloads; refuses a wrong type or an oversize file before sending', async () => {
    proofService.own.mockResolvedValue(proof());
    proofService.attach.mockResolvedValueOnce({ document_id: 'd2' });
    const { container } = render(<MyProofPanel fy="2026-27" />);
    await screen.findByTestId('proof-item-i-rent');
    // rc-upload replaces its input after every pick, so each drop looks it up again.
    const input = () => container.querySelector('input[type="file"]');

    const good = new File(['%PDF-'], 'lic.pdf', { type: 'application/pdf' });
    fireEvent.change(input(), { target: { files: [good] } });
    await waitFor(() => expect(proofService.attach).toHaveBeenCalledWith('2026-27', 'i-rent', good));
    await waitFor(() => expect(proofService.own).toHaveBeenCalledTimes(2));

    // A wrong type never reaches the server: the dropzone's accept list filters it, and receiptProblem
    // (proofLabels.test.js) refuses it again for a browser that lets it through.
    const bad = new File(['x'], 'notes.docx', { type: 'application/msword' });
    fireEvent.change(input(), { target: { files: [bad] } });

    const big = new File(['x'], 'big.png', { type: 'image/png' });
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 });
    fireEvent.change(input(), { target: { files: [big] } });
    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith('Receipt not accepted', expect.stringMatching(/10 MB/)));
    expect(proofService.attach).toHaveBeenCalledTimes(1);
  });

  it('shows a returned item with the reviewer\'s reason and lets the employee drop a replacement', async () => {
    proofService.own.mockResolvedValueOnce(
      proof({ status: 'REJECTED', reviewer_note: 'Please fix the loan item', items: [LOAN] }),
    );
    render(<MyProofPanel fy="2026-27" />);
    const card = await screen.findByTestId('proof-item-i-loan');
    expect(within(card).getByTestId('proof-item-status').textContent).toMatch(/Returned/);
    expect(within(card).getByText('The certificate is for last year')).toBeTruthy();
    expect(within(card).getByText(/replacement receipt/)).toBeTruthy();
    expect(screen.getByTestId('proof-returned-note').textContent).toMatch(/Please fix the loan item/);
  });

  it('saves a changed claim as a decimal string', async () => {
    proofService.own.mockResolvedValue(proof({ items: [LOAN] }));
    proofService.updateItem.mockResolvedValueOnce({});
    render(<MyProofPanel fy="2026-27" />);
    const card = await screen.findByTestId('proof-item-i-loan');
    fireEvent.change(within(card).getByLabelText('Claimed amount'), { target: { value: '45000.50' } });
    fireEvent.click(within(card).getByTestId('proof-save-claim'));
    await waitFor(() =>
      expect(proofService.updateItem).toHaveBeenCalledWith('2026-27', 'i-loan', {
        claimed_amount: '45000.5',
        employee_note: null,
      }),
    );
  });

  it('opens a receipt through the authenticated fetch', async () => {
    proofService.own.mockResolvedValueOnce(proof());
    render(<MyProofPanel fy="2026-27" />);
    const card = await screen.findByTestId('proof-item-i-rent');
    fireEvent.click(within(card).getByRole('button', { name: /View/ }));
    await waitFor(() => expect(openReceipt).toHaveBeenCalled());
    const [fetchBlob, doc] = openReceipt.mock.calls[0];
    expect(doc.file_name).toBe('rent.pdf');
    fetchBlob();
    expect(proofService.ownReceipt).toHaveBeenCalledWith('2026-27', 'i-rent', 'd1');
  });

  it('is read only once submitted: no dropzone, no remove, no submit, status under review', async () => {
    proofService.own.mockResolvedValueOnce(
      proof({ status: 'SUBMITTED', editable: false, submitted_at: '2027-01-12T10:00:00Z' }),
    );
    render(<MyProofPanel fy="2026-27" />);
    const card = await screen.findByTestId('proof-item-i-rent');
    expect(within(card).getByTestId('proof-item-status').textContent).toBe('Under review');
    expect(within(card).queryByTestId('proof-dropzone')).toBeNull();
    expect(within(card).queryByTestId('proof-remove-receipt')).toBeNull();
    expect(screen.queryByTestId('proof-submit')).toBeNull();
    expect(screen.getByTestId('proof-status').textContent).toBe('Submitted');
  });

  it('submits after confirmation, and reports a refused submit', async () => {
    proofService.own.mockResolvedValue(proof());
    proofService.submit.mockResolvedValueOnce({ status: 'SUBMITTED' });
    render(<MyProofPanel fy="2026-27" />);
    fireEvent.click(await screen.findByTestId('proof-submit'));
    fireEvent.click(await screen.findByText('Yes, submit'));
    await waitFor(() => expect(proofService.submit).toHaveBeenCalledWith('2026-27'));
    await waitFor(() => expect(successMsg).toHaveBeenCalled());

    const refusal = { status: 409, code: 'ATTACHMENT_REQUIRED', message: 'Attach a receipt' };
    proofService.submit.mockRejectedValueOnce(refusal);
    fireEvent.click(screen.getByTestId('proof-submit'));
    const confirms = await screen.findAllByText('Yes, submit');
    fireEvent.click(confirms[confirms.length - 1]);
    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith(refusal));
  });
});
