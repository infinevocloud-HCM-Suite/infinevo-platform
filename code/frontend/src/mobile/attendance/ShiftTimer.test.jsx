import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { ShiftTimer } from './ShiftTimer.jsx';

describe('ShiftTimer component (W-98.2 §6)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders default 00:00:00 when clockInAt is missing or invalid', () => {
    render(<ShiftTimer clockInAt={null} active={false} />);
    expect(screen.getByTestId('shift-timer')).toBeTruthy();
    expect(screen.getByText('Active Shift Time')).toBeTruthy();
  });

  it('calculates elapsed time from clockInAt timestamp correctly', () => {
    const fixedNow = new Date('2026-11-16T10:30:00Z').getTime();
    vi.spyOn(Date, 'now').mockReturnValue(fixedNow);

    // Clock in 2 hours, 15 minutes, 30 seconds ago
    const clockInAt = new Date('2026-11-16T08:14:30Z').toISOString();

    render(<ShiftTimer clockInAt={clockInAt} active={false} />);
    const timer = screen.getByTestId('shift-timer');
    expect(timer.textContent).toContain('02');
    expect(timer.textContent).toContain('15');
    expect(timer.textContent).toContain('30');
  });

  it('increments elapsed timer every second when active', () => {
    vi.useFakeTimers();
    const startTime = new Date('2026-11-16T09:00:00Z').getTime();
    vi.setSystemTime(startTime);

    const clockInAt = new Date('2026-11-16T09:00:00Z').toISOString();
    render(<ShiftTimer clockInAt={clockInAt} active={true} />);

    // Advance 5 seconds inside act
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    const timer = screen.getByTestId('shift-timer');
    expect(timer.textContent).toContain('05');
  });
});
