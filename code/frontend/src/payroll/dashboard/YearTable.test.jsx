import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { YearTable } from './YearTable.jsx';
import { dashboardFixture } from './dashboardFixture.js';

const renderTable = (data = dashboardFixture()) =>
  render(
    <MemoryRouter initialEntries={['/payroll/dashboard']}>
      <Routes>
        <Route
          path="/payroll/dashboard"
          element={<YearTable months={data.months} yearTotals={data.year_totals} recentRuns={data.recent_runs} />}
        />
        <Route path="/payroll/runs/:id" element={<div>run page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('YearTable (W-47.5 §7)', () => {
  it('takes the footer from year_totals, not from the column', () => {
    renderTable();
    const footer = screen.getByText('Paid (1 runs)').closest('tr');
    // The gross column adds up to 24,38,952.50; the footer is the PAID run alone, as sent.
    expect(within(footer).getByText('12,00,000.00')).toBeDefined();
    expect(within(footer).getByText('95,000.00')).toBeDefined();
    expect(within(footer).getByText('40,000.00')).toBeDefined();
    expect(within(footer).getByText('11,05,000.00')).toBeDefined();
    expect(screen.queryByText('24,38,952.50')).toBeNull();
  });

  it('lists the months and recent runs, and a row opens its run', () => {
    renderTable();
    const months = screen.getByRole('table', { name: 'Months' });
    expect(within(months).getByText('41,000.00')).toBeDefined();
    const recent = screen.getByRole('table', { name: 'Recent runs' });
    expect(within(recent).getAllByText('2026-08-31').length).toBeGreaterThan(0);
    fireEvent.click(within(recent).getByText('2026-08'));
    expect(screen.getByText('run page')).toBeDefined();
  });
});
