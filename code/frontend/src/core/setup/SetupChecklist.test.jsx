import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SetupChecklist } from './SetupChecklist.jsx';
import { setupService } from './setupService.js';
import * as shellScreens from '@shell/screens';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('./setupService.js', () => ({
  setupService: {
    get: vi.fn(),
    skip: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('SetupChecklist component', () => {
  const mockChecklistResponse = {
    steps: [
      {
        code: 'WORK_LOCATION',
        label: 'Work location',
        module: null,
        displayOrder: 1,
        completed: true,
        skipped: false,
        skipReason: null,
        completedAt: '2026-09-30T10:00:00Z',
        newStep: false,
      },
      {
        code: 'EMPLOYEE',
        label: 'Employee',
        module: null,
        displayOrder: 2,
        completed: false,
        skipped: true,
        skipReason: 'Externally managed',
        completedAt: null,
        newStep: false,
      },
      {
        code: 'PAY_SCHEDULE',
        label: 'Pay schedule',
        module: 'PAYROLL',
        displayOrder: 3,
        completed: false,
        skipped: false,
        skipReason: null,
        completedAt: null,
        newStep: false,
      },
      {
        code: 'NEW_CUSTOM_STEP',
        label: 'New catalogue step',
        module: null,
        displayOrder: 4,
        completed: false,
        skipped: false,
        skipReason: null,
        completedAt: null,
        newStep: true,
      },
    ],
    completedCount: 1,
    skippedCount: 1,
    totalCount: 4,
    newCount: 1,
    progressPercentage: 50,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    setupService.get.mockResolvedValue(mockChecklistResponse);

    // Default: full access and all modules
    vi.spyOn(shellScreens, 'useCan').mockReturnValue(true);
    vi.spyOn(shellScreens, 'useHasModule').mockReturnValue(true);
  });

  it('renders NotEntitled when user lacks core.tenant.read', async () => {
    vi.spyOn(shellScreens, 'useCan').mockImplementation((action) => action !== 'core.tenant.read');

    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
    expect(setupService.get).not.toHaveBeenCalled();
  });

  it('an HRMS-only feed renders no payroll group', async () => {
    vi.spyOn(shellScreens, 'useHasModule').mockImplementation((mod) => mod === 'HRMS');

    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Core Setup')).toBeDefined();
    });

    expect(screen.queryByText('Payroll Setup')).toBeNull();
    expect(screen.queryByText('Pay schedule')).toBeNull();
  });

  it('renders each of the four tags: Done, Skipped, New, and To do', async () => {
    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Done')).toBeDefined();
      expect(screen.getByText('Skipped')).toBeDefined();
      expect(screen.getByText('New')).toBeDefined();
      expect(screen.getByText('To do')).toBeDefined();
    });

    expect(screen.getByText('Reason: Externally managed')).toBeDefined();
  });

  it('the server percentage is shown unchanged', async () => {
    setupService.get.mockResolvedValueOnce({
      ...mockChecklistResponse,
      progressPercentage: 73,
    });

    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Setup Progress: 73%')).toBeDefined();
    });
  });

  it('no Skip without core.tenant.manage', async () => {
    vi.spyOn(shellScreens, 'useCan').mockImplementation((action) => {
      if (action === 'core.tenant.manage') return false;
      return true;
    });

    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Core Setup')).toBeDefined();
    });

    expect(screen.queryByText('Skip')).toBeNull();
  });

  it('no Open for a step whose path is not in the feed', async () => {
    // User lacks core.org.read
    vi.spyOn(shellScreens, 'useCan').mockImplementation((action) => {
      if (action === 'core.org.read') return false;
      return true;
    });

    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Core Setup')).toBeDefined();
    });

    // WORK_LOCATION requires core.org.read: should NOT have an Open button
    expect(document.getElementById('btn-open-step-work-location')).toBeNull();

    // PAY_SCHEDULE has no entry in stepLinks: should NOT have an Open button
    expect(document.getElementById('btn-open-step-pay-schedule')).toBeNull();
  });

  it('renders Open button when step link exists and user has required action, and navigates on click', async () => {
    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(document.getElementById('btn-open-step-work-location')).toBeTruthy();
    });

    fireEvent.click(document.getElementById('btn-open-step-work-location'));
    expect(mockNavigate).toHaveBeenCalledWith('/org/work-locations');
  });

  it('clicking Skip opens SkipStepModal', async () => {
    render(
      <MemoryRouter>
        <SetupChecklist />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(document.getElementById('btn-skip-step-pay-schedule')).toBeTruthy();
    });

    fireEvent.click(document.getElementById('btn-skip-step-pay-schedule'));

    await waitFor(() => {
      expect(screen.getByText('Skip Setup Step: Pay schedule')).toBeDefined();
    });
  });
});
