import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { Inbox } from './Inbox.jsx';
import { approvalService } from './approvalService.js';
import approvalReducer from './approvalSlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./approvalService.js', () => ({
  approvalService: {
    pending: vi.fn(),
    decide: vi.fn(),
  },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => mockNavigate,
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('Inbox component', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        approvals: approvalReducer,
      },
    });

    approvalService.pending.mockResolvedValue({
      content: [
        {
          id: 'step-101',
          instanceId: 'inst-1',
          stepIndex: 0,
          totalSteps: 2,
          itemRef: null,
          approverKind: 'REPORTING_MANAGER',
          assigneeEmployeeId: 'emp-mgr-1',
          delegatedFromEmployeeId: null,
          escalatedFromEmployeeId: null,
          reassignedFromEmployeeId: null,
          reassignReason: null,
          decision: null,
          comment: null,
          approvedAmount: null,
          decidedAt: null,
          createdAt: '2026-09-29T10:00:00Z',
          flowType: 'LEAVE',
          subjectEmployeeId: 'emp-1',
          subjectEmployeeName: 'Alice Smith',
          itemId: 'item-leave-1',
          summary: 'Annual Leave (3 days)',
        },
        {
          id: 'step-102',
          instanceId: 'inst-2',
          stepIndex: 1,
          totalSteps: 3,
          itemRef: null,
          approverKind: 'ROLE',
          assigneeEmployeeId: 'emp-hr-1',
          delegatedFromEmployeeId: null,
          escalatedFromEmployeeId: null,
          reassignedFromEmployeeId: null,
          reassignReason: null,
          decision: null,
          comment: null,
          approvedAmount: null,
          decidedAt: null,
          createdAt: '2026-09-29T11:00:00Z',
          flowType: 'REIMBURSEMENT',
          subjectEmployeeId: 'emp-2',
          subjectEmployeeName: null,
          itemId: 'item-reimb-2',
          summary: 'Travel Expense',
        },
      ],
      totalElements: 2,
    });

    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    vi.spyOn(useCanModule, 'useHasModule').mockReturnValue(true);
  });

  it('"View Item" opens the subject with the inbox as its origin, so the item can link back (W-48.5 §5)', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter>
          <Inbox />
        </MemoryRouter>
      </Provider>
    );
    await waitFor(() => expect(document.getElementById('btn-view-item-step-101')).toBeTruthy(), { timeout: 5000 });
    fireEvent.click(document.getElementById('btn-view-item-step-101'));
    expect(mockNavigate).toHaveBeenCalledWith('/leave/requests/item-leave-1', { state: { from: '/approvals' } });
  });

  it('renders rows across two flow types, names the subject from the response, displays steps and sets pendingCount', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter>
          <Inbox />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(
      () => {
        expect(screen.getByText('Annual Leave (3 days)')).toBeDefined();
        expect(screen.getByText('Travel Expense')).toBeDefined();
        expect(screen.getByText('Employee: Alice Smith')).toBeDefined();
        // No name in the response: the id is shown, and no employee endpoint is called for it.
        expect(screen.getByText('Employee ID: emp-2')).toBeDefined();
        expect(screen.getByText('Step 1 of 2')).toBeDefined();
        expect(screen.getByText('Step 2 of 3')).toBeDefined();
      },
      { timeout: 5000 }
    );

    expect(store.getState().approvals.pendingCount).toBe(2);
  });

  it('shows every assigned row to a Payroll-only tenant - the modules narrow the filter, never the rows', async () => {
    vi.spyOn(useCanModule, 'useHasModule').mockImplementation((module) => module === 'PAYROLL');

    render(
      <Provider store={store}>
        <MemoryRouter>
          <Inbox />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Annual Leave (3 days)')).toBeDefined();
      expect(screen.getByText('Travel Expense')).toBeDefined();
    });
    expect(store.getState().approvals.pendingCount).toBe(2);
  });

  it('approves a request and decrements pendingCount', async () => {
    approvalService.pending
      .mockResolvedValueOnce({
        content: [
          {
            id: 'step-101',
            instanceId: 'inst-1',
            stepIndex: 0,
            itemRef: null,
            approverKind: 'REPORTING_MANAGER',
            assigneeEmployeeId: 'emp-mgr-1',
            delegatedFromEmployeeId: null,
            escalatedFromEmployeeId: null,
            reassignedFromEmployeeId: null,
            reassignReason: null,
            decision: null,
            comment: null,
            approvedAmount: null,
            decidedAt: null,
            createdAt: '2026-09-29T10:00:00Z',
            flowType: 'LEAVE',
            subjectEmployeeId: 'emp-1',
            itemId: 'item-leave-1',
            summary: 'Annual Leave (3 days)',
          },
        ],
        totalElements: 2,
      })
      .mockResolvedValueOnce({
        content: [],
        totalElements: 1,
      });

    approvalService.decide.mockResolvedValueOnce({});

    render(
      <Provider store={store}>
        <MemoryRouter>
          <Inbox />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Annual Leave (3 days)')).toBeDefined();
    });

    const approveBtn = document.getElementById('btn-approve-step-101');
    fireEvent.click(approveBtn);

    await waitFor(() => {
      const confirmBtn = document.getElementById('btn-confirm-decide');
      expect(confirmBtn).toBeTruthy();
      fireEvent.click(confirmBtn);
    });

    await waitFor(() => {
      expect(approvalService.decide).toHaveBeenCalledWith('step-101', {
        decision: 'APPROVED',
        comment: '',
      });
      expect(store.getState().approvals.pendingCount).toBe(1);
    });
  });

  it('blocks rejection when comment is missing in decide modal', async () => {
    render(
      <Provider store={store}>
        <MemoryRouter>
          <Inbox />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Annual Leave (3 days)')).toBeDefined();
    });

    const rejectBtn = document.getElementById('btn-reject-step-101');
    fireEvent.click(rejectBtn);

    await waitFor(() => {
      const confirmBtn = document.getElementById('btn-confirm-decide');
      fireEvent.click(confirmBtn);
    });

    await waitFor(() => {
      expect(screen.getByText('Comment is required when rejecting a request.')).toBeDefined();
      expect(approvalService.decide).not.toHaveBeenCalled();
    });
  });

  it('renders NotEntitled when user lacks core.approval.decide permission', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);

    render(
      <Provider store={store}>
        <MemoryRouter>
          <Inbox />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Module Not Subscribed')).toBeDefined();
    });
    expect(approvalService.pending).not.toHaveBeenCalled();
  });
});
