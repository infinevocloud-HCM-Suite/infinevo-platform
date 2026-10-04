import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { HeadcountCard } from './HeadcountCard.jsx';
import { dashboardFixture } from './dashboardFixture.js';

describe('HeadcountCard (W-47.5 §7)', () => {
  it('shows active today and every skip reason with a readable label', () => {
    render(<HeadcountCard employees={dashboardFixture().employees} />);
    expect(screen.getByText('42')).toBeDefined();
    expect(screen.getByText('At the last run (2026-09)')).toBeDefined();
    const list = screen.getByRole('list', { name: 'Skipped by reason' });
    expect(within(list).getByText('No bank details: 2')).toBeDefined();
    expect(within(list).getByText('No salary in force: 0')).toBeDefined();
  });

  it('hides the last-run part when as_at_run is null', () => {
    render(<HeadcountCard employees={{ active_today: 5, as_at_run: null }} />);
    expect(screen.getByText('5')).toBeDefined();
    expect(screen.queryByText(/At the last run/)).toBeNull();
  });
});
