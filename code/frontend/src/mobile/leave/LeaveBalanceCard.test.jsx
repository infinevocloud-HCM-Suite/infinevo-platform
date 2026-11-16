import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { LeaveBalanceCard } from './LeaveBalanceCard.jsx';

describe('LeaveBalanceCard component (W-98.3 §6)', () => {
  const sampleBalance = {
    leaveTypeId: 'type-cl',
    leaveTypeCode: 'CL',
    leaveTypeName: 'Casual Leave',
    availableDays: 8.5,
    consumedDays: 4,
    totalDays: 14,
    pendingDays: 1.5,
    expired: false,
  };

  it('renders leave name, code, and available days', () => {
    render(<LeaveBalanceCard balance={sampleBalance} />);
    expect(screen.getByText('Casual Leave')).toBeTruthy();
    expect(screen.getByText('CL')).toBeTruthy();
    expect(screen.getByTestId('balance-available-days').textContent).toBe('8.5');
    expect(screen.getByText('days available')).toBeTruthy();
    expect(screen.getByText('Used: 4d')).toBeTruthy();
    expect(screen.getByText('Total: 14d')).toBeTruthy();
    expect(screen.getByText('1.5 pending approval')).toBeTruthy();
  });

  it('shows Expired tag and grayed styling when leave type is expired', () => {
    const expiredBalance = {
      ...sampleBalance,
      expired: true,
      pendingDays: 0,
    };
    render(<LeaveBalanceCard balance={expiredBalance} />);
    expect(screen.getByText('Expired')).toBeTruthy();
    expect(screen.getByText('days (expired)')).toBeTruthy();
  });

  it('triggers onClick handler when card is clicked', () => {
    const handleClick = vi.fn();
    render(<LeaveBalanceCard balance={sampleBalance} onClick={handleClick} />);
    fireEvent.click(screen.getByTestId('leave-balance-card'));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
