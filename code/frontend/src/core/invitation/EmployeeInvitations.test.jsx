import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EmployeeInvitations } from './EmployeeInvitations.jsx';
import { employeeInvitationService } from './employeeInvitationService.js';
import { apiClient } from '@shared/api/client.js';
import * as shellScreens from '@shell/screens';

vi.mock('./employeeInvitationService.js', () => ({
  employeeInvitationService: {
    list: vi.fn(),
    create: vi.fn(),
    resend: vi.fn(),
    revoke: vi.fn(),
  },
}));

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('EmployeeInvitations component', () => {
  const mockEmployees = [
    {
      id: 'emp-1',
      employeeNumber: 'EMP-001',
      firstName: 'Jane',
      lastName: 'Doe',
      workEmail: 'jane.doe@example.com',
    },
  ];

  const mockInvitations = [
    {
      id: 'emp-inv-1',
      email: 'jane.doe@example.com',
      status: 'PENDING',
      expiresAt: '2026-10-15T12:00:00Z',
      createdAt: '2026-10-01T12:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    employeeInvitationService.list.mockResolvedValue(mockInvitations);
    apiClient.get.mockResolvedValue({ data: { content: mockEmployees } });
  });

  it('renders NotEntitled when user lacks core.employee.create', () => {
    vi.spyOn(shellScreens, 'useCan').mockReturnValue(false);

    render(<EmployeeInvitations />);
    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
    expect(employeeInvitationService.list).not.toHaveBeenCalled();
  });

  it('renders invitation list when user has core.employee.create', async () => {
    vi.spyOn(shellScreens, 'useCan').mockReturnValue(true);

    render(<EmployeeInvitations />);

    expect(screen.getByRole('heading', { name: /Employee Invitations/i })).toBeDefined();
    await waitFor(() => {
      expect(employeeInvitationService.list).toHaveBeenCalled();
      expect(screen.getByText('jane.doe@example.com')).toBeDefined();
    });
  });

  it('opens modal, has no free-text email input, and posts employeeId', async () => {
    vi.spyOn(shellScreens, 'useCan').mockReturnValue(true);
    employeeInvitationService.create.mockResolvedValue({ id: 'new-emp-inv', status: 'PENDING' });

    render(<EmployeeInvitations />);

    const inviteBtn = screen.getByRole('button', { name: /Invite Employee/i });
    fireEvent.click(inviteBtn);

    // Modal opens
    expect(screen.getByText('Invite Employee to Platform')).toBeDefined();

    // Verify there is NO free-text email input field
    expect(document.getElementById('input-invite-email')).toBeNull();

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/v1/employees', expect.anything());
    });

    // Submitting without selecting an employee triggers validation error
    const submitBtn = screen.getByRole('button', { name: /Send Invitation/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Please select an employee')).toBeDefined();
    });
    expect(employeeInvitationService.create).not.toHaveBeenCalled();

    // Select employee
    const selectElem = document.getElementById('select-invite-employee');
    fireEvent.mouseDown(selectElem);

    await waitFor(() => {
      expect(screen.getByText(/Jane Doe/i)).toBeDefined();
    });
    fireEvent.click(screen.getByText(/Jane Doe/i));

    // Shows employee email in display
    expect(document.getElementById('display-employee-email').textContent).toBe(
      'jane.doe@example.com',
    );

    // Submit form
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(employeeInvitationService.create).toHaveBeenCalledWith({
        employeeId: 'emp-1',
      });
    });
  });
});
