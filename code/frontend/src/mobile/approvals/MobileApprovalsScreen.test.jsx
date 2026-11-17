import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MobileApprovalsScreen } from './MobileApprovalsScreen.jsx';
import { mobileApprovalService } from './mobileApprovalService.js';
import approvalReducer from '../../core/approvals/approvalSlice.js';

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
  return render(<Provider store={store}>{ui}</Provider>);
}

describe('MobileApprovalsScreen component (W-98.5 §6)', () => {
  const mockItems = [
    {
      id: 'step-1',
      instanceId: 'inst-1',
      flowType: 'LEAVE',
      summary: 'Casual Leave from 24 Nov to 26 Nov',
      subjectEmployeeName: 'Priya Sharma',
      subjectEmployeeId: 'EMP-045',
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
      subjectEmployeeId: 'EMP-088',
      stepIndex: 0,
      totalSteps: 1,
      createdAt: '2026-11-16T08:30:00Z',
      reason: 'Plumber visit',
      daysCount: 1,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders pending approval cards and badge count', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: mockItems,
      total: 2,
      unfilteredTotal: 2,
      countsByType: { ALL: 2, LEAVE: 1, WORK_FROM_HOME: 1 },
    });

    renderWithStore(<MobileApprovalsScreen />);

    expect(screen.getByTestId('approvals-loading')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByTestId('mobile-approvals-screen')).toBeTruthy();
      expect(screen.getByText('Approvals')).toBeTruthy();
      expect(screen.getByTestId('approvals-feed')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-1')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-2')).toBeTruthy();
    });
  });

  it('renders empty state when there are no pending items', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: [],
      total: 0,
      unfilteredTotal: 0,
      countsByType: { ALL: 0 },
    });

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByText('All Caught Up!')).toBeTruthy();
    });
  });

  it('filters cards when selecting category segment', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: mockItems,
      total: 2,
      unfilteredTotal: 2,
      countsByType: { ALL: 2, LEAVE: 1, WORK_FROM_HOME: 1 },
    });

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('approval-card-step-1')).toBeTruthy();
      expect(screen.getByTestId('approval-card-step-2')).toBeTruthy();
    });

    // Tap WFH segment filter
    const wfhTab = screen.getByText('WFH (1)');
    fireEvent.click(wfhTab);

    await waitFor(() => {
      expect(screen.queryByTestId('approval-card-step-1')).toBeNull();
      expect(screen.getByTestId('approval-card-step-2')).toBeTruthy();
    });
  });

  it('executes Approve flow successfully', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: mockItems,
      total: 2,
      unfilteredTotal: 2,
      countsByType: { ALL: 2, LEAVE: 1, WORK_FROM_HOME: 1 },
    });
    mobileApprovalService.decide.mockResolvedValueOnce({ success: true });

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('btn-quick-approve-step-1')).toBeTruthy();
    });

    // Click quick approve
    fireEvent.click(screen.getByTestId('btn-quick-approve-step-1'));

    // Modal appears
    await waitFor(() => {
      expect(screen.getByTestId('modal-confirm-approve')).toBeTruthy();
    });

    // Confirm approval
    fireEvent.click(screen.getByTestId('modal-confirm-approve'));

    await waitFor(() => {
      expect(mobileApprovalService.decide).toHaveBeenCalledWith({
        stepId: 'step-1',
        decision: 'APPROVED',
        comment: '',
      });
      // step-1 is removed from feed
      expect(screen.queryByTestId('approval-card-step-1')).toBeNull();
    });
  });

  it('requires mandatory rejection reason before submitting Reject', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      rows: mockItems,
      total: 2,
      unfilteredTotal: 2,
      countsByType: { ALL: 2, LEAVE: 1, WORK_FROM_HOME: 1 },
    });
    mobileApprovalService.decide.mockResolvedValueOnce({ success: true });

    renderWithStore(<MobileApprovalsScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('btn-quick-reject-step-1')).toBeTruthy();
    });

    // Click quick reject
    fireEvent.click(screen.getByTestId('btn-quick-reject-step-1'));

    // Modal appears
    await waitFor(() => {
      expect(screen.getByTestId('modal-confirm-reject')).toBeTruthy();
    });

    // Click confirm reject with blank comment -> error shown
    fireEvent.click(screen.getByTestId('modal-confirm-reject'));

    await waitFor(() => {
      expect(screen.getByText('Please provide a reason for rejection')).toBeTruthy();
      expect(mobileApprovalService.decide).not.toHaveBeenCalled();
    });

    // Enter rejection comment
    const input = screen.getByTestId('decision-comment-input');
    fireEvent.change(input, { target: { value: 'Staffing shortage' } });

    // Click confirm reject again
    fireEvent.click(screen.getByTestId('modal-confirm-reject'));

    await waitFor(() => {
      expect(mobileApprovalService.decide).toHaveBeenCalledWith({
        stepId: 'step-1',
        decision: 'REJECTED',
        comment: 'Staffing shortage',
      });
    });
  });
});

