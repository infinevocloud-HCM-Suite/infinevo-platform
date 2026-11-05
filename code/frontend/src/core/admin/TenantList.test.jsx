import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TenantList, adminInvitationLabel } from './TenantList.jsx';
import { tenantService } from './tenantService.js';
import { PLATFORM_TENANT_ID } from './platform.js';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('./tenantService.js', () => ({
  tenantService: { list: vi.fn() },
}));

const rows = [
  {
    tenant_id: 'tenant-acme',
    name: 'Acme',
    status: 'ACTIVE',
    modules: ['PAYROLL'],
    created_at: '2026-09-01T00:00:00Z',
    user_count: 3,
    employee_count: 12,
    employee_limit: 50,
    admin_invitation: { email: 'boss@acme.example', status: 'PENDING' },
  },
  {
    tenant_id: 'tenant-globex',
    name: 'Globex',
    status: 'ACTIVE',
    modules: ['HRMS'],
    created_at: '2026-09-02T00:00:00Z',
    user_count: 4,
    employee_count: 12,
    employee_limit: null,
    admin_invitation: { email: 'hank@globex.example', status: 'ACCEPTED' },
  },
  {
    tenant_id: 'tenant-initech',
    name: 'Initech',
    status: 'ACTIVE',
    modules: ['HRMS', 'PAYROLL'],
    created_at: '2026-09-03T00:00:00Z',
    user_count: 2,
    employee_count: 55,
    employee_limit: 50,
    admin_invitation: { email: 'bill@initech.example', status: 'ACCEPTED' },
  },
  {
    tenant_id: PLATFORM_TENANT_ID,
    name: 'Infinevo',
    status: 'ACTIVE',
    modules: [],
    created_at: '2026-09-01T00:00:00Z',
    user_count: 1,
    employee_count: 0,
    employee_limit: null,
    admin_invitation: { email: null, status: 'NONE' },
  },
];

function rowOf(name) {
  return screen.getByText(name).closest('tr');
}

describe('TenantList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tenantService.list.mockResolvedValue(rows);
  });

  it('lists tenants with status and module tags', async () => {
    render(<MemoryRouter><TenantList /></MemoryRouter>);
    expect(await screen.findByText('Acme')).toBeDefined();
    const acme = rowOf('Acme');
    expect(within(acme).getByText('PAYROLL')).toBeDefined();
    expect(within(acme).getByText('Active')).toBeDefined();
  });

  it('tags the Infinevo row "platform" and gives it no actions', async () => {
    render(<MemoryRouter><TenantList /></MemoryRouter>);
    await screen.findByText('Infinevo');
    const infinevo = rowOf('Infinevo');
    expect(within(infinevo).getByText('platform')).toBeDefined();
    expect(within(infinevo).queryAllByRole('button')).toHaveLength(0);

    const acme = rowOf('Acme');
    expect(within(acme).queryByText('platform')).toBeNull();
    fireEvent.click(within(acme).getByRole('button', { name: /open acme/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants/tenant-acme');
  });

  it('shows the administrator invitation in an Admin column', async () => {
    render(<MemoryRouter><TenantList /></MemoryRouter>);
    await screen.findByText('Acme');
    expect(screen.getByRole('columnheader', { name: 'Admin' })).toBeDefined();
    expect(within(rowOf('Acme')).getByText('invited boss@acme.example')).toBeDefined();
    expect(within(rowOf('Globex')).getByText('accepted')).toBeDefined();
    expect(within(rowOf('Infinevo')).getByText('—')).toBeDefined();
  });

  it('W-84.1: shows employees against the limit, the bare count with none, red when over', async () => {
    render(<MemoryRouter><TenantList /></MemoryRouter>);
    await screen.findByText('Acme');
    expect(screen.getByRole('columnheader', { name: 'Employees' })).toBeDefined();

    const acme = within(rowOf('Acme')).getByText('12 / 50');
    expect(acme.closest('.ant-typography-danger')).toBeNull();
    expect(within(rowOf('Globex')).getByText('12')).toBeDefined();
    const initech = within(rowOf('Initech')).getByText('55 / 50');
    expect(initech.closest('.ant-typography-danger')).not.toBeNull();
  });

  it('adminInvitationLabel reads PENDING, ACCEPTED, NONE and a missing value', () => {
    expect(adminInvitationLabel({ email: 'a@b.example', status: 'PENDING' })).toBe('invited a@b.example');
    expect(adminInvitationLabel({ email: 'a@b.example', status: 'ACCEPTED' })).toBe('accepted');
    expect(adminInvitationLabel({ email: null, status: 'NONE' })).toBe('—');
    expect(adminInvitationLabel(undefined)).toBe('—');
  });

  it('"New tenant" goes to the create form', async () => {
    render(<MemoryRouter><TenantList /></MemoryRouter>);
    await screen.findByText('Acme');
    fireEvent.click(screen.getByRole('button', { name: /new tenant/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants/new');
  });

  it('a failed load shows the error with Retry and no rows; Retry lists them (D-85)', async () => {
    tenantService.list.mockReset();
    tenantService.list
      .mockRejectedValueOnce(Object.assign(new Error('Gateway timeout'), { status: 504 }))
      .mockResolvedValue(rows);
    render(<MemoryRouter><TenantList /></MemoryRouter>);
    expect(await screen.findByText('Gateway timeout')).toBeDefined();
    expect(screen.queryByRole('table')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Acme')).toBeDefined();
    expect(tenantService.list).toHaveBeenCalledTimes(2);
  });
});
