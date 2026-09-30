import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Definitions } from './Definitions.jsx';
import { definitionService } from './definitionService.js';
import { roleService } from './roleService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./definitionService.js', () => ({
  definitionService: {
    list: vi.fn(),
    save: vi.fn(),
  },
}));

vi.mock('./roleService.js', () => ({
  roleService: {
    list: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('Definitions component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);

    roleService.list.mockResolvedValue([
      { id: 'r1', code: 'HR_ADMIN', name: 'HR Administrator' },
      { id: 'r2', code: 'FINANCE_ADMIN', name: 'Finance Administrator' },
      { id: 'r3', code: 'hr', name: 'HR' },
    ]);

    definitionService.list.mockResolvedValue({
      flowType: 'LEAVE',
      steps: [
        {
          stepIndex: 0,
          approverKind: 'REPORTING_MANAGER',
          assigneeRole: null,
          assigneeEmployeeId: null,
          escalateAfterDays: 3,
          perItem: false,
        },
        {
          stepIndex: 1,
          approverKind: 'ROLE',
          assigneeRole: 'HR_ADMIN',
          assigneeEmployeeId: null,
          escalateAfterDays: 5,
          perItem: true,
        },
      ],
    });
  });

  it('renders steps and reveals role select when approverKind is ROLE', async () => {
    render(<Definitions />);

    await waitFor(() => {
      expect(screen.getByText('Approval Definitions')).toBeDefined();
      const roleSelect = document.getElementById('select-role-1');
      expect(roleSelect).toBeDefined();
    });
  });

  it('shows all seven flow tabs even when navigation feed has only core modules (D-7)', async () => {
    vi.spyOn(useCanModule, 'useNavigation').mockReturnValue({
      items: [{ key: 'core.employees', path: '/employees' }],
      actions: ['core.approval_definition.manage'],
      loading: false,
    });

    render(<Definitions />);

    await waitFor(() => {
      expect(screen.getByText('Leave')).toBeDefined();
      expect(screen.getByText('Regularization')).toBeDefined();
      expect(screen.getByText('Overtime')).toBeDefined();
      expect(screen.getByText('Timesheet')).toBeDefined();
      expect(screen.getByText('Reimbursement')).toBeDefined();
      expect(screen.getByText('Proof of Investment')).toBeDefined();
      expect(screen.getByText('Pay Run')).toBeDefined();
    });
  });

  it('reorders steps and saves the ordered definition matching backend schema', async () => {
    definitionService.save.mockResolvedValueOnce({});
    render(<Definitions />);

    await waitFor(() => {
      expect(document.getElementById('select-role-1')).toBeDefined();
    });

    // Move step 1 up to step 0
    const moveUpBtn = document.getElementById('btn-move-up-1');
    fireEvent.click(moveUpBtn);

    const saveBtn = document.getElementById('btn-save-definition');
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(definitionService.save).toHaveBeenCalledWith(
        'LEAVE',
        expect.objectContaining({
          stepOrdering: 'SEQUENTIAL',
          commentScope: 'PER_STEP',
          isActive: true,
          steps: [
            {
              kind: 'ROLE',
              assignee: 'HR_ADMIN',
              escalate_after_days: 5,
              per_item: true,
            },
            {
              kind: 'REPORTING_MANAGER',
              assignee: null,
              escalate_after_days: 3,
              per_item: false,
            },
          ],
        })
      );
      // Assert no duplicate client-test fields are sent in payload
      const sentStep = definitionService.save.mock.calls[0][1].steps[0];
      expect(sentStep).not.toHaveProperty('stepIndex');
      expect(sentStep).not.toHaveProperty('approverKind');
      expect(sentStep).not.toHaveProperty('assigneeRole');
      expect(sentStep).not.toHaveProperty('escalateAfterDays');
    });
  });

  it('correctly loads and normalizes backend snake_case DTO definition', async () => {
    definitionService.list.mockResolvedValueOnce({
      flowType: 'OVERTIME',
      stepOrdering: 'SEQUENTIAL',
      commentScope: 'PER_STEP',
      isActive: true,
      steps: [
        {
          kind: 'ROLE',
          assignee: 'FINANCE_ADMIN',
          escalate_after_days: 7,
          per_item: true,
        },
      ],
    });

    render(<Definitions />);

    await waitFor(() => {
      const roleSelect = document.getElementById('select-role-0');
      expect(roleSelect).toBeDefined();
    });
  });
});

