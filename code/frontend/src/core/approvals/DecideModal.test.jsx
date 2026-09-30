import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DecideModal } from './DecideModal.jsx';
import { approvalService } from './approvalService.js';

vi.mock('./approvalService.js', () => ({
  approvalService: {
    decide: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  errorMsg: vi.fn(),
}));

describe('DecideModal component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('blocks rejection when comment is missing', async () => {
    const handleSuccess = vi.fn();
    render(
      <DecideModal
        open={true}
        decision="REJECTED"
        step={{ id: 'step-1', stepIndex: 0, flowType: 'LEAVE', itemRef: 'LEAVE Annual Leave' }}
        onCancel={vi.fn()}
        onSuccess={handleSuccess}
      />
    );

    const okBtn = document.getElementById('btn-confirm-decide');
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(screen.getByText('Comment is required when rejecting a request.')).toBeDefined();
      expect(approvalService.decide).not.toHaveBeenCalled();
    }, { timeout: 4000 });
  });

  it('shows approvedAmount for REIMBURSEMENT and sends string with two decimals', async () => {
    approvalService.decide.mockResolvedValueOnce({});
    const handleSuccess = vi.fn();

    render(
      <DecideModal
        open={true}
        decision="APPROVED"
        step={{ id: 'step-2', stepIndex: 1, flowType: 'REIMBURSEMENT', itemRef: 'REIMBURSEMENT Travel' }}
        onCancel={vi.fn()}
        onSuccess={handleSuccess}
      />
    );

    const amountInput = document.getElementById('input-approved-amount');
    expect(amountInput).toBeDefined();

    fireEvent.change(amountInput, { target: { value: '2500' } });
    const okBtn = document.getElementById('btn-confirm-decide');
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(approvalService.decide).toHaveBeenCalledWith('step-2', {
        decision: 'APPROVED',
        comment: '',
        approvedAmount: '2500.00',
      });
      expect(handleSuccess).toHaveBeenCalled();
    });
  });

  it('does not display approvedAmount for non-financial flow types like LEAVE', () => {
    render(
      <DecideModal
        open={true}
        decision="APPROVED"
        step={{ id: 'step-3', stepIndex: 0, flowType: 'LEAVE', itemRef: 'LEAVE Sick Leave' }}
        onCancel={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    const amountInput = document.getElementById('input-approved-amount');
    expect(amountInput).toBeNull();
  });
});
