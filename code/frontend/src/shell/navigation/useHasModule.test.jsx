/**
 * useHasModule: true only for a module in the feed's `modules`; the menu items play no part.
 * The real hook is rendered inside the real provider.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NavigationProvider } from './useNavigation.js';
import { useHasModule } from './useHasModule.js';

function Probe() {
  const hrms = useHasModule('HRMS');
  const payroll = useHasModule('payroll');
  return <p>{`hrms:${hrms} payroll:${payroll}`}</p>;
}

function renderWithFeed(feed) {
  return render(
    <NavigationProvider value={{ items: [], actions: [], loading: false, error: null, ...feed }}>
      <Probe />
    </NavigationProvider>,
  );
}

describe('useHasModule', () => {
  it('is true for a held module, in either case', () => {
    renderWithFeed({ modules: ['PAYROLL'] });

    expect(screen.getByText('hrms:false payroll:true')).toBeTruthy();
  });

  it('ignores the menu items: a payroll item without the module is not the module', () => {
    renderWithFeed({ items: [{ key: 'payroll.runs', path: '/payroll/runs' }], modules: [] });

    expect(screen.getByText('hrms:false payroll:false')).toBeTruthy();
  });

  it('is false when the feed carries no modules at all', () => {
    renderWithFeed({});

    expect(screen.getByText('hrms:false payroll:false')).toBeTruthy();
  });
});
