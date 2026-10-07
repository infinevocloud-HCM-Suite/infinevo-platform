import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { RoleMatrixScreen } from './RoleMatrixScreen';
import { roleService } from './roleService';

vi.mock('./roleService', () => ({
  roleService: {
    list: vi.fn(),
    listActions: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@shell/authz/useCan', () => ({
  useCan: vi.fn().mockReturnValue(true),
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('RoleMatrixScreen component', () => {
  const sampleRoles = [
    {
      id: 'role-1-uuid',
      code: 'platform-admin',
      name: 'Platform administrator',
      system: true,
      actionCodes: ['core.tenant.read', 'core.user.read'],
    },
    {
      id: 'role-2-uuid',
      code: 'leave-auditor',
      name: 'Leave Auditor',
      system: false,
      actionCodes: ['core.leave.read'],
    },
  ];

  const sampleActions = [
    { code: 'core.leave.read', name: 'Read leaves', module: 'core', description: 'Read leave data' },
    { code: 'core.user.read', name: 'Read users', module: 'core', description: 'Read users' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders roles table and fetches roles and actions on mount', async () => {
    roleService.list.mockResolvedValueOnce(sampleRoles);
    roleService.listActions.mockResolvedValueOnce(sampleActions);

    render(<RoleMatrixScreen />);

    expect(screen.getByText('Roles & Permissions Management')).toBeTruthy();

    await waitFor(() => {
      expect(roleService.list).toHaveBeenCalled();
      expect(roleService.listActions).toHaveBeenCalled();
      expect(screen.getByText('Platform administrator')).toBeTruthy();
      expect(screen.getByText('Leave Auditor')).toBeTruthy();
      expect(screen.getByText('System Role')).toBeTruthy();
      expect(screen.getByText('Custom Role')).toBeTruthy();
    });
  });

  it('opens create custom role modal when clicking Create Custom Role button', async () => {
    roleService.list.mockResolvedValueOnce(sampleRoles);
    roleService.listActions.mockResolvedValueOnce(sampleActions);

    render(<RoleMatrixScreen />);

    await waitFor(() => {
      expect(screen.getByText('Leave Auditor')).toBeTruthy();
    });

    const createBtn = screen.getByTestId('btn-create-role');
    fireEvent.click(createBtn);

    await waitFor(() => {
      expect(screen.getByText('Create New Custom Role')).toBeTruthy();
      expect(screen.getByTestId('input-role-name')).toBeTruthy();
    });
  });

  it('opens view permissions drawer when clicking view button', async () => {
    roleService.list.mockResolvedValueOnce(sampleRoles);
    roleService.listActions.mockResolvedValueOnce(sampleActions);

    render(<RoleMatrixScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('btn-view-role-leave-auditor')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('btn-view-role-leave-auditor'));

    await waitFor(() => {
      expect(screen.getByText('Role Permissions:', { exact: false })).toBeTruthy();
    });
  });
});
