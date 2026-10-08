import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import {
  EmployeeCreate,
  employeeSchema,
  toCreatePayload,
  roleOptions,
  toInvitationPayload,
} from './EmployeeCreate.jsx';
import employeeReducer from './employeeSlice.js';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';
import { roleService } from '../approvals/roleService.js';
import { employeeInvitationService } from '../invitation/employeeInvitationService.js';
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
    create: vi.fn(),
  },
}));

vi.mock('./orgMasterService.js', () => ({
  orgMasterService: {
    all: vi.fn(),
  },
}));

vi.mock('../approvals/roleService.js', () => ({
  roleService: {
    list: vi.fn(),
  },
}));

vi.mock('../invitation/employeeInvitationService.js', () => ({
  employeeInvitationService: {
    create: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

const ROLES = [
  { id: 'role-hr', code: 'hr', name: 'HR', system: true },
  { id: 'role-employee', code: 'employee', name: 'Employee', system: true },
  { id: 'role-manager', code: 'manager', name: 'Manager', system: true },
  { id: 'role-platform-admin', code: 'platform-admin', name: 'Platform Admin', system: true },
];

const MASTERS = {
  departments: { 'dep-1': 'Engineering' },
  designations: { 'des-1': 'Engineer' },
  workLocations: { 'loc-1': 'Pune' },
  raw: {
    departments: [{ id: 'dep-1', name: 'Engineering' }],
    designations: [{ id: 'des-1', name: 'Engineer' }],
    workLocations: [{ id: 'loc-1', name: 'Pune' }],
  },
};

function renderWithStore(ui) {
  const store = configureStore({
    reducer: { employee: employeeReducer },
  });
  return render(
    <Provider store={store}>
      <MemoryRouter>{ui}</MemoryRouter>
    </Provider>
  );
}

/** Opens an antd Select by its input id and picks the option with this label. */
async function pick(selectId, label) {
  fireEvent.mouseDown(document.getElementById(selectId));
  const option = await waitFor(() => {
    const found = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
      (el) => el.getAttribute('title') === label
    );
    if (!found) throw new Error(`option ${label} not rendered yet`);
    return found;
  });
  fireEvent.click(option);
}

function typeDate(input, value) {
  fireEvent.mouseDown(input);
  fireEvent.change(input, { target: { value } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
}

/** Fills every required field, as the submit test does. */
function fillRequired() {
  fireEvent.change(screen.getByPlaceholderText('e.g. EMP001'), { target: { value: 'EMP001' } });
  fireEvent.change(screen.getByPlaceholderText('First name'), { target: { value: 'Asha' } });
  fireEvent.change(screen.getByPlaceholderText('Last name'), { target: { value: 'Rao' } });
  fireEvent.change(screen.getByPlaceholderText('email@company.com'), {
    target: { value: 'asha@acme.test' },
  });
  typeDate(screen.getByPlaceholderText('Select date'), '2026-04-01');
}

async function tickAccess() {
  fireEvent.click(document.getElementById('check-giveAccess'));
  await waitFor(() => expect(document.getElementById('select-roles')).toBeTruthy());
}

const VALID = {
  employeeNumber: 'EMP001',
  firstName: 'Asha',
  middleName: '',
  lastName: 'Rao',
  gender: null,
  dateOfJoining: '2026-04-01',
  status: 'ACTIVE',
  workEmail: 'asha@acme.test',
  mobile: '',
  departmentId: 'dep-1',
  designationId: 'des-1',
  workLocationId: 'loc-1',
  portalEnabled: true,
  employmentType: null,
  probationEndDate: null,
  noticePeriodDays: null,
};

describe('EmployeeCreate component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    orgMasterService.all.mockResolvedValue(MASTERS);
    roleService.list.mockResolvedValue(ROLES);
    vi.spyOn(useCanModule, 'useCan').mockImplementation((code) => code === 'core.role.assign');
  });

  it('shows the Required group open and the More group collapsed', async () => {
    renderWithStore(<EmployeeCreate />);

    expect(screen.getByText('Required')).toBeDefined();
    for (const id of [
      'input-employeeNumber',
      'input-firstName',
      'input-lastName',
      'input-workEmail',
      'picker-dateOfJoining',
      'select-department',
      'select-designation',
      'select-workLocation',
    ]) {
      expect(document.getElementById(id)).toBeTruthy();
    }

    // More is collapsed: its fields are not rendered until it is opened.
    expect(screen.getByText('More')).toBeDefined();
    expect(document.getElementById('select-gender')).toBeNull();
    expect(document.getElementById('select-employmentType')).toBeNull();

    fireEvent.click(screen.getByText('More'));

    await waitFor(() => {
      for (const id of [
        'select-gender',
        'input-mobile',
        'select-employmentType',
        'picker-probationEndDate',
        'input-noticePeriodDays',
      ]) {
        expect(document.getElementById(id)).toBeTruthy();
      }
    });
  });

  it('blocks submit and names every missing required field', async () => {
    renderWithStore(<EmployeeCreate />);

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => {
      expect(screen.getByText('Employee number is required')).toBeDefined();
      expect(screen.getByText('First name is required')).toBeDefined();
      expect(screen.getByText('Last name is required')).toBeDefined();
      expect(screen.getByText('Work email is required')).toBeDefined();
      expect(screen.getByText('Date of joining is required')).toBeDefined();
    });
    // Department, designation and work location are optional: a new tenant may have none yet.
    expect(screen.queryByText('Department is required')).toBeNull();
    expect(screen.queryByText('Designation is required')).toBeNull();
    expect(screen.queryByText('Work location is required')).toBeNull();

    expect(employeeService.create).not.toHaveBeenCalled();
  });

  it('offers the four gender values and the six employment types in More', async () => {
    renderWithStore(<EmployeeCreate />);
    fireEvent.click(screen.getByText('More'));
    await waitFor(() => expect(document.getElementById('select-gender')).toBeTruthy());

    fireEvent.mouseDown(document.getElementById('select-gender'));
    await waitFor(() => {
      const titles = Array.from(document.querySelectorAll('.ant-select-item-option')).map((el) =>
        el.getAttribute('title')
      );
      expect(titles).toEqual(['Male', 'Female', 'Other', 'Prefer not to say']);
    });
  });

  it('submits the required fields plus the More fields and navigates to the new employee', async () => {
    employeeService.create.mockResolvedValueOnce({
      id: 'new-emp-101',
      employeeNumber: 'EMP001',
      firstName: 'Asha',
    });

    renderWithStore(<EmployeeCreate />);

    fireEvent.change(screen.getByPlaceholderText('e.g. EMP001'), { target: { value: 'EMP001' } });
    fireEvent.change(screen.getByPlaceholderText('First name'), { target: { value: 'Asha' } });
    fireEvent.change(screen.getByPlaceholderText('Last name'), { target: { value: 'Rao' } });
    fireEvent.change(screen.getByPlaceholderText('email@company.com'), {
      target: { value: 'asha@acme.test' },
    });
    typeDate(screen.getByPlaceholderText('Select date'), '2026-04-01');

    await waitFor(() => expect(document.getElementById('select-department')).toBeTruthy());
    await pick('select-department', 'Engineering');
    await pick('select-designation', 'Engineer');
    await pick('select-workLocation', 'Pune');

    fireEvent.click(screen.getByText('More'));
    await waitFor(() => expect(document.getElementById('select-employmentType')).toBeTruthy());
    await pick('select-employmentType', 'Contract');
    fireEvent.change(document.getElementById('input-noticePeriodDays'), { target: { value: '30' } });
    fireEvent.blur(document.getElementById('input-noticePeriodDays'));

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => {
      expect(employeeService.create).toHaveBeenCalledWith(
        expect.objectContaining({
          employeeNumber: 'EMP001',
          firstName: 'Asha',
          lastName: 'Rao',
          workEmail: 'asha@acme.test',
          dateOfJoining: '2026-04-01',
          departmentId: 'dep-1',
          designationId: 'des-1',
          workLocationId: 'loc-1',
          employmentType: 'CONTRACT',
          noticePeriodDays: 30,
          probationEndDate: null,
          gender: null,
        })
      );
      expect(mockNavigate).toHaveBeenCalledWith('/employees/new-emp-101');
    });
  });

  it('opens More when a field inside it fails validation', async () => {
    renderWithStore(<EmployeeCreate />);

    // Probation end set in More, More closed again, then a joining date after it.
    fireEvent.click(screen.getByText('More'));
    await waitFor(() => expect(screen.getByPlaceholderText('Probation end date')).toBeTruthy());
    typeDate(screen.getByPlaceholderText('Probation end date'), '2026-03-01');
    fireEvent.click(screen.getByText('More'));
    await waitFor(() =>
      expect(document.querySelector('.ant-collapse-item-active')).toBeNull()
    );
    typeDate(screen.getByPlaceholderText('Select date'), '2026-04-01');

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => {
      expect(document.querySelector('.ant-collapse-item-active')).toBeTruthy();
      expect(screen.getByText('Probation end cannot be before the date of joining')).toBeDefined();
    });
    expect(employeeService.create).not.toHaveBeenCalled();
  });

  it('leaves More closed when only Required fields fail', async () => {
    renderWithStore(<EmployeeCreate />);

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => expect(screen.getByText('Employee number is required')).toBeDefined());
    expect(document.querySelector('.ant-collapse-item-active')).toBeNull();
  });

  it('hides the roles select until Give portal access is ticked, then shows it with employee locked', async () => {
    renderWithStore(<EmployeeCreate />);
    await waitFor(() => expect(roleService.list).toHaveBeenCalled());

    expect(document.getElementById('select-roles')).toBeNull();
    expect(document.getElementById('text-access-note')).toBeNull();

    await tickAccess();

    expect(screen.getByText('An email goes to the work email.')).toBeDefined();
    const selected = await waitFor(() => {
      const items = Array.from(document.querySelectorAll('.ant-select-selection-item'));
      const found = items.find((el) => el.textContent === 'Employee');
      if (!found) throw new Error('Employee not selected yet');
      return found;
    });
    // Locked: a disabled option renders no remove icon on its tag.
    expect(selected.querySelector('.ant-select-selection-item-remove')).toBeNull();

    fireEvent.mouseDown(document.getElementById('select-roles'));
    await waitFor(() => {
      const titles = Array.from(document.querySelectorAll('.ant-select-item-option')).map((el) =>
        el.getAttribute('title')
      );
      expect(titles).toEqual(['Employee', 'HR', 'Manager']);
    });
    expect(
      Array.from(document.querySelectorAll('.ant-select-item-option')).some(
        (el) => el.getAttribute('title') === 'Platform Admin'
      )
    ).toBe(false);
  });

  it('creates the employee then the invitation with the chosen extra roles', async () => {
    employeeService.create.mockResolvedValueOnce({ id: 'new-emp-101', employeeNumber: 'EMP001' });
    employeeInvitationService.create.mockResolvedValueOnce({ id: 'inv-1' });

    renderWithStore(<EmployeeCreate />);
    await waitFor(() => expect(roleService.list).toHaveBeenCalled());
    fillRequired();
    await tickAccess();
    await pick('select-roles', 'HR');

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => {
      expect(employeeService.create).toHaveBeenCalledTimes(1);
      expect(employeeInvitationService.create).toHaveBeenCalledWith({
        employeeId: 'new-emp-101',
        roleIds: ['role-hr'],
      });
      expect(mockNavigate).toHaveBeenCalledWith('/employees/new-emp-101');
    });
    // The create request does not carry the access fields.
    const createBody = employeeService.create.mock.calls[0][0];
    expect(createBody).not.toHaveProperty('giveAccess');
    expect(createBody).not.toHaveProperty('roleIds');
  });

  it('does not invite when Give portal access is left unticked', async () => {
    employeeService.create.mockResolvedValueOnce({ id: 'new-emp-102', employeeNumber: 'EMP001' });

    renderWithStore(<EmployeeCreate />);
    fillRequired();
    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/employees/new-emp-102'));
    expect(employeeInvitationService.create).not.toHaveBeenCalled();
  });

  it('shows the partial-failure message when the invitation fails after the employee is saved', async () => {
    employeeService.create.mockResolvedValueOnce({ id: 'new-emp-101', employeeNumber: 'EMP001' });
    employeeInvitationService.create.mockRejectedValueOnce(new Error('no work email'));

    renderWithStore(<EmployeeCreate />);
    await waitFor(() => expect(roleService.list).toHaveBeenCalled());
    fillRequired();
    await tickAccess();

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() =>
      expect(
        screen.getByText('Saved. Invitation failed: no work email — invite from the employee page')
      ).toBeDefined()
    );
    expect(document.getElementById('alert-invitation-failed')).toBeTruthy();
    expect(mockNavigate).not.toHaveBeenCalledWith('/employees/new-emp-101');
    expect(screen.getByRole('button', { name: /create employee/i }).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: /open employee page/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/employees/new-emp-101');
  });

  it('without core.role.assign offers portal access with the employee role only and sends no roles', async () => {
    useCanModule.useCan.mockImplementation(() => false);
    employeeService.create.mockResolvedValueOnce({ id: 'new-emp-103', employeeNumber: 'EMP001' });
    employeeInvitationService.create.mockResolvedValueOnce({ id: 'inv-3' });

    renderWithStore(<EmployeeCreate />);
    fillRequired();
    fireEvent.click(document.getElementById('check-giveAccess'));

    await waitFor(() => expect(document.getElementById('text-locked-role')).toBeTruthy());
    expect(document.getElementById('text-locked-role').textContent).toBe('Employee');
    expect(document.getElementById('select-roles')).toBeNull();
    expect(screen.getByText('An email goes to the work email.')).toBeDefined();
    expect(roleService.list).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => {
      expect(employeeInvitationService.create).toHaveBeenCalledWith({
        employeeId: 'new-emp-103',
        roleIds: [],
      });
      expect(mockNavigate).toHaveBeenCalledWith('/employees/new-emp-103');
    });
  });

  it('with core.role.assign shows the roles picker, not the locked employee line', async () => {
    renderWithStore(<EmployeeCreate />);
    await waitFor(() => expect(roleService.list).toHaveBeenCalled());
    await tickAccess();

    expect(document.getElementById('text-locked-role')).toBeNull();
  });

  it('requires the work email when access is ticked', async () => {
    renderWithStore(<EmployeeCreate />);
    await tickAccess();

    fireEvent.click(screen.getByRole('button', { name: /create employee/i }));

    await waitFor(() => expect(screen.getByText('Work email is required')).toBeDefined());
    expect(employeeService.create).not.toHaveBeenCalled();
    expect(employeeInvitationService.create).not.toHaveBeenCalled();
  });
});

