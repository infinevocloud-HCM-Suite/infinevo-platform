import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { ProofReviewQueue } from './ProofReviewQueue';
import { proofService } from './proofService';

vi.mock('./proofService', () => ({
  proofService: {
    listQueue: vi.fn(),
    getReview: vi.fn(),
    decideItem: vi.fn(),
    decideFinal: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('ProofReviewQueue component', () => {
  const sampleQueueData = {
    content: [
      {
        employee_id: 'emp-1-uuid',
        number: 'EMP-01',
        name: 'Devashish Patel',
        tax_regime: 'OLD',
        proof_status: 'SUBMITTED',
        proof_id: 'proof-1-uuid',
        submitted_at: '2026-10-06T10:00:00Z',
        claimed_total: 75000,
        approved_total: null,
      },
      {
        employee_id: 'emp-2-uuid',
        number: 'EMP-02',
        name: 'Rahul Sharma',
        tax_regime: 'NEW',
        proof_status: 'DRAFT',
        proof_id: 'proof-2-uuid',
        submitted_at: null,
        claimed_total: 0,
        approved_total: null,
      },
    ],
    totalElements: 2,
    number: 0,
    size: 25,
  };

  const sampleReviewData = {
    id: 'proof-1-uuid',
    employee_id: 'emp-1-uuid',
    financial_year: '2026-2027',
    status: 'SUBMITTED',
    items: [
      {
        id: 'item-1-uuid',
        source_kind: 'SECTION_80C',
        description: 'LIC Life Insurance Premium',
        declared_amount: 80000,
        claimed_amount: 75000,
        approved_amount: null,
        status: 'PENDING',
        documents: [
          {
            document_id: 'doc-1-uuid',
            file_name: 'lic_receipt.pdf',
            size_bytes: 102400,
          },
        ],
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders proof review queue table and fetches data on mount', async () => {
    proofService.listQueue.mockResolvedValueOnce(sampleQueueData);

    render(<ProofReviewQueue />);

    expect(screen.getByText('Proof of Investment Verification Queue')).toBeTruthy();

    await waitFor(() => {
      expect(proofService.listQueue).toHaveBeenCalled();
      expect(screen.getByText('Devashish Patel')).toBeTruthy();
      expect(screen.getByText('Rahul Sharma')).toBeTruthy();
      expect(screen.getByText('EMP-01', { exact: false })).toBeTruthy();
    });
  });

  it('opens review drawer and loads review details on clicking review button', async () => {
    proofService.listQueue.mockResolvedValueOnce(sampleQueueData);
    proofService.getReview.mockResolvedValueOnce(sampleReviewData);

    render(<ProofReviewQueue />);

    await waitFor(() => {
      expect(screen.getByText('Devashish Patel')).toBeTruthy();
    });

    const reviewBtn = screen.getByTestId('review-btn-emp-1-uuid');
    fireEvent.click(reviewBtn);

    await waitFor(() => {
      expect(proofService.getReview).toHaveBeenCalledWith('proof-1-uuid');
      expect(screen.getByText('LIC Life Insurance Premium')).toBeTruthy();
      expect(screen.getByText('lic_receipt.pdf')).toBeTruthy();
    });
  });

  it('allows approving an item with verified amount', async () => {
    proofService.listQueue.mockResolvedValueOnce(sampleQueueData);
    proofService.getReview.mockResolvedValue(sampleReviewData);
    proofService.decideItem.mockResolvedValueOnce({
      id: 'item-1-uuid',
      status: 'VERIFIED',
      approved_amount: 75000,
    });

    render(<ProofReviewQueue />);

    await waitFor(() => {
      expect(screen.getByText('Devashish Patel')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('review-btn-emp-1-uuid'));

    await waitFor(() => {
      expect(screen.getByTestId('btn-approve-item-item-1-uuid')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('btn-approve-item-item-1-uuid'));

    const recordBtn = screen.getByRole('button', { name: /record decision/i });
    fireEvent.click(recordBtn);

    await waitFor(() => {
      expect(proofService.decideItem).toHaveBeenCalledWith(
        'proof-1-uuid',
        'item-1-uuid',
        expect.objectContaining({
          action: 'APPROVE',
        }),
      );
    });
  });
});
