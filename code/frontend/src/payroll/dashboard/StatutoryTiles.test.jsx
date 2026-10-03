import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { StatutoryTiles } from './StatutoryTiles.jsx';
import { dashboardFixture } from './dashboardFixture.js';

describe('StatutoryTiles (W-47.5 §7)', () => {
  it('shows four tiles, each saying what it sums', () => {
    render(<StatutoryTiles statutory={dashboardFixture().statutory} fy={2026} />);
    expect(screen.getAllByText('PAID runs, FY 2026-27')).toHaveLength(4);
    expect(within(screen.getByTestId('tile-epf')).getAllByText('36,000.00')).toHaveLength(2);
    expect(within(screen.getByTestId('tile-professional_tax')).getByText('7,800.00')).toBeDefined();
    expect(within(screen.getByTestId('tile-tds')).queryByText('Employer')).toBeNull();
  });

  it('shows zero as 0.00, never a dash', () => {
    render(<StatutoryTiles statutory={dashboardFixture().statutory} fy={2026} />);
    const esi = screen.getByTestId('tile-esi');
    expect(within(esi).getAllByText('0.00')).toHaveLength(2);
    expect(within(esi).queryByText('-')).toBeNull();
  });
});
