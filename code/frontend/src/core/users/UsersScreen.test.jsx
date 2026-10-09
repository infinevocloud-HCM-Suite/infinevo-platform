import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as shellScreens from '@shell/screens';
import { UsersScreen } from './UsersScreen.jsx';
import { userService } from './userService.js';
import { userInvitationService } from '../invitation/userInvitationService.js';
import { employeeInvitationService } from '../invitation/employeeInvitationService.js';
import { routes } from '../index.js';

vi.mock('./userService.js', () => ({
  userService: { list: vi.fn(), setRoles: vi.fn(), disable: vi.fn(), enable: vi.fn(), roles: vi.fn() },
}));
vi.mock('../invitation/userInvitationService.js', () => ({
  userInvitationService: { list: vi.fn(), create: vi.fn(), resend: vi.fn(), revoke: vi.fn() },
}));
vi.mock('../invitation/employeeInvitationService.js', () => ({
  employeeInvitationService: { list: vi.fn(), create: vi.fn(), resend: vi.fn(), revoke: vi.fn() },
}));

const ROLES = [
  { id: 'r-emp', code: 'employee', name: 'Employee' },
  { id: 'r-mgr', code: 'manager', name: 'Manager' },
  { id: 'r-admin', code: 'tenant-admin', name: 'Tenant administrator' },
];

const USERS = [
  {
    id: 'u-1',
    email: 'asha@acme.test',
    displayName: 'Asha Rao',
    roles: [{ id: 'r-emp', code: 'employee', name: 'Employee' }],
    employeeId: 'e-1',
    employeeNumber: 'E-001',
    enabled: true,
  },
  {
    id: 'u-2',
    email: 'old@acme.test',
    displayName: 'old@acme.test',
    roles: [],
    employeeId: null,
    employeeNumber: null,
    enabled: false,
  },
];

function can(actions) {
  vi.spyOn(shellScreens, 'useCan').mockImplementation((action) => actions.includes(action));
}

function renderAt(path = '/users') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/users" element={<UsersScreen />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function pick(selectId, label) {
  fireEvent.mouseDown(document.getElementById(selectId));
  const option = await waitFor(() => {
    const found = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
      (el) => el.getAttribute('title') === label,
    );
    if (!found) throw new Error(`option ${label} not rendered yet`);
    return found;
  });
  fireEvent.click(option);
}

