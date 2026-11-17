import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { Modal } from 'antd';
import { EmployeeList } from './EmployeeList.jsx';
import employeeReducer from './employeeSlice.js';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';
import { employeeImportService } from './employeeImportService.js';
import { successMsg } from '@shared/ui/msgHelper.js';
import * as useCanModule from '@shell/screens';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('./employeeService.js', () => ({
  employeeService: {
    list: vi.fn(),
    limit: vi.fn(),
    export: vi.fn(),
    downloadFile: vi.fn(),
  },
}));

vi.mock('./employeeImportService.js', () => ({
  employeeImportService: {
    inviteAllCount: vi.fn(),
    inviteAll: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

vi.mock('./orgMasterService.js', () => ({
  orgMasterService: {
    all: vi.fn(),
  },
}));

function renderWithStore(ui, { initialState, route = '/employees' } = {}) {
  const store = configureStore({
    reducer: { employee: employeeReducer },
    preloadedState: initialState,
  });
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </Provider>
  );
}

describe('EmployeeList component', () => {
  afterEach(() => {
    Modal.destroyAll();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    orgMasterService.all.mockResolvedValue({
      departments: { 'd-1': 'Engineering' },
      designations: {},
      workLocations: {},
      raw: { departments: [], designations: [], workLocations: [] },
    });
    employeeService.list.mockResolvedValue({
      content: [
        {
          id: 'emp-1',
          employeeNumber: 'E-101',
          firstName: 'Asha',
          lastName: 'Rao',
          status: 'ACTIVE',
          workEmail: 'asha@work.com',
          departmentId: 'd-1',
          dateOfJoining: '2026-04-01',
        },
      ],
      totalElements: 1,
    });
    // Default: the limit call fails, so the badge stays on the list total (W-83.2 behaviour).
    employeeService.limit.mockRejectedValue({ code: 'NETWORK', message: 'offline', status: 0 });
  });

  it('renders table rows, sends search q, and handles row navigation', async () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => {
      if (action === 'core.employee.create') return true;
      if (action === 'core.employee.delete') return true;
      return true;
    });

    renderWithStore(<EmployeeList />);

    await waitFor(() => {
      expect(screen.getByText('E-101')).toBeDefined();
      expect(screen.getByText('Asha Rao')).toBeDefined();
    });
    // The joining date and status read as words, never the ISO date or the code (D-78).
    expect(screen.getByText('1 Apr 2026')).toBeDefined();
    expect(screen.queryByText('2026-04-01')).toBeNull();
    expect(screen.getByText('Active')).toBeDefined();
    expect(screen.queryByText('ACTIVE')).toBeNull();
    // W-83.5: the list avatar is the small (24 px) size.
    expect(document.querySelector('.ant-table-row .ant-avatar-sm')).not.toBeNull();

    // Search query
    const searchInput = screen.getByPlaceholderText('Search employees...');
    fireEvent.change(searchInput, { target: { value: 'Asha' } });
    fireEvent.keyDown(searchInput, { key: 'Enter', code: 'Enter' });

    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledWith(
        expect.objectContaining({ q: 'Asha' })
      );
    });

    // Row click navigates to /employees/:id
    const row = screen.getByTestId('employee-row-emp-1');
    fireEvent.click(row);
    // D-139: the list hands its own URL, with the opened row, to the employee page for its back arrow.
    expect(mockNavigate).toHaveBeenCalledWith('/employees/emp-1', {
      state: { from: '/employees?q=Asha&focus=emp-1' },
    });
  });

  it('D-139: the list view comes from the URL, the focused row is highlighted, and a filter change resets to page 1', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);

    renderWithStore(<EmployeeList />, { route: '/employees?page=3&size=10&status=ACTIVE&q=rao&focus=emp-1' });

    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledWith(
        expect.objectContaining({ page: 2, size: 10, status: 'ACTIVE', q: 'rao' })
      );
    });
    await waitFor(() => {
      expect(screen.getByTestId('employee-row-emp-1').className).toContain('employee-row-focused');
    });
    expect(screen.getByPlaceholderText('Search employees...').value).toBe('rao');

    // Changing the status filter goes back to page 1 and drops the focus.
    employeeService.list.mockClear();
    const select = document.querySelector('#select-status-filter');
    fireEvent.mouseDown(select.querySelector('.ant-select-selector') || select);
    fireEvent.click(await screen.findByText('Terminated'));
    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledWith(
        expect.objectContaining({ page: 0, size: 10, status: 'TERMINATED', q: 'rao' })
      );
    });
    await waitFor(() => {
      expect(screen.getByTestId('employee-row-emp-1').className).not.toContain('employee-row-focused');
    });
  });

  it('D-139: a malformed URL falls back to the first page and the defaults', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    renderWithStore(<EmployeeList />, { route: '/employees?page=abc&size=-5&status=NOPE' });
    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledWith(
        expect.objectContaining({ page: 0, size: 25, status: undefined, q: undefined })
      );
    });
  });

  it('does not render "Include deleted" switch when user lacks core.employee.delete', async () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => {
      if (action === 'core.employee.delete') return false;
      return true;
    });

    renderWithStore(<EmployeeList />);

    await waitFor(() => {
      expect(screen.getByText('E-101')).toBeDefined();
    });

    expect(screen.queryByText('Include deleted:')).toBeNull();
  });

  it('sends status filter when status select is changed', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);

    const { container } = renderWithStore(<EmployeeList />);

    await waitFor(() => {
      expect(screen.getByText('E-101')).toBeDefined();
    });

    employeeService.list.mockClear();

    const selector = container.querySelector('#select-status-filter .ant-select-selector') || container.querySelector('#select-status-filter');
    fireEvent.mouseDown(selector);

    await waitFor(() => {
      const suspendedOption = document.querySelector('.ant-select-item-option[title="Suspended"]') || screen.getByText('Suspended');
      expect(suspendedOption).toBeTruthy();
      fireEvent.click(suspendedOption);
    });

    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'SUSPENDED' })
      );
    });
  });

  it('shows the error and a retry instead of an empty table when the load fails', async () => {
    employeeService.list.mockReset();
    employeeService.list.mockRejectedValueOnce({ code: 'FORBIDDEN', message: 'You do not have access', status: 403 });
    employeeService.list.mockResolvedValueOnce({ content: [], totalElements: 0 });

    renderWithStore(<EmployeeList />);

    expect(await screen.findByText('Employees could not be loaded')).toBeDefined();
    expect(screen.getByText('You do not have access')).toBeDefined();

    fireEvent.click(document.getElementById('btn-retry-employees'));

    await waitFor(() => {
      expect(employeeService.list).toHaveBeenCalledTimes(2);
      expect(screen.queryByText('Employees could not be loaded')).toBeNull();
    });
  });

  it('W-73.7: Invite all confirms with the count, queues one job and opens the import page', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeImportService.inviteAllCount.mockResolvedValue(3);
    employeeImportService.inviteAll.mockResolvedValue('job-1');

    renderWithStore(<EmployeeList />);
    await waitFor(() => expect(document.getElementById('btn-invite-all')).not.toBeNull());
    fireEvent.click(document.getElementById('btn-invite-all'));

    expect(await screen.findByText(/Send a portal invitation to 3 employees/)).toBeDefined();
    fireEvent.click(screen.getByText('Invite 3'));

    await waitFor(() => {
      expect(employeeImportService.inviteAll).toHaveBeenCalledTimes(1);
      expect(mockNavigate).toHaveBeenCalledWith('/employees/import');
    });
  });

  it('W-73.7: Invite all with nobody to invite says so and queues nothing', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeImportService.inviteAllCount.mockResolvedValue(0);

    renderWithStore(<EmployeeList />);
    await waitFor(() => expect(document.getElementById('btn-invite-all')).not.toBeNull());
    fireEvent.click(document.getElementById('btn-invite-all'));

    await waitFor(() => expect(successMsg).toHaveBeenCalledWith('Nobody to invite', expect.any(String)));
    expect(employeeImportService.inviteAll).not.toHaveBeenCalled();
  });

  it('W-73.7: Import opens the import page; neither button shows without core.employee.create', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    const { unmount } = renderWithStore(<EmployeeList />);
    await waitFor(() => expect(document.getElementById('btn-import-employees')).not.toBeNull());
    fireEvent.click(document.getElementById('btn-import-employees'));
    expect(mockNavigate).toHaveBeenCalledWith('/employees/import');
    unmount();

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    renderWithStore(<EmployeeList />);
    await waitFor(() => expect(employeeService.list).toHaveBeenCalled());
    expect(document.getElementById('btn-invite-all')).toBeNull();
    expect(document.getElementById('btn-import-employees')).toBeNull();
  });

  it('Export button calls employeeService.export and downloadFile when clicked', async () => {
    vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => {
      if (action === 'core.employee.export') return true;
      return false;
    });
    employeeService.export.mockResolvedValue({
      url: '/api/v1/documents/download?t=signed-token',
      fileName: 'employees-2026.xlsx',
      rowCount: 53,
    });

    renderWithStore(<EmployeeList />);
    await waitFor(() => expect(document.getElementById('btn-export-employees')).not.toBeNull());
    fireEvent.click(document.getElementById('btn-export-employees'));

    await waitFor(() => {
      expect(employeeService.export).toHaveBeenCalled();
      expect(employeeService.downloadFile).toHaveBeenCalledWith(
        '/api/v1/documents/download?t=signed-token',
        'employees-2026.xlsx'
      );
      expect(successMsg).toHaveBeenCalledWith('Export downloaded', expect.stringContaining('53'));
    });
  });

  it('filtered to Terminated, the badge reads "N employees" and Active appears nowhere in it (W-83.2)', async () => {
    employeeService.list.mockResolvedValue({
      content: [
        {
          id: 'emp-2',
          employeeNumber: 'E-102',
          firstName: 'Ramesh',
          lastName: 'Kumar',
          status: 'TERMINATED',
          workEmail: 'ramesh@work.com',
          departmentId: 'd-1',
          dateOfJoining: '2025-01-01',
        },
      ],
      totalElements: 1,
    });

    renderWithStore(<EmployeeList />);
    await waitFor(() => {
      const badge = screen.getByText('1 employees');
      expect(badge).toBeTruthy();
      expect(screen.queryByText(/Active Staff/i)).toBeNull();
    });
  });

  it('W-84.2: with a limit the badge reads "N of M employees" and no limit warning shows below it', async () => {
    employeeService.limit.mockResolvedValue({ limit: 50, used: 12 });

    renderWithStore(<EmployeeList />);

    expect(await screen.findByText('12 of 50 employees')).toBeDefined();
    expect(employeeService.limit).toHaveBeenCalled();
    expect(document.getElementById('alert-employee-limit')).toBeNull();
  });

  it('W-84.2: without a limit the badge reads "N employees" from the server count', async () => {
    employeeService.limit.mockResolvedValue({ limit: null, used: 12 });

    renderWithStore(<EmployeeList />);

    expect(await screen.findByText('12 employees')).toBeDefined();
    expect(screen.queryByText(/^\d+ of \d+ employees$/)).toBeNull();
    expect(document.getElementById('alert-employee-limit')).toBeNull();
  });

  it('W-84.2: at the limit the badge reads "50 of 50 employees" and the warning Alert shows', async () => {
    employeeService.limit.mockResolvedValue({ limit: 50, used: 50 });

    renderWithStore(<EmployeeList />);

    expect(await screen.findByText('50 of 50 employees')).toBeDefined();
    const alert = document.getElementById('alert-employee-limit');
    expect(alert).not.toBeNull();
    expect(alert.textContent).toContain(
      'Your plan allows 50 employees and 50 are in use. Contact Infinevo to raise the limit.'
    );
  });

  it('W-84.2: a failed limit call leaves the badge on the list total', async () => {
    employeeService.list.mockResolvedValue({ content: [], totalElements: 7 });
    employeeService.limit.mockRejectedValue({ code: 'FORBIDDEN', message: 'no', status: 403 });

    renderWithStore(<EmployeeList />);

    await waitFor(() => expect(employeeService.limit).toHaveBeenCalled());
    expect(await screen.findByText('7 employees')).toBeDefined();
    expect(document.getElementById('alert-employee-limit')).toBeNull();
  });

  it('W-84.2: the limit is fetched again after every successful list load', async () => {
    employeeService.limit.mockResolvedValue({ limit: 50, used: 12 });

    renderWithStore(<EmployeeList />);
    expect(await screen.findByText('12 of 50 employees')).toBeDefined();
    const callsAfterMount = employeeService.limit.mock.calls.length;

    employeeService.limit.mockResolvedValue({ limit: 50, used: 13 });
    const searchInput = screen.getByPlaceholderText('Search employees...');
    fireEvent.change(searchInput, { target: { value: 'Asha' } });
    fireEvent.keyDown(searchInput, { key: 'Enter', code: 'Enter' });

    expect(await screen.findByText('13 of 50 employees')).toBeDefined();
    expect(employeeService.limit.mock.calls.length).toBeGreaterThan(callsAfterMount);
  });
  it('D-139: typing in the search box suggests matching employees and picking one opens that employee', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    renderWithStore(<EmployeeList />, { route: '/employees?page=2&status=ACTIVE' });
    await screen.findByText('E-101');
    const callsAfterMount = employeeService.list.mock.calls.length;

    const searchInput = screen.getByPlaceholderText('Search employees...');
    // The e2e suite fills the box by this id; the suggestion wrapper must not swallow it.
    expect(document.getElementById('input-search-employees')).toBe(searchInput);
    fireEvent.change(searchInput, { target: { value: 'As' } });

    // Suggestions come from the same list call, limited to a few rows, with the current filters.
    await waitFor(() => {
      expect(employeeService.list.mock.calls.length).toBeGreaterThan(callsAfterMount);
    });
    expect(employeeService.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'As', size: 8, page: 0, status: 'ACTIVE' })
    );
    const options = await screen.findAllByText('E-101 · Asha Rao');
    // antd renders the option in an a11y list and in the visible dropdown; click the visible one.
    fireEvent.click(options[options.length - 1]);

    expect(mockNavigate).toHaveBeenCalledWith(
      '/employees/emp-1',
      { state: { from: '/employees?page=2&status=ACTIVE&focus=emp-1' } }
    );
  });

  it('D-139: one letter brings no suggestions', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    renderWithStore(<EmployeeList />);
    await screen.findByText('E-101');
    const callsAfterMount = employeeService.list.mock.calls.length;
    fireEvent.change(screen.getByPlaceholderText('Search employees...'), { target: { value: 'A' } });
    await new Promise((r) => setTimeout(r, 400));
    expect(employeeService.list.mock.calls.length).toBe(callsAfterMount);
    expect(screen.queryByText('E-101 · Asha Rao')).toBeNull();
  });
});
