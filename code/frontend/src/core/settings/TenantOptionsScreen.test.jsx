import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { TenantOptionsScreen } from './TenantOptionsScreen.jsx';
import { optionService } from './optionService.js';
import * as shellScreens from '@shell/screens';

vi.mock('./optionService.js', () => ({
  optionService: {
    list: vi.fn(),
    update: vi.fn(),
    reset: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(undefined),
  errorMsg: vi.fn(),
}));

function can(codes) {
  return vi.spyOn(shellScreens, 'useCan').mockImplementation((code) => codes.includes(code));
}

describe('TenantOptionsScreen (W-104 §5, §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it('shows empty state when no options are available', async () => {
    can(['core.tenant_option.read', 'core.tenant_option.manage']);
    optionService.list.mockResolvedValue([]);

    render(<TenantOptionsScreen />);

    await waitFor(() => {
      expect(screen.getByText('No options are available yet.')).toBeDefined();
    });
  });

  it('shows load error when fetching options fails', async () => {
    can(['core.tenant_option.read', 'core.tenant_option.manage']);
    optionService.list.mockRejectedValue(new Error('Network error loading options'));

    render(<TenantOptionsScreen />);

    await waitFor(() => {
      expect(screen.getByText('Network error loading options')).toBeDefined();
    });
  });

  it('renders category tabs including Portal and Attendance', async () => {
    can(['core.tenant_option.read', 'core.tenant_option.manage']);
    optionService.list.mockResolvedValue([
      {
        key: 'core.employee.self_edit',
        category: 'PORTAL',
        valueType: 'BOOLEAN',
        label: 'Allow employee self-edit',
        description: 'When off, employees cannot edit their profile.',
        value: 'true',
        defaultValue: 'true',
        overridden: false,
      },
      {
        key: 'hrms.attendance.clock_location_required',
        category: 'ATTENDANCE',
        valueType: 'BOOLEAN',
        label: 'Require location for clock in/out',
        description: 'Refuses clock punch without coords.',
        value: 'false',
        defaultValue: 'false',
        overridden: false,
      },
    ]);

    render(<TenantOptionsScreen />);

    await waitFor(() => {
      expect(screen.getByText('Portal')).toBeDefined();
      expect(screen.getByText('Attendance')).toBeDefined();
    });

    // Portal tab
    fireEvent.click(screen.getByText('Portal'));
    await waitFor(() => {
      expect(screen.getByText('Allow employee self-edit')).toBeDefined();
    });

    // Attendance tab
    fireEvent.click(screen.getByText('Attendance'));
    await waitFor(() => {
      expect(screen.getByText('Require location for clock in/out')).toBeDefined();
    });
  });

  it('allows tenant-admin to update a boolean switch and reset an overridden option', async () => {
    can(['core.tenant_option.read', 'core.tenant_option.manage']);
    const mockOptions = [
      {
        key: 'core.employee.self_edit',
        category: 'PORTAL',
        valueType: 'BOOLEAN',
        label: 'Allow employee self-edit',
        description: 'When off, employees cannot edit their profile.',
        value: 'true',
        defaultValue: 'true',
        overridden: true,
      },
    ];
    optionService.list.mockResolvedValue(mockOptions);
    optionService.update.mockResolvedValue({
      ...mockOptions[0],
      value: 'false',
      overridden: true,
    });
    optionService.reset.mockResolvedValue({
      ...mockOptions[0],
      value: 'true',
      overridden: false,
    });

    render(<TenantOptionsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('switch-core.employee.self_edit')).toBeDefined();
    });

    // Toggle switch
    const switchEl = screen.getByTestId('switch-core.employee.self_edit');
    fireEvent.click(switchEl);

    await waitFor(() => {
      expect(optionService.update).toHaveBeenCalledWith('core.employee.self_edit', 'false');
    });

    // Reset option
    const resetBtn = screen.getByTestId('reset-core.employee.self_edit');
    fireEvent.click(resetBtn);

    await waitFor(() => {
      expect(optionService.reset).toHaveBeenCalledWith('core.employee.self_edit');
    });
  });

  it('allows tenant-admin to update an integer option with Save button', async () => {
    can(['core.tenant_option.read', 'core.tenant_option.manage']);
    const mockOptions = [
      {
        key: 'core.general.session_timeout',
        category: 'GENERAL',
        valueType: 'INTEGER',
        label: 'Session timeout',
        description: 'Minutes before idle timeout.',
        value: '30',
        defaultValue: '30',
        overridden: false,
      },
    ];
    optionService.list.mockResolvedValue(mockOptions);
    optionService.update.mockResolvedValue({
      ...mockOptions[0],
      value: '60',
      overridden: true,
    });

    render(<TenantOptionsScreen />);

    await waitFor(() => {
      expect(screen.getByRole('spinbutton')).toBeDefined();
    });

    const input = screen.getByRole('spinbutton');
    fireEvent.change(input, { target: { value: '60' } });

    await waitFor(() => {
      expect(screen.getByTestId('save-core.general.session_timeout')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-core.general.session_timeout'));

    await waitFor(() => {
      expect(optionService.update).toHaveBeenCalledWith('core.general.session_timeout', '60');
    });
  });

  it('hides write controls when caller lacks core.tenant_option.manage', async () => {
    can(['core.tenant_option.read']); // Read-only caller (e.g. hr or payroll-officer)
    optionService.list.mockResolvedValue([
      {
        key: 'core.employee.self_edit',
        category: 'PORTAL',
        valueType: 'BOOLEAN',
        label: 'Allow employee self-edit',
        description: 'When off, employees cannot edit their profile.',
        value: 'true',
        defaultValue: 'true',
        overridden: false,
      },
    ]);

    render(<TenantOptionsScreen />);

    await waitFor(() => {
      expect(screen.getByText('Allow employee self-edit')).toBeDefined();
    });

    // Write controls should not be rendered
    expect(screen.queryByTestId('switch-core.employee.self_edit')).toBeNull();
    expect(screen.queryByTestId('reset-core.employee.self_edit')).toBeNull();
    expect(screen.getByTestId('readonly-core.employee.self_edit')).toBeDefined();
    expect(screen.getByTestId('readonly-core.employee.self_edit').textContent).toBe('true');
  });
});
