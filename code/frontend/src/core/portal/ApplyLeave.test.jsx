import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import dayjs from 'dayjs';
import { ApplyLeave } from './ApplyLeave.jsx';
import { portalService } from '@shell/portal/portalService.js';
import { leaveTypeService } from '../leave/leaveTypeService.js';
import { leaveRequestService } from '../leave/leaveRequestService.js';
import * as useCanModule from '@shell/screens';

vi.mock('@shell/portal/portalService.js', () => ({
  portalService: {
    getPanels: vi.fn(),
    getProfile: vi.fn(),
  },
}));

vi.mock('../leave/leaveTypeService.js', () => ({
  leaveTypeService: {
    eligible: vi.fn(),
  },
}));

vi.mock('../leave/leaveRequestService.js', () => ({
  leaveRequestService: {
    create: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('ApplyLeave component (W-46.5 §7)', () => {
  const me = { id: 'emp-101', firstName: 'John', lastName: 'Doe' };
  const panelsWithLeave = [{ code: 'profile', title: 'My Profile' }, { code: 'leave', title: 'My Leave' }];
  const eligibleTypes = [
    { id: 'type-cl', code: 'CL', name: 'Casual Leave' },
    { id: 'type-sl', code: 'SL', name: 'Sick Leave' },
  ];

  const defaultValues = {
    leaveTypeId: 'type-cl',
    fromDate: dayjs('2026-10-10'),
    toDate: dayjs('2026-10-12'),
    reason: 'Family trip',
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    portalService.getPanels.mockReset();
    portalService.getProfile.mockReset();
    leaveTypeService.eligible.mockReset();
    leaveRequestService.create.mockReset();

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    portalService.getPanels.mockResolvedValue(panelsWithLeave);
    portalService.getProfile.mockResolvedValue(me);
    leaveTypeService.eligible.mockResolvedValue(eligibleTypes);
  });

  it('renders NotEntitled when caller lacks core.leave.apply', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);

    render(
      <MemoryRouter>
        <ApplyLeave />
      </MemoryRouter>
    );

    expect(screen.getByText(/module not subscribed/i)).toBeDefined();
  });

  it('renders NotEntitled when leave panel is not returned for caller', async () => {
    portalService.getPanels.mockResolvedValue([{ code: 'profile', title: 'My Profile' }]);

    render(
      <MemoryRouter>
        <ApplyLeave />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/module not subscribed/i)).toBeDefined();
    });
  });

  it('loads eligible leave types from eligible(me)', async () => {
    render(
      <MemoryRouter>
        <ApplyLeave />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(leaveTypeService.eligible).toHaveBeenCalledWith('emp-101');
    });

    expect(screen.getByText('Apply for Leave')).toBeDefined();
    expect(screen.getByRole('button', { name: /save draft/i })).toBeDefined();
    expect(screen.getByRole('button', { name: /submit/i })).toBeDefined();
  });

  it('Save draft sends submit: false with form values', async () => {
    leaveRequestService.create.mockResolvedValue({ id: 'req-draft-1', status: 'DRAFT' });

    render(
      <MemoryRouter>
        <ApplyLeave initialValues={defaultValues} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(leaveTypeService.eligible).toHaveBeenCalledWith('emp-101');
    });

    const draftBtn = screen.getByRole('button', { name: /save draft/i });
    fireEvent.click(draftBtn);

    await waitFor(() => {
      expect(leaveRequestService.create).toHaveBeenCalledWith(
        expect.objectContaining({
          leaveTypeId: 'type-cl',
          fromDate: '2026-10-10',
          toDate: '2026-10-12',
          reason: 'Family trip',
          submit: false,
        })
      );
    });
  });

  it('Submit sends submit: true with form values', async () => {
    leaveRequestService.create.mockResolvedValue({ id: 'req-sub-1', status: 'SUBMITTED' });

    render(
      <MemoryRouter>
        <ApplyLeave initialValues={defaultValues} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(leaveTypeService.eligible).toHaveBeenCalledWith('emp-101');
    });

    const submitBtn = screen.getByRole('button', { name: /submit/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(leaveRequestService.create).toHaveBeenCalledWith(
        expect.objectContaining({
          leaveTypeId: 'type-cl',
          fromDate: '2026-10-10',
          toDate: '2026-10-12',
          reason: 'Family trip',
          submit: true,
        })
      );
    });
  });

  it('balance error or 409 renders the envelope message', async () => {
    const errorResponse = {
      response: {
        status: 409,
        data: {
          code: 'LEAVE_BALANCE_EXCEEDED',
          message: 'Requested leave days exceed available balance (10 remaining)',
        },
      },
    };
    leaveRequestService.create.mockRejectedValue(errorResponse);

    render(
      <MemoryRouter>
        <ApplyLeave initialValues={defaultValues} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(leaveTypeService.eligible).toHaveBeenCalledWith('emp-101');
    });

    const submitBtn = screen.getByRole('button', { name: /submit/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Requested leave days exceed available balance (10 remaining)')).toBeDefined();
    });
  });

  it('renders attachments field for supporting documents', async () => {
    render(
      <MemoryRouter>
        <ApplyLeave />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(leaveTypeService.eligible).toHaveBeenCalled();
    });

    expect(screen.getByText(/supporting documents \/ attachments/i)).toBeDefined();
    expect(screen.getByRole('button', { name: /attach document/i })).toBeDefined();
  });
});
