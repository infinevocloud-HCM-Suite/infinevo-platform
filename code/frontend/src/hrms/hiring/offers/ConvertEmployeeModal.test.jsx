import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ConvertEmployeeModal } from './ConvertEmployeeModal.jsx';
import { hiringService } from '../hiringService.js';

vi.mock('../hiringService.js', () => ({
  hiringService: {
    offers: {
      convertToEmployee: vi.fn(),
    },
  },
}));

describe('ConvertEmployeeModal component (W-112)', () => {
  const mockOffer = {
    id: 'offer-1',
    candidateName: 'Kavita Singh',
    candidateEmail: 'kavita@example.com',
    designationTitle: 'Senior Frontend Engineer',
    departmentName: 'Engineering',
    workLocationName: 'Bangalore HQ',
    targetJoinDate: '2026-12-01',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders offer summary and employee conversion form', () => {
    render(
      <ConvertEmployeeModal
        open={true}
        offer={mockOffer}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByText('Convert Candidate to Active Employee')).toBeTruthy();
    expect(screen.getByText('Candidate: Kavita Singh')).toBeTruthy();
    expect(screen.getByText('Employee Number / ID')).toBeTruthy();
  });

  it('submits employee conversion request', async () => {
    hiringService.offers.convertToEmployee.mockResolvedValue({ id: 'emp-1', employeeNumber: 'EMP-2026-001' });
    const handleSuccess = vi.fn();
    const handleClose = vi.fn();

    render(
      <ConvertEmployeeModal
        open={true}
        offer={mockOffer}
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    );

    const empNumberInput = screen.getByPlaceholderText('e.g. EMP-2026-088');
    fireEvent.change(empNumberInput, { target: { value: 'EMP-2026-001' } });

    const submitBtn = screen.getByRole('button', { name: 'Confirm & Create Employee' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(hiringService.offers.convertToEmployee).toHaveBeenCalledWith(
        'offer-1',
        expect.objectContaining({
          employeeNumber: 'EMP-2026-001',
        })
      );
    });
  });
});

