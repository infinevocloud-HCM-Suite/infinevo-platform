import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RecordPaymentModal } from './RecordPaymentModal.jsx';
import { billingService } from '../billing/billingService.js';
import * as msgHelper from '@shared/ui/msgHelper.js';

vi.mock('../billing/billingService.js', () => ({
  billingService: {
    recordPayment: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

const mockInvoice = {
  id: 'inv-123',
  invoiceNumber: 'INF/2026-27/0001',
  totalAmount: 5000,
  amountPaid: 2000,
};

describe('RecordPaymentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders modal with balance due precalculated', () => {
    render(
      <RecordPaymentModal
        open={true}
        onClose={vi.fn()}
        invoice={mockInvoice}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByText(/Record Payment for INF\/2026-27\/0001/)).toBeDefined();
    expect(screen.getByText(/Balance Due:/)).toBeDefined();
    // 5000 - 2000 = 3000
    expect(screen.getByText(/₹\s*3,000\.00/)).toBeDefined();
  });

  it('validates that UTR/Transaction Reference is required', async () => {
    render(
      <RecordPaymentModal
        open={true}
        onClose={vi.fn()}
        invoice={mockInvoice}
        onSuccess={vi.fn()}
      />
    );

    const submitBtn = screen.getByRole('button', { name: 'Record Payment' });
    fireEvent.click(submitBtn);

    expect(await screen.findByText('Please enter UTR or transaction reference')).toBeDefined();
    expect(billingService.recordPayment).not.toHaveBeenCalled();
  });

  it('dispatches recordPayment API call and calls onSuccess when form is valid', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();
    billingService.recordPayment.mockResolvedValueOnce({ id: 'pay-1', status: 'SUCCESS' });

    render(
      <RecordPaymentModal
        open={true}
        onClose={onClose}
        invoice={mockInvoice}
        onSuccess={onSuccess}
      />
    );

    const utrInput = screen.getByPlaceholderText(/e\.g\. UTR1234567890/i);
    fireEvent.change(utrInput, { target: { value: 'UTR9876543210' } });

    const submitBtn = screen.getByRole('button', { name: 'Record Payment' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(billingService.recordPayment).toHaveBeenCalledWith('inv-123', {
        paymentDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        amount: 3000,
        paymentMethod: 'NEFT',
        transactionReference: 'UTR9876543210',
        notes: null,
      });
    });

    expect(msgHelper.successMsg).toHaveBeenCalledWith(
      'Payment recorded',
      expect.stringContaining('INF/2026-27/0001')
    );
    expect(onSuccess).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('handles API error on failure', async () => {
    const apiError = new Error('Payment already settled');
    billingService.recordPayment.mockRejectedValueOnce(apiError);

    render(
      <RecordPaymentModal
        open={true}
        onClose={vi.fn()}
        invoice={mockInvoice}
        onSuccess={vi.fn()}
      />
    );

    const utrInput = screen.getByPlaceholderText(/e\.g\. UTR1234567890/i);
    fireEvent.change(utrInput, { target: { value: 'UTR9876543210' } });

    const submitBtn = screen.getByRole('button', { name: 'Record Payment' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(msgHelper.errorMsg).toHaveBeenCalledWith(apiError);
    });
  });
});
