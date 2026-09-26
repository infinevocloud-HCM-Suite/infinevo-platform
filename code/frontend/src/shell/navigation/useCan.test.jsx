/**
 * useCan (W-12.3 §7): a code in `actions` renders the button; a missing code hides it; an empty
 * `actions` hides every guarded button. The real hook is rendered inside the real provider.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NavigationProvider } from './useNavigation.js';
import { useCan } from './useCan.js';

function DeleteButton() {
  const can = useCan('core.employee.delete');
  return can ? <button>Delete</button> : null;
}

function ExportButton() {
  const can = useCan('core.report.export');
  return can ? <button>Export</button> : null;
}

function renderWithActions(actions) {
  return render(
    <NavigationProvider value={{ items: [], actions, loading: false, error: null }}>
      <DeleteButton />
      <ExportButton />
    </NavigationProvider>,
  );
}

describe('useCan', () => {
  it('renders the button when the feed carries its code', () => {
    renderWithActions(['core.employee.read', 'core.employee.delete']);

    expect(screen.getByRole('button', { name: 'Delete' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Export' })).toBeNull();
  });

  it('hides the button when the code is missing', () => {
    renderWithActions(['core.employee.read']);

    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });

  it('hides every guarded button when actions is empty', () => {
    renderWithActions([]);

    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });

  it('accepts a Set as well as an array', () => {
    renderWithActions(new Set(['core.report.export']));

    expect(screen.getByRole('button', { name: 'Export' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });

  it('is false for anything but a non-empty string', () => {
    function Odd() {
      const a = useCan('');
      const b = useCan(undefined);
      const c = useCan(42);
      return <span>{String(a || b || c)}</span>;
    }
    render(
      <NavigationProvider value={{ items: [], actions: ['core.employee.delete'], loading: false, error: null }}>
        <Odd />
      </NavigationProvider>,
    );

    expect(screen.getByText('false')).toBeTruthy();
  });
});