describe('roleOptions', () => {
  it('drops platform-admin, puts employee first and locks it', () => {
    expect(roleOptions(ROLES)).toEqual([
      { value: 'role-employee', label: 'Employee', disabled: true },
      { value: 'role-hr', label: 'HR', disabled: false },
      { value: 'role-manager', label: 'Manager', disabled: false },
    ]);
  });

  it('falls back to the code when a role has no name', () => {
    expect(roleOptions([{ id: 'r-x', code: 'payroll-admin' }])).toEqual([
      { value: 'r-x', label: 'payroll-admin', disabled: false },
    ]);
  });
});

describe('toInvitationPayload', () => {
  it('sends the extra roles only, with the employee role stripped', () => {
    expect(
      toInvitationPayload('emp-1', { roleIds: ['role-employee', 'role-hr'] }, ROLES)
    ).toEqual({ employeeId: 'emp-1', roleIds: ['role-hr'] });
  });

  it('sends no roles at all without core.role.assign', () => {
    expect(toInvitationPayload('emp-1', { roleIds: ['role-hr'] }, ROLES, false)).toEqual({
      employeeId: 'emp-1',
      roleIds: [],
    });
  });

  it('sends an empty list when no extra role was chosen', () => {
    expect(toInvitationPayload('emp-1', { roleIds: [] }, ROLES)).toEqual({
      employeeId: 'emp-1',
      roleIds: [],
    });
  });
});