describe('UsersScreen (W-73.4)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
    userService.list.mockResolvedValue(USERS);
    userService.roles.mockResolvedValue(ROLES);
    userService.setRoles.mockResolvedValue({});
    userService.disable.mockResolvedValue();
    userInvitationService.list.mockResolvedValue([
      { id: 'ui-1', email: 'new.admin@acme.test', status: 'PENDING', createdAt: '2026-10-02T10:00:00Z' },
    ]);
    employeeInvitationService.list.mockResolvedValue([
      { id: 'ei-1', email: 'joiner@acme.test', status: 'PENDING', createdAt: '2026-10-03T10:00:00Z' },
    ]);
  });

  it('shows NotEntitled without core.user.manage and loads nothing', () => {
    can([]);
    renderAt();
    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
    expect(userService.list).not.toHaveBeenCalled();
  });

  it('lists users with roles, linked employee and status', async () => {
    can(['core.user.manage', 'core.role.assign']);
    renderAt();
    expect(await screen.findByText('Asha Rao')).toBeDefined();
    expect(screen.getByText('E-001').closest('a').getAttribute('href')).toBe('/employees/e-1');
    expect(screen.getByText('Active')).toBeDefined();
    expect(screen.getByText('Disabled')).toBeDefined();
    expect(document.getElementById('btn-enable-u-2')).not.toBeNull();
  });

  it('Change roles sends the chosen role ids', async () => {
    can(['core.user.manage', 'core.role.assign']);
    renderAt();
    await screen.findByText('Asha Rao');
    fireEvent.click(document.getElementById('btn-change-roles-u-1'));
    await waitFor(() => expect(userService.roles).toHaveBeenCalled());
    await pick('select-user-roles', 'Manager');
    fireEvent.click(document.getElementById('btn-save-roles'));
    await waitFor(() => expect(userService.setRoles).toHaveBeenCalledWith('u-1', ['r-emp', 'r-mgr']));
  });

  it('shows the server refusal when roles cannot change', async () => {
    can(['core.user.manage', 'core.role.assign']);
    userService.setRoles.mockRejectedValue({ message: 'You cannot remove your own tenant-admin role.' });
    renderAt();
    await screen.findByText('Asha Rao');
    fireEvent.click(document.getElementById('btn-change-roles-u-1'));
    await waitFor(() => expect(userService.roles).toHaveBeenCalled());
    fireEvent.click(document.getElementById('btn-save-roles'));
    expect(await screen.findByText('You cannot remove your own tenant-admin role.')).toBeDefined();
  });

  it('hides Change roles without core.role.assign', async () => {
    can(['core.user.manage']);
    renderAt();
    await screen.findByText('Asha Rao');
    expect(document.getElementById('btn-change-roles-u-1')).toBeNull();
  });

  it('Disable asks first, then calls the endpoint', async () => {
    can(['core.user.manage']);
    renderAt();
    await screen.findByText('Asha Rao');
    fireEvent.click(document.getElementById('btn-disable-u-1'));
    fireEvent.click(await waitFor(() => document.getElementById('btn-confirm-disable-u-1')));
    await waitFor(() => expect(userService.disable).toHaveBeenCalledWith('u-1'));
  });

  it('?tab=invitations shows both kinds in one table with a Kind column', async () => {
    can(['core.user.manage', 'core.employee.create']);
    renderAt('/users?tab=invitations');
    const userRow = (await screen.findByText('new.admin@acme.test')).closest('tr');
    const employeeRow = screen.getByText('joiner@acme.test').closest('tr');
    expect(within(userRow).getByText('User')).toBeDefined();
    expect(within(employeeRow).getByText('Employee')).toBeDefined();
    expect(userInvitationService.list).toHaveBeenCalledWith({ status: 'PENDING' });
    expect(employeeInvitationService.list).toHaveBeenCalledWith({ status: 'PENDING' });
  });

  it('Resend goes to the endpoint of the row kind', async () => {
    can(['core.user.manage', 'core.employee.create']);
    employeeInvitationService.resend.mockResolvedValue({});
    renderAt('/users?tab=invitations');
    await screen.findByText('joiner@acme.test');
    fireEvent.click(document.getElementById('btn-resend-ei-1'));
    await waitFor(() => expect(employeeInvitationService.resend).toHaveBeenCalledWith('ei-1'));
    expect(userInvitationService.resend).not.toHaveBeenCalled();
  });

  it('reads employee invitations only with core.employee.create', async () => {
    can(['core.user.manage']);
    renderAt('/users?tab=invitations');
    await screen.findByText('new.admin@acme.test');
    expect(employeeInvitationService.list).not.toHaveBeenCalled();
    expect(screen.queryByText('joiner@acme.test')).toBeNull();
  });

  it('Invite user sends email and roles', async () => {
    can(['core.user.manage', 'core.role.assign']);
    userInvitationService.create.mockResolvedValue({ id: 'ui-2' });
    renderAt('/users?tab=invitations');
    await screen.findByText('new.admin@acme.test');
    fireEvent.click(document.getElementById('btn-invite-user'));
    fireEvent.change(await waitFor(() => document.getElementById('input-invite-email')), {
      target: { value: 'hr.lead@acme.test' },
    });
    await pick('select-invite-roles', 'Manager');
    fireEvent.click(document.getElementById('btn-submit-invite'));
    await waitFor(() =>
      expect(userInvitationService.create).toHaveBeenCalledWith({ email: 'hr.lead@acme.test', roleIds: ['r-mgr'] }),
    );
  });
});

describe('old invitation routes (W-73.4)', () => {
  function Where() {
    const location = useLocation();
    return <div>at {location.pathname + location.search}</div>;
  }

  it.each(['/invitations/users', '/invitations/employees'])('%s redirects to the Invitations tab', async (path) => {
    const route = routes.find((r) => r.path === path);
    expect(route.mountWith).toBe('/users');
    render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={path} element={route.element} />
          <Route path="/users" element={<Where />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('at /users?tab=invitations')).toBeDefined();
  });
});
