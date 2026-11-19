import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ApprovalRequestCard } from './ApprovalRequestCard.jsx';

describe('ApprovalRequestCard component (W-98.5 §6)', () => {
  const sampleItem = {
    id: 'step-101',
    instanceId: 'inst-202',
    flowType: 'LEAVE',
    summary: 'Casual Leave from 24 Nov to 26 Nov',
    subjectEmployeeName: 'Priya Sharma',
    subjectEmployeeId: 'EMP-045',
    stepIndex: 0,
    totalSteps: 2,
    createdAt: '2026-11-15T10:00:00Z',
    reason: 'Family event',
    daysCount: 3,
  };

  it('renders employee info, flow type tag, and request summary without employee code', () => {
    render(
      <ApprovalRequestCard
        item={sampleItem}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onClick={vi.fn()}
      />
    );

    expect(screen.getByTestId('approval-card-step-101')).toBeTruthy();
    expect(screen.getByText('Priya Sharma')).toBeTruthy();
    // Employee code must not be shown
    expect(screen.queryByText('EMP-045')).toBeNull();
    expect(screen.queryByText(/ID:/)).toBeNull();
    expect(screen.getByText('Leave')).toBeTruthy();
    expect(screen.getByText('Casual Leave from 24 Nov to 26 Nov')).toBeTruthy();
    expect(screen.getByText('"Family event"')).toBeTruthy();
    expect(screen.getByText('Step 1 of 2')).toBeTruthy();
    // Exact match assertion (W-98.5 code gaps)
    expect(screen.getByText('3 days')).toBeTruthy();
  });

  it('triggers onApprove when clicking Approve button', () => {
    const handleApprove = vi.fn();
    render(
      <ApprovalRequestCard
        item={sampleItem}
        onApprove={handleApprove}
        onReject={vi.fn()}
        onClick={vi.fn()}
      />
    );

    const approveBtn = screen.getByTestId('btn-quick-approve-step-101');
    fireEvent.click(approveBtn);

    expect(handleApprove).toHaveBeenCalledTimes(1);
    expect(handleApprove).toHaveBeenCalledWith(sampleItem);
  });

  it('triggers onReject when clicking Reject button', () => {
    const handleReject = vi.fn();
    render(
      <ApprovalRequestCard
        item={sampleItem}
        onApprove={vi.fn()}
        onReject={handleReject}
        onClick={vi.fn()}
      />
    );

    const rejectBtn = screen.getByTestId('btn-quick-reject-step-101');
    fireEvent.click(rejectBtn);

    expect(handleReject).toHaveBeenCalledTimes(1);
    expect(handleReject).toHaveBeenCalledWith(sampleItem);
  });

  it('triggers onClick when clicking anywhere on the card body', () => {
    const handleClick = vi.fn();
    render(
      <ApprovalRequestCard
        item={sampleItem}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onClick={handleClick}
      />
    );

    fireEvent.click(screen.getByTestId('approval-card-step-101'));
    expect(handleClick).toHaveBeenCalledTimes(1);
    expect(handleClick).toHaveBeenCalledWith(sampleItem);
  });
});
