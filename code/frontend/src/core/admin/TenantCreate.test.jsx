import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TenantCreate, isTimeZone } from './TenantCreate.jsx';
import { tenantService } from './tenantService.js';
import { successMsg } from '@shared/ui/msgHelper.js';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});
vi.mock('./tenantService.js', () => ({ tenantService: { create: vi.fn(), countryTemplates: vi.fn(), setPlan: vi.fn() } }));
vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(undefined),
  errorMsg: vi.fn(),
}));

const TEMPLATES = [
  {
    countryCode: 'IN',
    sections: ['holidays', 'leave_types', 'pay_schedule', 'salary_components', 'statutory'],
    version: 1,
  },
];

describe('TenantCreate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tenantService.countryTemplates.mockResolvedValue(TEMPLATES);
  });

  it('W-73.9: under Country, says what an IN tenant starts with, and "No template" for AE', async () => {
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);

    expect(
      await screen.findByText(
        'Starts with: holidays, leave types, pay schedule, salary components, EPF and ESI',
      ),
    ).toBeDefined();

    fireEvent.change(screen.getByLabelText('Country'), { target: { value: 'ae' } });
    expect(await screen.findByText('No template — the admin sets everything up')).toBeDefined();
  });

  it('W-73.9: no line at all when the template list cannot be read, and the form still creates', async () => {
    tenantService.countryTemplates.mockRejectedValue(new Error('403'));
    tenantService.create.mockResolvedValue({ tenantId: 't-9' });
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Initech' } });
    fireEvent.change(screen.getByLabelText('Administrator email'), { target: { value: 'admin@initech.example' } });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    await waitFor(() => expect(tenantService.create).toHaveBeenCalled());
    expect(screen.queryByText(/Starts with/)).toBeNull();
    expect(screen.queryByText(/No template/)).toBeNull();
  });

  it('posts the W-12-1 field names with the defaults and returns to the list', async () => {
    tenantService.create.mockResolvedValue({ tenantId: 't-9', adminInvitationId: 'inv-1' });
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: '  Initech  ' } });
    fireEvent.change(screen.getByLabelText('Administrator email'), {
      target: { value: '  boss@initech.example  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    await waitFor(() =>
      expect(tenantService.create).toHaveBeenCalledWith({
        name: 'Initech',
        country_code: 'IN',
        timezone: 'Asia/Kolkata',
        leave_year_start_month: 4,
        modules: [],
        admin_email: 'boss@initech.example',
      }),
    );
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants'));
    expect(successMsg).toHaveBeenCalledWith(
      'Tenant created',
      'Initech is ready. An invitation was sent to boss@initech.example.',
    );
    // W-84.1 follow-up: nothing chosen under Plan, so the tenant is left unlimited and no plan call is made.
    expect(tenantService.setPlan).not.toHaveBeenCalled();
  });

  it('W-84.1: a plan, support plan and employee limit chosen on the form are saved right after the tenant is created', async () => {
    tenantService.create.mockResolvedValue({ tenantId: 't-9', adminInvitationId: 'inv-1' });
    tenantService.setPlan.mockResolvedValue({});
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Initech' } });
    fireEvent.change(screen.getByLabelText('Administrator email'), { target: { value: 'boss@initech.example' } });
    fireEvent.change(screen.getByLabelText('Employee limit'), { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    await waitFor(() => expect(tenantService.setPlan).toHaveBeenCalledWith('t-9', {
      plan: null,
      supportPlan: null,
      employeeLimit: 25,
    }));
    expect(tenantService.setPlan.mock.invocationCallOrder[0]).toBeGreaterThan(
      tenantService.create.mock.invocationCallOrder[0],
    );
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants'));
  });

  it('W-84.1: when the plan cannot be saved the tenant still counts as created and the page opens the tenant', async () => {
    tenantService.create.mockResolvedValue({ tenantId: 't-9', adminInvitationId: 'inv-1' });
    tenantService.setPlan.mockRejectedValue({ code: 'FORBIDDEN', message: 'no' });
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Initech' } });
    fireEvent.change(screen.getByLabelText('Administrator email'), { target: { value: 'boss@initech.example' } });
    fireEvent.change(screen.getByLabelText('Employee limit'), { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/tenants/t-9'));
    expect(successMsg).toHaveBeenCalledWith(
      'Tenant created',
      'Initech is ready, but the plan was not saved. Set it on the tenant page.',
    );
  });

  it('sends a given administrator email as admin_email and says the admin was invited', async () => {
    tenantService.create.mockResolvedValue({ tenantId: 't-9', adminInvitationId: 'inv-1' });
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Initech' } });
    fireEvent.change(screen.getByLabelText('Administrator email'), {
      target: { value: '  boss@initech.example  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    await waitFor(() =>
      expect(tenantService.create).toHaveBeenCalledWith({
        name: 'Initech',
        country_code: 'IN',
        timezone: 'Asia/Kolkata',
        leave_year_start_month: 4,
        modules: [],
        admin_email: 'boss@initech.example',
      }),
    );
    await waitFor(() =>
      expect(successMsg).toHaveBeenCalledWith(
        'Tenant created',
        'Initech is ready. An invitation was sent to boss@initech.example.',
      ),
    );
  });

  it('refuses a malformed administrator email before calling the server', async () => {
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Initech' } });
    fireEvent.change(screen.getByLabelText('Administrator email'), { target: { value: 'not-an-email' } });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    expect(await screen.findByText('Enter a valid email address')).toBeDefined();
    expect(tenantService.create).not.toHaveBeenCalled();
  });

  it('refuses a blank name, a three-letter country, an unknown timezone and a missing administrator email before calling the server', async () => {
    render(<MemoryRouter><TenantCreate /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Country'), { target: { value: 'IND' } });
    fireEvent.change(screen.getByLabelText('Timezone'), { target: { value: 'Mars/Olympus' } });
    fireEvent.click(screen.getByRole('button', { name: /create tenant/i }));

    expect(await screen.findByText('Name is required')).toBeDefined();
    expect(await screen.findByText('Administrator email is required')).toBeDefined();
    expect(await screen.findByText(/Not a known IANA timezone/)).toBeDefined();
    expect(await screen.findByText(/Two-letter ISO 3166-1 code/)).toBeDefined();
    expect(tenantService.create).not.toHaveBeenCalled();
  });

  it('isTimeZone accepts IANA zones only', () => {
    expect(isTimeZone('Asia/Kolkata')).toBe(true);
    expect(isTimeZone('Mars/Olympus')).toBe(false);
  });
});
