import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TenantList } from './TenantList.jsx';
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
  },
  {
    tenant_id: PLATFORM_TENANT_ID,
    name: 'Infinevo',
    status: 'ACTIVE',
    modules: [],
    created_at: '2026-09-01T00:00:00Z',
    user_count: 1,
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
    expect(within(acme).getByText('ACTIVE')).toBeDefined();
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

  it('"New tenant" goes to the create form', async () => {
    render(<MemoryRouter><TenantList /></MemoryRouter>);
    await screen.findByText('Acme');
    fireEvent.click(screen.getByRole('button', { name: /new tenant/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants/new');
  });
});