describe('employeeSchema (D-40 rules)', () => {
  it('accepts a complete valid form', async () => {
    await expect(employeeSchema.validate(VALID)).resolves.toBeTruthy();
  });

  it('refuses a probation end before the date of joining, allows the same day', async () => {
    await expect(
      employeeSchema.validateAt('probationEndDate', { ...VALID, probationEndDate: '2026-03-31' })
    ).rejects.toThrow('Probation end cannot be before the date of joining');
    await expect(
      employeeSchema.validateAt('probationEndDate', { ...VALID, probationEndDate: '2026-04-01' })
    ).resolves.toBe('2026-04-01');
  });

  it('bounds the notice period to 0..365 whole days', async () => {
    await expect(employeeSchema.validateAt('noticePeriodDays', { ...VALID, noticePeriodDays: -1 })).rejects.toThrow();
    await expect(employeeSchema.validateAt('noticePeriodDays', { ...VALID, noticePeriodDays: 366 })).rejects.toThrow();
    await expect(employeeSchema.validateAt('noticePeriodDays', { ...VALID, noticePeriodDays: 1.5 })).rejects.toThrow();
    await expect(employeeSchema.validateAt('noticePeriodDays', { ...VALID, noticePeriodDays: 0 })).resolves.toBe(0);
    await expect(employeeSchema.validateAt('noticePeriodDays', { ...VALID, noticePeriodDays: 365 })).resolves.toBe(365);
  });
});

