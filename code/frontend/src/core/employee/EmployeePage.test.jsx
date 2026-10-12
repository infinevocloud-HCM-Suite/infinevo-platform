import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { EmployeePage } from './EmployeePage.jsx';
import { employeeService } from './employeeService.js';
import { employeeInvitationService } from '../invitation/employeeInvitationService.js';
import employeeReducer from './employeeSlice.js';
import * as useCanModule from '@shell/screens';
import { ModuleEmployeeTabsProvider } from '@shell/navigation/moduleEmployeeTabs.js';
import { moduleEmployeeTabs } from '@shell/moduleTabs.js';

vi.mock('./employeeService.js', () => ({
  employeeService: {
    get: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    section: vi.fn().mockResolvedValue({}),
    getAccess: vi.fn(),
  },
}));

vi.mock('../invitation/employeeInvitationService.js', () => ({
  employeeInvitationService: {
    create: vi.fn(),
    resend: vi.fn(),
    revoke: vi.fn(),
  },
}));

vi.mock('./orgMasterService.js', () => ({
  orgMasterService: {
    all: vi.fn().mockResolvedValue({ departments: {}, designations: {}, workLocations: {} }),
  },
}));

vi.mock('./tabs/DocumentsTab.jsx', () => ({
  DocumentsTab: vi.fn(() => <div data-testid="documents-tab">DocumentsTab</div>),
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('EmployeePage component', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        employee: employeeReducer,
      },
    });

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.getAccess.mockResolvedValue({
      state: 'NONE',
      invitationId: null,
      expiresAt: null,
      roles: [],
    });
  });

  function renderPage(employeeId) {
    return render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[`/employees/${employeeId}`]}>
          <Routes>
            <Route path="/employees/:id" element={<EmployeePage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );
  }

  it('offers Reactivate and Terminate on a SUSPENDED employee, and Reactivate sends ACTIVE', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-susp-1',
      employeeNumber: 'EMP-999',
      firstName: 'Bob',
      lastName: 'Marley',
      status: 'SUSPENDED',
    });
    employeeService.update.mockResolvedValueOnce({
      id: 'emp-susp-1',
      employeeNumber: 'EMP-999',
      firstName: 'Bob',
      lastName: 'Marley',
      status: 'ACTIVE',
    });

    renderPage('emp-susp-1');

    await waitFor(() => expect(document.getElementById('btn-delete-employee')).not.toBeNull());
    expect(document.getElementById('btn-reactivate-employee')).not.toBeNull();
    expect(document.getElementById('btn-terminate-employee')).not.toBeNull();

    fireEvent.click(document.getElementById('btn-reactivate-employee'));

    await waitFor(() => {
      const confirmBtn = document.querySelector('.ant-modal-confirm-btns .ant-btn-primary');
      expect(confirmBtn).not.toBeNull();
      fireEvent.click(confirmBtn);
    });

    await waitFor(() => {
      expect(employeeService.update).toHaveBeenCalledWith(
        'emp-susp-1',
        expect.objectContaining({ status: 'ACTIVE', terminationDate: null })
      );
    });
  });

  it('offers neither Reactivate nor Terminate on a TERMINATED employee - the status is terminal', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-term-1',
      employeeNumber: 'EMP-998',
      firstName: 'Peter',
      lastName: 'Tosh',
      status: 'TERMINATED',
    });

    renderPage('emp-term-1');

    await waitFor(() => expect(document.getElementById('btn-delete-employee')).not.toBeNull());
    expect(document.getElementById('btn-reactivate-employee')).toBeNull();
    expect(document.getElementById('btn-terminate-employee')).toBeNull();
    expect(document.getElementById('btn-delete-employee')).not.toBeNull();
  });

  it('offers Terminate but not Reactivate on an ACTIVE employee', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-act-1',
      employeeNumber: 'EMP-997',
      firstName: 'Rita',
      lastName: 'Marley',
      status: 'ACTIVE',
    });

    renderPage('emp-act-1');

    await waitFor(() => expect(document.getElementById('btn-delete-employee')).not.toBeNull());
    expect(document.getElementById('btn-terminate-employee')).not.toBeNull();
    expect(document.getElementById('btn-reactivate-employee')).toBeNull();
  });

  it('hides Identification, Bank and Reporting Line tabs when read permissions are missing', async () => {
    employeeService.get.mockResolvedValueOnce({
      id: 'emp-norm-1',
      employeeNumber: 'EMP-100',
      firstName: 'Alice',
      lastName: 'Wonder',
      status: 'ACTIVE',
    });

    vi.spyOn(useCanModule, 'useCan').mockImplementation((perm) => {
      if (
        perm === 'core.employee_identification.read' ||
        perm === 'core.employee_bank.read' ||
        perm === 'core.org.read'
      ) {
        return false;
      }
      return true;
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/employees/emp-norm-1']}>
          <Routes>
            <Route path="/employees/:id" element={<EmployeePage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Overview')).toBeDefined();
      expect(screen.getByText('Personal')).toBeDefined();
      expect(screen.getByText('Contact')).toBeDefined();
      expect(screen.getByText('Employment')).toBeDefined();
      expect(screen.queryByText('Identification')).toBeNull();
      expect(screen.queryByText('Bank')).toBeNull();
      expect(screen.queryByText('Reporting Line')).toBeNull();
    });
  });

  const ACTIVE_EMPLOYEE = {
    id: 'emp-acc-1',
    employeeNumber: 'EMP-200',
    firstName: 'Asha',
    lastName: 'Rao',
    status: 'ACTIVE',
  };

  it('shows No access with an Invite button that invites with no extra roles', async () => {
    employeeService.get.mockResolvedValueOnce(ACTIVE_EMPLOYEE);
    employeeInvitationService.create.mockResolvedValueOnce({ id: 'inv-9' });

    renderPage('emp-acc-1');

    await waitFor(() => expect(screen.getByText('No access')).toBeDefined());
    expect(employeeService.getAccess).toHaveBeenCalledWith('emp-acc-1');
    const invite = document.getElementById('btn-invite-employee');
    expect(invite).not.toBeNull();

    fireEvent.click(invite);

    await waitFor(() =>
      expect(employeeInvitationService.create).toHaveBeenCalledWith({
        employeeId: 'emp-acc-1',
        roleIds: [],
      })
    );
    await waitFor(() => expect(employeeService.getAccess).toHaveBeenCalledTimes(2));
  });

  it('shows Invited with the expiry date and a Resend button', async () => {
    employeeService.get.mockResolvedValueOnce(ACTIVE_EMPLOYEE);
    employeeService.getAccess.mockResolvedValue({
      state: 'INVITED',
      invitationId: 'inv-1',
      expiresAt: '2026-10-15T10:00:00Z',
      roles: [{ id: 'role-employee', code: 'employee', name: 'Employee' }],
    });
    employeeInvitationService.resend.mockResolvedValueOnce({ id: 'inv-2' });

    renderPage('emp-acc-1');

    await waitFor(() => expect(screen.getByText('Invited, expires 15 Oct 2026')).toBeDefined());
    expect(document.getElementById('btn-invite-employee')).toBeNull();
    const resend = document.getElementById('btn-resend-invitation');
    expect(resend).not.toBeNull();

    fireEvent.click(resend);

    await waitFor(() => expect(employeeInvitationService.resend).toHaveBeenCalledWith('inv-1'));
  });

  it('W-73.4: Revoke asks first, then revokes the pending invitation', async () => {
    employeeService.get.mockResolvedValueOnce(ACTIVE_EMPLOYEE);
    employeeService.getAccess.mockResolvedValue({
      state: 'INVITED',
      invitationId: 'inv-1',
      expiresAt: '2026-10-15T10:00:00Z',
      roles: [],
    });
    employeeInvitationService.revoke.mockResolvedValueOnce(undefined);

    renderPage('emp-acc-1');

    await waitFor(() => expect(document.getElementById('btn-revoke-invitation')).not.toBeNull());
    fireEvent.click(document.getElementById('btn-revoke-invitation'));
    expect(employeeInvitationService.revoke).not.toHaveBeenCalled();
    fireEvent.click(await waitFor(() => {
      const ok = document.getElementById('btn-confirm-revoke-invitation');
      if (!ok) throw new Error('confirm not rendered yet');
      return ok;
    }));

    await waitFor(() => expect(employeeInvitationService.revoke).toHaveBeenCalledWith('inv-1'));
  });

  it('shows Active with the account roles and no invite action', async () => {
    employeeService.get.mockResolvedValueOnce(ACTIVE_EMPLOYEE);
    employeeService.getAccess.mockResolvedValue({
      state: 'ACTIVE',
      invitationId: null,
      expiresAt: null,
      roles: [
        { id: 'role-employee', code: 'employee', name: 'Employee' },
        { id: 'role-hr', code: 'hr', name: 'HR' },
      ],
    });

    renderPage('emp-acc-1');

    await waitFor(() => expect(document.getElementById('tag-access')?.textContent).toBe('Active'));
    expect(screen.getByText('employee')).toBeDefined();
    expect(screen.getByText('hr')).toBeDefined();
    expect(document.getElementById('btn-invite-employee')).toBeNull();
    expect(document.getElementById('btn-resend-invitation')).toBeNull();
    expect(document.getElementById('btn-revoke-invitation')).toBeNull();
  });
  describe('D-66: module employee tabs the shell composes', () => {
    function renderWithFeed(feed) {
      return render(
        <ModuleEmployeeTabsProvider tabs={moduleEmployeeTabs} modules={feed.modules} actions={feed.actions}>
          <Provider store={store}>
            <MemoryRouter initialEntries={['/employees/emp-acc-1']}>
              <Routes>
                <Route path="/employees/:id" element={<EmployeePage />} />
              </Routes>
            </MemoryRouter>
          </Provider>
        </ModuleEmployeeTabsProvider>
      );
    }

    it('shows the Payroll Salary tab after the core tabs when the tenant holds Payroll and the user payroll.salary.read', async () => {
      employeeService.get.mockResolvedValueOnce(ACTIVE_EMPLOYEE);
      renderWithFeed({ modules: ['CORE', 'PAYROLL'], actions: ['payroll.salary.read'] });

      await waitFor(() => expect(screen.getByText('Overview')).toBeDefined());
      const labels = screen.getAllByRole('tab').map((t) => t.textContent);
      expect(labels).toContain('Salary');
      expect(labels.indexOf('Salary')).toBeGreaterThan(labels.indexOf('Documents'));
      expect(labels).not.toContain('FBP');
      expect(labels).not.toContain('Tax declaration');
    });

    it('hides the Salary tab when the tenant does not hold Payroll', async () => {
      employeeService.get.mockResolvedValueOnce(ACTIVE_EMPLOYEE);
      renderWithFeed({ modules: ['CORE'], actions: ['payroll.salary.read'] });

      await waitFor(() => expect(screen.getByText('Overview')).toBeDefined());
      expect(screen.queryByText('Salary')).toBeNull();
    });

    it('hides the Salary tab when the user lacks payroll.salary.read', async () => {
      employeeService.get.mockResolvedValueOnce(ACTIVE_EMPLOYEE);
      renderWithFeed({ modules: ['CORE', 'PAYROLL'], actions: [] });

      await waitFor(() => expect(screen.getByText('Overview')).toBeDefined());
      expect(screen.queryByText('Salary')).toBeNull();
    });
  });
});
