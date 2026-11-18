import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ChallanModal } from './ChallanModal';
import { form24QService } from './form24QService';

vi.mock('./form24QService', () => ({
  form24QService: {
    createChallan: vi.fn(),
  },
}));

describe('ChallanModal component (W-89.4)', () => {
  const onClose = vi.fn();
  const onSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders modal with required input fields when open', () => {
    render(
      <ChallanModal
        open={true}
        onClose={onClose}
        onSuccess={onSuccess}
        financialYear="2026-27"
        quarter="Q1"
      />
    );

    expect(screen.getByText('Record ITNS 281 TDS Challan')).toBeTruthy();
    expect(screen.getByLabelText(/BSR Code/i)).toBeTruthy();
    expect(screen.getByLabelText(/Challan Serial No/i)).toBeTruthy();
    expect(screen.getByLabelText(/Tender \/ Deposit Date/i)).toBeTruthy();
    expect(screen.getByLabelText(/Minor Head/i)).toBeTruthy();
    expect(screen.getByLabelText(/^Tax$/i)).toBeTruthy();
  });

  it('validates 7-digit requirement for BSR code', async () => {
    render(
      <ChallanModal
        open={true}
        onClose={onClose}
        onSuccess={onSuccess}
        financialYear="2026-27"
        quarter="Q1"
      />
    );

    const bsrInput = screen.getByLabelText(/BSR Code/i);
    fireEvent.change(bsrInput, { target: { value: '123' } });

    const okBtn = screen.getByRole('button', { name: /Record Challan/i });
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(screen.getByText('BSR code must be exactly 7 digits')).toBeTruthy();
    });
    expect(form24QService.createChallan).not.toHaveBeenCalled();
  });

  it('successfully submits valid challan form and triggers onSuccess', async () => {
    form24QService.createChallan.mockResolvedValueOnce({ id: 'ch-success' });

    render(
      <ChallanModal
        open={true}
        onClose={onClose}
        onSuccess={onSuccess}
        financialYear="2026-27"
        quarter="Q1"
      />
    );

    const bsrInput = screen.getByLabelText(/BSR Code/i);
    fireEvent.change(bsrInput, { target: { value: '0510304' } });

    const challanNoInput = screen.getByLabelText(/Challan Serial No/i);
    fireEvent.change(challanNoInput, { target: { value: '00123' } });

    const taxInput = screen.getByLabelText(/^Tax$/i);
    fireEvent.change(taxInput, { target: { value: '15000' } });

    const okBtn = screen.getByRole('button', { name: /Record Challan/i });
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(form24QService.createChallan).toHaveBeenCalledTimes(1);
      expect(onSuccess).toHaveBeenCalledTimes(1);
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });
});
