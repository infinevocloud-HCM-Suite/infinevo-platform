import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { GuidedStatutorySetup } from './GuidedStatutorySetup.jsx';
import { guidedStatutoryService } from './guidedStatutoryService.js';

vi.mock('./guidedStatutoryService.js', () => ({
  guidedStatutoryService: {
    getReadiness: vi.fn(),
    applySetup: vi.fn(),
  },
}));

describe('GuidedStatutorySetup component (W-93)', () => {
  const mockReadiness = {
    epfConfigured: false,
    epf: {
      isEnabled: true,
      registrationNumber: '',
      restrictEmployeeToCeiling: true,
      includeEmployerInCtc: true,
    },
    esiConfigured: false,
    esi: {
      isEnabled: false,
      registrationNumber: '',
      includeEmployerInCtc: true,
    },
    operatingStates: [
      {
        stateCode: 'MH',
        stateName: 'Maharashtra',
        leviesPt: true,
        registrationNumber: null,
        configured: false,
      },
      {
        stateCode: 'DL',
        stateName: 'Delhi',
        leviesPt: false,
        registrationNumber: null,
        configured: true,
      },
    ],
    defaults: {
      epfEmployeeRate: 12.0,
      epfEmployerRate: 12.0,
      epfEpsRate: 8.33,
      epfEdliRate: 0.5,
      epfAdminRate: 0.5,
      epfWageCeiling: 15000.0,
      esiEmployeeRate: 0.75,
      esiEmployerRate: 3.25,
      esiWageCeiling: 21000.0,
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads readiness and renders step 0 overview with detected operating states', async () => {
    guidedStatutoryService.getReadiness.mockResolvedValueOnce(mockReadiness);

    render(
      <MemoryRouter>
        <GuidedStatutorySetup />
      </MemoryRouter>
    );

    expect(screen.getByTestId('guided-setup-loading')).toBeDefined();

    await waitFor(() => {
      expect(screen.getByTestId('step-overview')).toBeDefined();
    });

    expect(screen.getByText('Maharashtra')).toBeDefined();
    expect(screen.getByText('PT Applicable')).toBeDefined();
    expect(screen.getByText('Delhi')).toBeDefined();
    expect(screen.getByText('Exempt / No PT Levied')).toBeDefined();
    expect(screen.getByTestId('btn-wizard-next')).toBeDefined();
  });

  it('navigates through wizard, validates EPF inputs, and completes statutory setup', async () => {
    guidedStatutoryService.getReadiness.mockResolvedValueOnce(mockReadiness);
    guidedStatutoryService.applySetup.mockResolvedValueOnce({
      epf: { isEnabled: true, registrationNumber: 'DLCPM1234567000' },
      esi: { isEnabled: false },
      ptStates: [{ stateCode: 'MH', registrationNumber: '27123456789P' }],
      allStatutoryConfigured: true,
    });

    render(
      <MemoryRouter>
        <GuidedStatutorySetup />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('step-overview')).toBeDefined();
    });

    // Move to Step 1 (EPF)
    fireEvent.click(screen.getByTestId('btn-wizard-next'));
    await waitFor(() => {
      expect(screen.getByTestId('step-epf')).toBeDefined();
    });

    // Attempting next without EPF reg number when registered = true triggers error
    fireEvent.click(screen.getByTestId('btn-wizard-next'));
    await waitFor(() => {
      expect(screen.getByText('Registration number is required')).toBeDefined();
    });

    // Enter EPF reg number
    const epfInput = screen.getByTestId('input-epf-registration-number');
    fireEvent.change(epfInput, { target: { value: 'DLCPM1234567000' } });

    // Move to Step 2 (ESI)
    fireEvent.click(screen.getByTestId('btn-wizard-next'));
    await waitFor(() => {
      expect(screen.getByTestId('step-esi')).toBeDefined();
    });

    // ESI defaults to not registered, click Next to move to Step 3 (PT)
    fireEvent.click(screen.getByTestId('btn-wizard-next'));
    await waitFor(() => {
      expect(screen.getByTestId('step-pt')).toBeDefined();
    });

    // Enter MH PTRC Number
    const mhInput = screen.getByTestId('input-pt-MH');
    fireEvent.change(mhInput, { target: { value: '27123456789P' } });

    // Move to Step 4 (Review)
    fireEvent.click(screen.getByTestId('btn-wizard-next'));
    await waitFor(() => {
      expect(screen.getByTestId('step-review')).toBeDefined();
    });

    expect(screen.getByText('DLCPM1234567000')).toBeDefined();
    expect(screen.getByText('27123456789P')).toBeDefined();

    // Click Complete Statutory Setup
    const submitBtn = screen.getByTestId('submit-guided-setup');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(guidedStatutoryService.applySetup).toHaveBeenCalledTimes(1);
      expect(screen.getByTestId('guided-setup-success')).toBeDefined();
    });

    expect(screen.getByText('Statutory Setup Completed!')).toBeDefined();
    expect(screen.getByTestId('btn-to-checklist')).toBeDefined();
  });

  it('displays error state with retry button when loading readiness fails, preventing saving defaults', async () => {
    guidedStatutoryService.getReadiness.mockRejectedValueOnce(new Error('Network error loading settings'));

    render(
      <MemoryRouter>
        <GuidedStatutorySetup />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('guided-setup-error')).toBeDefined();
    });

    expect(screen.getByText('Failed to Load Statutory Settings')).toBeDefined();
    expect(screen.getByText('Network error loading settings')).toBeDefined();
    expect(screen.queryByTestId('step-overview')).toBeNull();
    expect(guidedStatutoryService.applySetup).not.toHaveBeenCalled();

    // Now clicking Retry re-fetches readiness and displays wizard
    guidedStatutoryService.getReadiness.mockResolvedValueOnce(mockReadiness);
    fireEvent.click(screen.getByTestId('btn-retry-load'));

    await waitFor(() => {
      expect(screen.getByTestId('step-overview')).toBeDefined();
    });
  });

  it('pre-populates existing registration dates and settings when re-running wizard', async () => {
    const existingReadiness = {
      ...mockReadiness,
      epfConfigured: true,
      epf: {
        isEnabled: true,
        registrationNumber: 'DLCPM1234567000',
        registrationDate: '2020-01-15',
        restrictEmployeeToCeiling: true,
        includeEmployerInCtc: true,
        includeEdliInCtc: true,
        includeAdminInCtc: true,
      },
      esiConfigured: true,
      esi: {
        isEnabled: true,
        registrationNumber: '31000123450000101',
        registrationDate: '2019-05-20',
        includeEmployerInCtc: true,
      },
    };
    guidedStatutoryService.getReadiness.mockResolvedValueOnce(existingReadiness);
    guidedStatutoryService.applySetup.mockResolvedValueOnce({
      epf: { isEnabled: true, registrationNumber: 'DLCPM1234567000' },
      esi: { isEnabled: true, registrationNumber: '31000123450000101' },
      ptStates: [],
      allStatutoryConfigured: true,
    });

    render(
      <MemoryRouter>
        <GuidedStatutorySetup />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('step-overview')).toBeDefined();
    });

    // Move through steps to review
    fireEvent.click(screen.getByTestId('btn-wizard-next')); // to EPF
    await waitFor(() => expect(screen.getByTestId('step-epf')).toBeDefined());
    expect(screen.getByTestId('input-epf-registration-number').value).toBe('DLCPM1234567000');

    fireEvent.click(screen.getByTestId('btn-wizard-next')); // to ESI
    await waitFor(() => expect(screen.getByTestId('step-esi')).toBeDefined());
    expect(screen.getByTestId('input-esi-registration-number').value).toBe('31000123450000101');

    fireEvent.click(screen.getByTestId('btn-wizard-next')); // to PT
    await waitFor(() => expect(screen.getByTestId('step-pt')).toBeDefined());

    fireEvent.click(screen.getByTestId('btn-wizard-next')); // to Review
    await waitFor(() => expect(screen.getByTestId('step-review')).toBeDefined());

    // Submit
    fireEvent.click(screen.getByTestId('submit-guided-setup'));

    await waitFor(() => {
      expect(guidedStatutoryService.applySetup).toHaveBeenCalledTimes(1);
    });

    const payload = guidedStatutoryService.applySetup.mock.calls[0][0];
    expect(payload.epf.registrationDate).toBe('2020-01-15');
    expect(payload.epf.includeEdliInCtc).toBe(true);
    expect(payload.epf.includeAdminInCtc).toBe(true);
    expect(payload.esi.registrationDate).toBe('2019-05-20');
    expect(payload.esi.registered).toBe(true);
  });
});