describe('toCreatePayload', () => {
  it('trims text, turns blanks into null and carries the three employment terms', () => {
    const payload = toCreatePayload({
      ...VALID,
      employeeNumber: ' EMP001 ',
      middleName: '   ',
      mobile: ' 98765 ',
      gender: 'UNDISCLOSED',
      employmentType: 'PERMANENT',
      probationEndDate: '2026-10-01',
      noticePeriodDays: 60,
    });

    expect(payload).toEqual({
      employeeNumber: 'EMP001',
      firstName: 'Asha',
      middleName: null,
      lastName: 'Rao',
      gender: 'UNDISCLOSED',
      dateOfJoining: '2026-04-01',
      status: 'ACTIVE',
      workEmail: 'asha@acme.test',
      mobile: '98765',
      departmentId: 'dep-1',
      designationId: 'des-1',
      workLocationId: 'loc-1',
      portalEnabled: true,
      employmentType: 'PERMANENT',
      probationEndDate: '2026-10-01',
      noticePeriodDays: 60,
    });
  });

  it('sends null for the terms when More was left empty', () => {
    const payload = toCreatePayload(VALID);
    expect(payload.employmentType).toBeNull();
    expect(payload.probationEndDate).toBeNull();
    expect(payload.noticePeriodDays).toBeNull();
  });
});
