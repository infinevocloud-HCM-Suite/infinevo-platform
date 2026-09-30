import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { Form } from 'antd';
import { Delegations } from './Delegations.jsx';
import { delegationService } from './delegationService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./delegationService.js', () => ({
  delegationService: {
    list: vi.fn(),
    create: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('Delegations component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);

    delegationService.list.mockResolvedValue([
      {
        id: 'del-1',
        delegateEmployeeId: 'emp-colleague-1',
        startsOn: '2026-10-01',
        endsOn: '2026-10-05',
        flowTypes: ['LEAVE', 'OVERTIME'],
        active: true,
      },
    ]);
  });

  it('renders delegations table and deletes with confirmation', async () => {
    delegationService.remove.mockResolvedValueOnce({});
    render(<Delegations />);

    await waitFor(() => {
      expect(screen.getByText('emp-colleague-1')).toBeDefined();
      expect(screen.getByText('2026-10-01')).toBeDefined();
      expect(screen.getByText('LEAVE')).toBeDefined();
    });

    const deleteBtn = document.getElementById('btn-delete-delegation-del-1');
    fireEvent.click(deleteBtn);

    await waitFor(() => {
      const confirmOk = screen.getByText('Yes');
      fireEvent.click(confirmOk);
    });

    await waitFor(() => {
      expect(delegationService.remove).toHaveBeenCalledWith('del-1');
    });
  });

  it('renders delegations table with backend response shape (from, to, string flowTypes, isActive)', async () => {
    delegationService.list.mockResolvedValueOnce([
      {
        id: 'del-backend-1',
        delegateEmployeeId: 'emp-colleague-2',
        from: '2026-11-01',
        to: '2026-11-10',
        flowTypes: 'REGULARIZATION,REIMBURSEMENT',
        isActive: true,
      },
    ]);

    render(<Delegations />);

    await waitFor(() => {
      expect(screen.getByText('emp-colleague-2')).toBeDefined();
      expect(screen.getByText('2026-11-01')).toBeDefined();
      expect(screen.getByText('2026-11-10')).toBeDefined();
      expect(screen.getByText('REGULARIZATION')).toBeDefined();
      expect(screen.getByText('REIMBURSEMENT')).toBeDefined();
      expect(screen.getByText('ACTIVE')).toBeDefined();
    });
  });

  it('calls list with employeeId: me', async () => {
    render(<Delegations me="emp-me-123" />);
    await waitFor(() => {
      expect(delegationService.list).toHaveBeenCalledWith({ employeeId: 'emp-me-123' });
    });
  });

  it('renders NotEntitled when user lacks core.approval.delegate permission', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    render(<Delegations />);
    await waitFor(() => {
      expect(screen.getByText('Module Not Subscribed')).toBeDefined();
    });
  });

  it('create sends four fields matching backend DelegationCreateRequest', async () => {
    delegationService.create.mockResolvedValueOnce({ id: 'new-del-1' });

    let testForm;
    function TestWrapper() {
      const [form] = Form.useForm();
      testForm = form;
      return <Delegations form={form} me="emp-me-123" />;
    }

    render(<TestWrapper />);

    // Open create modal
    const newBtn = document.getElementById('btn-new-delegation');
    fireEvent.click(newBtn);

    await waitFor(() => {
      expect(screen.getByText('Create Approval Delegation')).toBeDefined();
    });

    testForm.setFieldsValue({
      delegateEmployeeId: 'emp-delegate-9',
      dates: [dayjs('2026-10-01'), dayjs('2026-10-05')],
      flowTypes: ['LEAVE', 'OVERTIME'],
    });

    // Click submit in modal
    const createBtn = screen.getByRole('button', { name: 'Create' });
    fireEvent.click(createBtn);

    await waitFor(() => {
      expect(delegationService.create).toHaveBeenCalledWith({
        delegateId: 'emp-delegate-9',
        from: '2026-10-01',
        to: '2026-10-05',
        flowTypes: 'LEAVE,OVERTIME',
      });
      // Assert duplicate fields from earlier client tests are not sent
      const payload = delegationService.create.mock.calls[0][0];
      expect(payload).not.toHaveProperty('delegateEmployeeId');
      expect(payload).not.toHaveProperty('startsOn');
      expect(payload).not.toHaveProperty('endsOn');
    });
  });
});

