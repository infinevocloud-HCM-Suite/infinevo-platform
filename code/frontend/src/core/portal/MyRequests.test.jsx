import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import dayjs from 'dayjs';
import { MyRequests } from './MyRequests.jsx';
import { portalService } from '@shell/portal/portalService.js';
import { leaveRequestService } from '../leave/leaveRequestService.js';

vi.mock('@shell/portal/portalService.js', () => ({
  portalService: {
    getLeaveRequests: vi.fn(),
  },
}));

vi.mock('../leave/leaveRequestService.js', () => ({
  leaveRequestService: {
    submit: vi.fn(),
    withdraw: vi.fn(),
    cancel: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('MyRequests component (W-46.5 §7)', () => {
  const futureDate = dayjs().add(5, 'day').format('YYYY-MM-DD');
  const pastDate = dayjs().subtract(5, 'day').format('YYYY-MM-DD');

  const sampleRequests = [
    {
      id: 'req-draft',
      leaveTypeName: 'Casual Leave',
      fromDate: futureDate,
      toDate: futureDate,
      workingDays: 1,
      status: 'DRAFT',
      reason: 'Draft leave',
    },
    {
      id: 'req-pending',
      leaveTypeName: 'Sick Leave',
      fromDate: futureDate,
      toDate: futureDate,
      workingDays: 1,
      status: 'PENDING',
      reason: 'Pending medical test',
    },
    {
      id: 'req-approved-future',
      leaveTypeName: 'Annual Leave',
      fromDate: futureDate,
      toDate: futureDate,
      workingDays: 1,
      status: 'APPROVED',
      reason: 'Approved future vacation',
    },
    {
      id: 'req-approved-past',
      leaveTypeName: 'Casual Leave',
      fromDate: pastDate,
      toDate: pastDate,
      workingDays: 1,
      status: 'APPROVED',
      reason: 'Past approved leave',
    },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    portalService.getLeaveRequests.mockReset();
    leaveRequestService.submit.mockReset();
    leaveRequestService.withdraw.mockReset();
    leaveRequestService.cancel.mockReset();

    portalService.getLeaveRequests.mockResolvedValue(sampleRequests);
  });

  it('renders table with request rows and status tags', async () => {
    render(
      <MemoryRouter>
        <MyRequests />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Sick Leave')).toBeDefined();
    });

    expect(screen.getAllByText('Casual Leave').length).toBe(2);
    expect(screen.getByText('Annual Leave')).toBeDefined();
    expect(screen.getByText('Draft')).toBeDefined();
    expect(screen.getByText('Pending')).toBeDefined();
    expect(screen.getAllByText('Approved').length).toBe(2);
  });

  it('submits a draft request when Submit button is clicked', async () => {
    leaveRequestService.submit.mockResolvedValue({ id: 'req-draft', status: 'PENDING' });

    render(
      <MemoryRouter>
        <MyRequests requests={sampleRequests} />
      </MemoryRouter>
    );

    const submitBtn = screen.getByRole('button', { name: /submit/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(leaveRequestService.submit).toHaveBeenCalledWith('req-draft');
    });
  });

  it('Withdraw is available only on PENDING request and requires a reason', async () => {
    leaveRequestService.withdraw.mockResolvedValue({ id: 'req-pending', status: 'WITHDRAWN' });

    render(
      <MemoryRouter>
        <MyRequests requests={sampleRequests} />
      </MemoryRouter>
    );

    // Withdraw button exists for req-pending
    const withdrawBtn = screen.getByRole('button', { name: /withdraw/i });
    fireEvent.click(withdrawBtn);

    // Modal opens
    await waitFor(() => {
      expect(screen.getByText('Withdraw Leave Request')).toBeDefined();
    });

    // Clicking OK without reason triggers validation
    const modalOkBtn = screen.getByRole('button', { name: /withdraw request/i });
    fireEvent.click(modalOkBtn);

    expect(screen.getByText('Reason is required')).toBeDefined();
    expect(leaveRequestService.withdraw).not.toHaveBeenCalled();

    // Type reason
    const reasonInput = screen.getByPlaceholderText('Reason (required)');
    fireEvent.change(reasonInput, { target: { value: 'Plans changed' } });

    // Click confirm
    fireEvent.click(modalOkBtn);

    await waitFor(() => {
      expect(leaveRequestService.withdraw).toHaveBeenCalledWith('req-pending', 'Plans changed');
    });
  });

  it('Cancel is available only on APPROVED request with future start date and requires reason', async () => {
    leaveRequestService.cancel.mockResolvedValue({ id: 'req-approved-future', status: 'CANCELLED' });

    render(
      <MemoryRouter>
        <MyRequests requests={sampleRequests} />
      </MemoryRouter>
    );

    // Only 1 Cancel button should exist (for req-approved-future, not req-approved-past)
    const cancelButtons = screen.getAllByRole('button', { name: /cancel/i });
    expect(cancelButtons.length).toBe(1);

    fireEvent.click(cancelButtons[0]);

    // Modal opens
    await waitFor(() => {
      expect(screen.getByText('Cancel Approved Leave')).toBeDefined();
    });

    // Clicking OK without reason triggers validation
    const modalOkBtn = screen.getByRole('button', { name: /cancel leave/i });
    fireEvent.click(modalOkBtn);

    expect(screen.getByText('Reason is required')).toBeDefined();
    expect(leaveRequestService.cancel).not.toHaveBeenCalled();

    // Provide reason
    const reasonInput = screen.getByPlaceholderText('Reason (required)');
    fireEvent.change(reasonInput, { target: { value: 'Conference postponed' } });

    fireEvent.click(modalOkBtn);

    await waitFor(() => {
      expect(leaveRequestService.cancel).toHaveBeenCalledWith('req-approved-future', 'Conference postponed');
    });
  });

  it('View button is present on rows for detail drilldown', async () => {
    render(
      <MemoryRouter>
        <MyRequests requests={sampleRequests} />
      </MemoryRouter>
    );

    const viewButtons = screen.getAllByRole('button', { name: /view/i });
    expect(viewButtons.length).toBe(4);
  });
});
