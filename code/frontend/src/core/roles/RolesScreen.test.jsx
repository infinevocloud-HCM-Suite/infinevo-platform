import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as shellScreens from '@shell/screens';
import { RolesScreen } from './RolesScreen.jsx';
import { rolesService } from './rolesService.js';

vi.mock('./rolesService.js', () => ({
  rolesService: { list: vi.fn(), actions: vi.fn() },
}));

// Shaped like RoleResponse and ActionResponse (core/authz).
const ROLES = [
  {
    id: 'r-hr',
    tenantId: 't-1',
    code: 'hr',
    name: 'HR',
    system: true,
    actionCodes: ['core.employee.read', 'core.leave.manage'],
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
  {
    id: 'r-aud',
    tenantId: 't-1',
    code: 'auditor',
    name: 'Auditor',
    system: false,
    actionCodes: ['core.audit.read'],
    createdAt: '2026-09-02T00:00:00Z',
    updatedAt: '2026-09-02T00:00:00Z',
  },
];

const ACTIONS = [
  { code: 'core.audit.read', name: 'Read audit', module: 'CORE', description: 'Read the audit trail' },
  { code: 'core.employee.read', name: 'Read employees', module: 'CORE', description: 'See every employee' },
  { code: 'core.leave.manage', name: 'Manage leave', module: 'CORE', description: null },
];

function can(actions) {
  vi.spyOn(shellScreens, 'useCan').mockImplementation((action) => actions.includes(action));
}

function renderScreen() {
  return render(
    <MemoryRouter initialEntries={['/roles']}>
      <RolesScreen />
    </MemoryRouter>,
  );
}

describe('RolesScreen (D-73)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    rolesService.list.mockResolvedValue(ROLES);
    rolesService.actions.mockResolvedValue(ACTIONS);
  });

  it('lists each role with its code, whether it is a system role and how many actions it holds', async () => {
    can(['core.role.read']);
    renderScreen();

    expect(screen.getByRole('heading', { name: 'Roles' })).toBeDefined();
    const hrRow = (await screen.findByText('HR')).closest('tr');
    expect(within(hrRow).getByText('hr')).toBeDefined();
    expect(within(hrRow).getByText('System')).toBeDefined();
    expect(within(hrRow).getByText('2')).toBeDefined();
    const audRow = screen.getByText('Auditor').closest('tr');
    expect(within(audRow).getByText('Custom')).toBeDefined();
    expect(within(audRow).getByText('1')).toBeDefined();
  });

  it('expands a role to its action codes with the catalogue description', async () => {
    can(['core.role.read']);
    renderScreen();

    const hrRow = (await screen.findByText('HR')).closest('tr');
    fireEvent.click(within(hrRow).getByRole('button', { name: /expand row/i }));

    await waitFor(() => expect(screen.getByText('core.employee.read')).toBeDefined());
    expect(screen.getByText('See every employee')).toBeDefined();
    expect(screen.getByText('core.leave.manage')).toBeDefined();
    expect(screen.getByText('Manage leave')).toBeDefined();
  });

  it('still lists the roles when the action catalogue cannot be read', async () => {
    can(['core.role.read']);
    rolesService.actions.mockRejectedValue(new Error('boom'));
    renderScreen();

    expect(await screen.findByText('Auditor')).toBeDefined();
  });

  it('shows Not entitled without core.role.read and asks the server nothing', () => {
    can([]);
    renderScreen();

    expect(screen.queryByRole('heading', { name: 'Roles' })).toBeNull();
    expect(rolesService.list).not.toHaveBeenCalled();
  });
});
