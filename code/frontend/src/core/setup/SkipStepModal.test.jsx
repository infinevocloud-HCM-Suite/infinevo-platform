import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SkipStepModal } from './SkipStepModal.jsx';
import { setupService } from './setupService.js';

vi.mock('./setupService.js', () => ({
  setupService: {
    skip: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('SkipStepModal component', () => {
  const mockStep = {
    code: 'EPF',
    label: 'EPF',
    module: 'PAYROLL',
    displayOrder: 7,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('blank reason is not sent and shows validation error', async () => {
    const handleClose = vi.fn();
    const handleSuccess = vi.fn();

    render(
      <SkipStepModal
        open={true}
        step={mockStep}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    );

    const submitBtn = document.getElementById('btn-confirm-skip-step');
    expect(submitBtn).toBeTruthy();
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(setupService.skip).not.toHaveBeenCalled();
      expect(handleSuccess).not.toHaveBeenCalled();
      expect(
        screen.getByText('Please provide a reason for skipping this step.')
      ).toBeDefined();
    });

    const textarea = document.getElementById('input-skip-reason');
    fireEvent.change(textarea, { target: { value: '   ' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(setupService.skip).not.toHaveBeenCalled();
      expect(screen.getByText('Skip reason must not be blank.')).toBeDefined();
    });
  });

  it('a 400 response shows the error message in an alert', async () => {
    setupService.skip.mockRejectedValueOnce({
      response: {
        data: {
          message: 'Skip reason must not exceed 500 characters',
        },
      },
    });

    const handleClose = vi.fn();
    const handleSuccess = vi.fn();

    render(
      <SkipStepModal
        open={true}
        step={mockStep}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    );

    const textarea = document.getElementById('input-skip-reason');
    fireEvent.change(textarea, { target: { value: 'Exceedingly long reason' } });

    const submitBtn = document.getElementById('btn-confirm-skip-step');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(setupService.skip).toHaveBeenCalledWith('EPF', 'Exceedingly long reason');
      expect(screen.getByText('Skip reason must not exceed 500 characters')).toBeDefined();
      expect(handleSuccess).not.toHaveBeenCalled();
    });
  });

  it('successful skip calls service, triggers onSuccess to refetch, and closes modal', async () => {
    setupService.skip.mockResolvedValueOnce({
      code: 'EPF',
      label: 'EPF',
      skipped: true,
      skipReason: 'Handled by external vendor',
    });

    const handleClose = vi.fn();
    const handleSuccess = vi.fn();

    render(
      <SkipStepModal
        open={true}
        step={mockStep}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    );

    const textarea = document.getElementById('input-skip-reason');
    fireEvent.change(textarea, { target: { value: 'Handled by external vendor' } });

    const submitBtn = document.getElementById('btn-confirm-skip-step');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(setupService.skip).toHaveBeenCalledWith('EPF', 'Handled by external vendor');
      expect(handleSuccess).toHaveBeenCalledTimes(1);
      expect(handleClose).toHaveBeenCalledTimes(1);
    });
  });
});
