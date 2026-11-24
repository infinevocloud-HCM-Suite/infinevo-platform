import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { ProofReviewDrawer, decisionProblem } from './ProofReviewDrawer';
import { proofService } from './proofService';
import { openReceipt } from './openReceipt';

vi.mock('./proofService', () => ({
  proofService: {
    review: vi.fn(),
    of: vi.fn(),
    decideItem: vi.fn(),
    decideFinal: vi.fn(),
    receiptOf: vi.fn(),
  },
}));
vi.mock('./openReceipt', () => ({ openReceipt: vi.fn() }));
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));

const ROW = { proof_id: 'p1', employee_id: 'e1', name: 'Asha Rao', number: 'GLBX-0001' };

const REVIEW = {
  id: 'p1',
  employee_id: 'e1',
  financial_year: '2026-2027',
  status: 'SUBMITTED',
  final_step_id: 's-final',
  comment_mandatory: false,
  submitted_at: '2027-01-12T10:00:00Z',
  items: [
    {
      id: 'i1',
      source_kind: 'SECTION_6A',
      description: 'LIC premium',
      declared_amount: 20000,
      claimed_amount: 20000,
      approved_amount: null,
      status: 'PENDING',
      documents: [{ document_id: 'd1', file_name: 'lic.pdf', content_type: 'application/pdf', size_bytes: 1000 }],
      open_step_id: 's1',
    },
  ],
};

function renderDrawer(props = {}) {
  return render(
    <ProofReviewDrawer row={ROW} fy="2026-27" canReview onClose={vi.fn()} onDecided={vi.fn()} {...props} />,
  );
}

describe('ProofReviewDrawer (D-152, W-34.2 screens)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('loads the review for a reviewer and shows each item with its receipt', async () => {
    proofService.review.mockResolvedValueOnce(REVIEW);
    renderDrawer();
    const card = await screen.findByTestId('review-item-i1');
    expect(proofService.review).toHaveBeenCalledWith('p1');
    expect(within(card).getByText('LIC premium')).toBeTruthy();
    expect(within(card).getByText('lic.pdf')).toBeTruthy();
    expect(within(card).getByText('Pending')).toBeTruthy();
    expect(screen.getByText('Asha Rao')).toBeTruthy();
  });

  it('opens a receipt through the officer download with the token-carrying client', async () => {
    proofService.review.mockResolvedValueOnce(REVIEW);
    renderDrawer();
    fireEvent.click(await screen.findByRole('button', { name: /Open receipt/ }));
    await waitFor(() => expect(openReceipt).toHaveBeenCalled());
    openReceipt.mock.calls[0][0]();
    expect(proofService.receiptOf).toHaveBeenCalledWith('e1', '2026-27', 'i1', 'd1');
  });

  it('verifies an item at the claimed amount by default', async () => {
    proofService.review.mockResolvedValue(REVIEW);
    proofService.decideItem.mockResolvedValueOnce({});
    renderDrawer();
    fireEvent.click(await screen.findByRole('button', { name: /Verify/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm' }));
    await waitFor(() =>
      expect(proofService.decideItem).toHaveBeenCalledWith('p1', 'i1', {
        action: 'APPROVE',
        approved_amount: '20000',
        comment: null,
      }),
    );
  });

  it('will not return an item without a reason, then sends it with one', async () => {
    proofService.review.mockResolvedValue(REVIEW);
    proofService.decideItem.mockResolvedValueOnce({});
    renderDrawer();
    fireEvent.click(await screen.findByRole('button', { name: /Return$/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm' }));
    expect((await screen.findByTestId('decision-problem')).textContent).toMatch(/reason is required/);
    expect(proofService.decideItem).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Decision comment'), { target: { value: 'Wrong year' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() =>
      expect(proofService.decideItem).toHaveBeenCalledWith('p1', 'i1', {
        action: 'RETURN',
        approved_amount: null,
        comment: 'Wrong year',
      }),
    );
  });

  it('approves the whole proof from the footer', async () => {
    proofService.review.mockResolvedValue(REVIEW);
    proofService.decideFinal.mockResolvedValueOnce({});
    renderDrawer();
    fireEvent.click(await screen.findByRole('button', { name: /Approve proof/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(proofService.decideFinal).toHaveBeenCalledWith('p1', { action: 'APPROVE', comment: null }));
  });

  it('is read only without the review permission: reads the officer endpoint, offers no decision', async () => {
    proofService.of.mockResolvedValueOnce({ ...REVIEW, items: [{ ...REVIEW.items[0], open_step_id: undefined }] });
    renderDrawer({ canReview: false });
    await screen.findByTestId('review-item-i1');
    expect(proofService.of).toHaveBeenCalledWith('e1', '2026-27');
    expect(proofService.review).not.toHaveBeenCalled();
    expect(screen.getByText('Read only')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Verify/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Approve proof/ })).toBeNull();
  });

  it('decisionProblem applies the W-34.2 wrapper rules without floats', () => {
    expect(decisionProblem({ action: 'APPROVE', amount: '20000.01', claimed: 20000 })).toMatch(/more than/);
    expect(decisionProblem({ action: 'APPROVE', amount: '100', claimed: '200' })).toMatch(/Say why/);
    expect(decisionProblem({ action: 'APPROVE', amount: '100', claimed: '200', comment: 'partial' })).toBeNull();
    expect(decisionProblem({ action: 'APPROVE', amount: null, claimed: '200' })).toMatch(/Enter/);
    expect(decisionProblem({ action: 'DISALLOW', comment: ' ' })).toMatch(/reason/);
    expect(decisionProblem({ action: 'FINAL_APPROVE', commentMandatory: true })).toMatch(/comment is required/);
    expect(decisionProblem({ action: 'FINAL_APPROVE' })).toBeNull();
  });
});
