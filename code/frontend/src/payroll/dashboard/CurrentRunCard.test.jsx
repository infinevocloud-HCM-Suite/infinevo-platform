import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CurrentRunCard } from './CurrentRunCard.jsx';
import { dashboardFixture } from './dashboardFixture.js';

const base = dashboardFixture().current_run;
const renderCard = (run, today) =>
  render(
    <MemoryRouter>
      <CurrentRunCard run={run} today={today} />
    </MemoryRouter>
  );

describe('CurrentRunCard (W-47.5 §7)', () => {
  it('shows the figures as sent and links to the run', () => {
    renderCard(base, '2026-09-15');
    expect(screen.getByText('Current run: 2026-09')).toBeDefined();
    expect(screen.getByText('Computed')).toBeDefined();
    expect(screen.getByText('12,38,952.50')).toBeDefined();
    expect(screen.getByText('98,000.00')).toBeDefined();
    expect(screen.getByText('11,40,952.50')).toBeDefined();
    expect(screen.getByRole('link', { name: 'Open run' }).getAttribute('href')).toBe('/payroll/runs/run-9');
  });

  it('shows progress only while COMPUTING', () => {
    const { unmount } = renderCard(
      { ...base, status: 'COMPUTING', progress_done: 10, progress_total: 40 },
      '2026-09-15'
    );
    expect(screen.getByRole('progressbar')).toBeDefined();
    expect(screen.getByText('10 of 40 employees computed')).toBeDefined();
    unmount();

    renderCard(base, '2026-09-15');
    expect(screen.queryByRole('progressbar')).toBeNull();
  });

  it('warns "Payment due" when the pay date has passed and the run is not PAID', () => {
    renderCard(base, '2026-10-03');
    expect(screen.getByText('Payment due')).toBeDefined();
  });

  it('does not warn when the run is PAID, or the pay date is today', () => {
    const { unmount } = renderCard({ ...base, status: 'PAID', paid_on: '2026-09-30' }, '2026-10-03');
    expect(screen.queryByText('Payment due')).toBeNull();
    unmount();

    renderCard(base, '2026-09-30');
    expect(screen.queryByText('Payment due')).toBeNull();
  });
});
