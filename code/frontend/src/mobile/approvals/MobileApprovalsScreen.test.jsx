import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MobileApprovalsScreen } from './MobileApprovalsScreen.jsx';
import { mobileApprovalService } from './mobileApprovalService.js';
import approvalReducer from '../../core/approvals/approvalSlice.js';
import { errorMsg, successMsg } from '../../shared/ui/msgHelper.js';

vi.mock('./mobileApprovalService.js', () => ({
  mobileApprovalService: {
    getPendingApprovals: vi.fn(),
    getInstanceDetail: vi.fn(),
    decide: vi.fn(),
  },
}));

vi.mock('../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

function renderWithStore(ui) {
  const store = configureStore({
    reducer: {
      approvals: approvalReducer,
    },
  });
  return {
    store,
    ...render(<Provider store={store}>{ui}</Provider>),
  };
}

describe('MobileApprovalsScreen component (W-98.5 §7)', () => {
  const mockItems = [
    {
      id: 'step-1',
      instanceId: 'inst-1',
      flowType: 'LEAVE',
      summary: 'Casual Leave from 24 Nov to 26 Nov',
      subjectEmployeeName: 'Priya Sharma',
      stepIndex: 0,
      totalSteps: 2,
      createdAt: '2026-11-15T10:00:00Z',
      reason: 'Personal work',
      daysCount: 2,
    },
    {
      id: 'step-2',
      instanceId: 'inst-2',
      flowType: 'WORK_FROM_HOME',
      summary: 'WFH for Client visit',
      subjectEmployeeName: 'Rahul Verma',
      stepIndex: 0,
      totalSteps: 1,
      createdAt: '2026-11-16T08:30:00Z',
      reason: 'Plumber visit',
      daysCount: 1,
    },
    {
      id: 'step-3',
      instanceId: 'inst-3',
      flowType: 'REIMBURSEMENT',
      summary: 'Travel Expense Claim',
      subjectEmployeeName: 'Ankit Patel',
      stepIndex: 0,
      totalSteps: 1,
      createdAt: '2026-11-17T11:00:00Z',
      reason: 'Client lunch',
      daysCount: null,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders pending approval cards and badge count from server totalElements', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: mockItems,
      total: 3,
      totalElements: 3,
      hasMore: false,
      countsByType: { ALL: 3, LEAVE: 1, WORK_FROM_HOME: 1, OTHER: 1 },
    });

    const { store } = renderWithStore(<MobileApprovalsScreen />);

    expect(screen.getByTestId('approvals-loading')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByTestId('mobile-approvals-screen')).toBeTruthy();
      expect(screen.getByText('Approvals')).toBeTruthy();
      expect(screen.getByTestId('approvals-feed')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-1')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-2')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-3')).toBeTruthy();
      expect(store.getState().approvals.pendingCount).toBe(3);
    });
  });

  it('renders distinct empty state when there are no pending items', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: [],
      total: 0,
      totalElements: 0,
      hasMore: false,
      countsByType: { ALL: 0 },
    });

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('approvals-empty-view')).toBeTruthy();
      expect(screen.getByText('All Caught Up!')).toBeTruthy();
    });
  });

  it('renders distinct error state when loading fails and list is empty', async () => {
    mobileApprovalService.getPendingApprovals.mockRejectedValueOnce(
      new Error('Failed to reach backend server')
    );

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('approvals-error-view')).toBeTruthy();
      expect(screen.getByText('Failed to load approvals')).toBeTruthy();
      expect(screen.getByText('Failed to reach backend server')).toBeTruthy();
    });
  });

  it('filters cards by category including Other', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: mockItems,
      total: 3,
      totalElements: 3,
      hasMore: false,
      countsByType: { ALL: 3, LEAVE: 1, WORK_FROM_HOME: 1, OTHER: 1 },
    });

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('approval-card-step-1')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-2')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-3')).toBeTruthy();
    });

    // Tap WFH segment filter
    const wfhTab = screen.getByText('WFH (1)');
    fireEvent.click(wfhTab);

    await waitFor(() => {
      expect(screen.queryByTestId('approval-card-step-1')).toBeNull();
      expect(screen.getByTestId('approval-card-step-2')).toBeTruthy();
      expect(screen.queryByTestId('approval-card-step-3')).toBeNull();
    });

    // Tap Other segment filter
    const otherTab = screen.getByText('Other (1)');
    fireEvent.click(otherTab);

    await waitFor(() => {
      expect(screen.queryByTestId('approval-card-step-1')).toBeNull();
      expect(screen.queryByTestId('approval-card-step-2')).toBeNull();
      expect(screen.getByTestId('approval-card-step-3')).toBeTruthy();
    });
  });

  it('loads next page of approvals when clicking Load More', async () => {
    mobileApprovalService.getPendingApprovals
      .mockResolvedValueOnce({
        rows: [mockItems[0]],
        total: 1,
        totalElements: 2,
        hasMore: true,
        countsByType: { ALL: 2, LEAVE: 1 },
      })
      .mockResolvedValueOnce({
        rows: [mockItems[1]],
        total: 1,
        totalElements: 2,
        hasMore: false,
        countsByType: { ALL: 2 },
      });

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('approval-card-step-1')).toBeTruthy();
      expect(screen.queryByTestId('approval-card-step-2')).toBeNull();
      expect(screen.getByTestId('btn-load-more-approvals')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('btn-load-more-approvals'));

    await waitFor(() => {
      expect(mobileApprovalService.getPendingApprovals).toHaveBeenCalledWith({
        page: 1,
        size: 20,
      });
      expect(screen.getByTestId('approval-card-step-1')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-2')).toBeTruthy();
      expect(screen.queryByTestId('btn-load-more-approvals')).toBeNull();
    });
  });

  it('handles 400 "already decided" by showing message and reloading the list', async () => {
    mobileApprovalService.getPendingApprovals
      .mockResolvedValueOnce({
        rows: [mockItems[0]],
        total: 1,
        totalElements: 1,
        hasMore: false,
        countsByType: { ALL: 1, LEAVE: 1 },
      })
      .mockResolvedValueOnce({
        rows: [],
        total: 0,
        totalElements: 0,
        hasMore: false,
        countsByType: { ALL: 0 },
      });

    const error400 = new Error('Step already decided');
    error400.response = { status: 400, data: { message: 'Step was already processed' } };
    mobileApprovalService.decide.mockRejectedValueOnce(error400);

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('btn-quick-approve-step-1')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('btn-quick-approve-step-1'));

    await waitFor(() => {
      expect(screen.getByTestId('modal-confirm-approve')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('modal-confirm-approve'));

    await waitFor(() => {
      expect(errorMsg).toHaveBeenCalledWith('This request was already decided');
      // Second call to getPendingApprovals reloads the list
      expect(mobileApprovalService.getPendingApprovals).toHaveBeenCalledTimes(2);
    });

    // Stale card is gone in second load
    await waitFor(() => {
      expect(screen.queryByTestId('approval-card-step-1')).toBeNull();
      expect(screen.getByTestId('approvals-empty-view')).toBeTruthy();
    });
  });

  it('leaves the card in place when another error (e.g. 500) occurs', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: [mockItems[0]],
      total: 1,
      totalElements: 1,
      hasMore: false,
      countsByType: { ALL: 1, LEAVE: 1 },
    });

    const error500 = new Error('Internal Server Error');
    error500.response = { status: 500, data: { message: 'Database connectivity timeout' } };
    mobileApprovalService.decide.mockRejectedValueOnce(error500);

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('btn-quick-approve-step-1')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('btn-quick-approve-step-1'));

    await waitFor(() => {
      expect(screen.getByTestId('modal-confirm-approve')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('modal-confirm-approve'));

    await waitFor(() => {
      expect(errorMsg).toHaveBeenCalledWith('Database connectivity timeout');
      // List was NOT reloaded
      expect(mobileApprovalService.getPendingApprovals).toHaveBeenCalledTimes(1);
      // Card is still present in the list
      expect(screen.getByTestId('approval-card-step-1')).toBeTruthy();
    });
  });

  it('executes successful Approve and decrements pending count', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: [mockItems[0]],
      total: 1,
      totalElements: 1,
      hasMore: false,
      countsByType: { ALL: 1, LEAVE: 1 },
    });
    mobileApprovalService.decide.mockResolvedValueOnce({ success: true });

    const { store } = renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('btn-quick-approve-step-1')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('btn-quick-approve-step-1'));

    await waitFor(() => {
      expect(screen.getByTestId('modal-confirm-approve')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('modal-confirm-approve'));

    await waitFor(() => {
      expect(mobileApprovalService.decide).toHaveBeenCalledWith({
        stepId: 'step-1',
        decision: 'APPROVED',
        comment: '',
      });
      expect(successMsg).toHaveBeenCalledWith('Request Approved');
      expect(screen.queryByTestId('approval-card-step-1')).toBeNull();
      expect(store.getState().approvals.pendingCount).toBe(0);
    });
  });
});
