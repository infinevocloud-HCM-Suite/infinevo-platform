import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TenantCreate, isTimeZone } from './TenantCreate.jsx';
import { tenantService } from './tenantService.js';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});
vi.mock('./tenantService.js', () => ({ tenantService: { create: vi.fn() } }));
vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(undefined),
  errorMsg: vi.fn(),
}));

describe('TenantCreate', () => {
  beforeEach(() => vi.clearAllMocks());

  it('posts the W-12-1 field names with the defaults and returns to the list', async () => {
    tenantService.create.mockResolvedValue({ tenantId: 't-9' });
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: '  Initech  ' } });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    await waitFor(() =>
      expect(tenantService.create).toHaveBeenCalledWith({
        name: 'Initech',
        country_code: 'IN',
        timezone: 'Asia/Kolkata',
        leave_year_start_month: 4,
        modules: [],
      }),
    );
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants'));
  });

  it('refuses a blank name, a three-letter country and an unknown timezone before calling the server', async () => {
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Country'), { target: { value: 'IND' } });
    fireEvent.change(screen.getByLabelText('Timezone'), { target: { value: 'Mars/Olympus' } });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    expect(await screen.findByText('Name is required')).toBeDefined();
    expect(await screen.findByText(/Not a known IANA timezone/)).toBeDefined();
    expect(await screen.findByText(/Two-letter ISO 3166-1 code/)).toBeDefined();
    expect(tenantService.create).not.toHaveBeenCalled();
  });

  it('isTimeZone accepts IANA zones only', () => {
    expect(isTimeZone('Asia/Kolkata')).toBe(true);
    expect(isTimeZone('Mars/Olympus')).toBe(false);
  });
});
