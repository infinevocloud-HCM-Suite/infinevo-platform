import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LwfSetting } from './LwfSetting.jsx';
import { lwfService } from './lwfService.js';

vi.mock('./lwfService.js', () => ({
  lwfService: {
    list: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
}));

describe('LwfSetting component', () => {
  const mockStates = [
    {
      stateCode: 'MH',
      stateName: 'Maharashtra',
      leviesLwf: true,
      isEnabled: true,
      deductionCycle: 'HALF_YEARLY_JUNE_DECEMBER',
      deductionMonths: [6, 12],
      calcMode: 'FLAT',
      employeeContribution: 25.0,
      employerContribution: 75.0,
      registrationNumber: 'MH-LWF-123',
      activeLocationsCount: 2,
    },
    {
      stateCode: 'HR',
      stateName: 'Haryana',
      leviesLwf: true,
      isEnabled: true,
      deductionCycle: 'MONTHLY',
      deductionMonths: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      calcMode: 'PERCENTAGE_WITH_CAP',
      employeeRate: 0.002,
      employerRate: 0.004,
      employeeCap: 25.0,
      employerCap: 50.0,
      registrationNumber: 'HR-LWF-789',
      activeLocationsCount: 1,
    },
    {
      stateCode: 'UP',
      stateName: 'Uttar Pradesh',
      leviesLwf: false,
      isEnabled: true,
      deductionCycle: 'NONE',
      deductionMonths: [],
      calcMode: 'FLAT',
      employeeContribution: 0,
      employerContribution: 0,
      registrationNumber: null,
      activeLocationsCount: 1,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders operating states with deduction cycles and contribution rules', async () => {
    lwfService.list.mockResolvedValueOnce(mockStates);

    render(<LwfSetting />);

    await waitFor(() => {
      expect(screen.getByText('Maharashtra')).toBeDefined();
    });

    expect(screen.getByText('Haryana')).toBeDefined();
    expect(screen.getByText('Uttar Pradesh')).toBeDefined();

    // Badges & cycles
    expect(screen.getByText('Half-Yearly (Jun & Dec)')).toBeDefined();
    expect(screen.getByText('Monthly')).toBeDefined();
    expect(screen.getByText('Exempt')).toBeDefined();

    // Inputs
    expect(screen.getByTestId('reg-input-MH')).toBeDefined();
    expect(screen.getByTestId('reg-input-HR')).toBeDefined();
  });

  it('saves registration number when clicking save button', async () => {
    lwfService.list.mockResolvedValueOnce(mockStates);
    lwfService.update.mockResolvedValueOnce({
      ...mockStates[0],
      registrationNumber: 'MH-NEW-999',
    });

    render(<LwfSetting />);

    const input = await screen.findByTestId('reg-input-MH');
    fireEvent.change(input, { target: { value: 'MH-NEW-999' } });

    const saveBtn = screen.getByTestId('save-reg-btn-MH');
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(lwfService.update).toHaveBeenCalledWith('MH', {
        registrationNumber: 'MH-NEW-999',
      });
    });
  });

  it('toggles enablement switch', async () => {
    lwfService.list.mockResolvedValueOnce(mockStates);
    lwfService.update.mockResolvedValueOnce({
      ...mockStates[0],
      isEnabled: false,
    });

    render(<LwfSetting />);

    const switchBtn = await screen.findByTestId('enable-switch-MH');
    fireEvent.click(switchBtn);

    await waitFor(() => {
      expect(lwfService.update).toHaveBeenCalledWith('MH', {
        isEnabled: false,
        registrationNumber: 'MH-LWF-123',
      });
    });
  });
});

