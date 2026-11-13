import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SettingsLayout, SETTINGS_MENU_ITEMS } from './SettingsLayout.jsx';

describe('SettingsLayout component (W-47.1b §5, §5a, §7)', () => {
  it('renders all six menu items including the sixth Tax declaration entry', () => {
    render(
      <MemoryRouter initialEntries={['/payroll/settings/pay-schedule']}>
        <SettingsLayout>
          <div>Settings Content</div>
        </SettingsLayout>
      </MemoryRouter>
    );

    expect(screen.getByText('Payroll Settings')).toBeDefined();
    expect(screen.getByText('Settings Content')).toBeDefined();

    // Verify all 6 menu entries
    expect(screen.getByRole('menuitem', { name: /pay schedule/i })).toBeDefined();
    expect(screen.getByRole('menuitem', { name: /epf/i })).toBeDefined();
    expect(screen.getByRole('menuitem', { name: /esi/i })).toBeDefined();
    expect(screen.getByRole('menuitem', { name: /professional tax/i })).toBeDefined();
    expect(screen.getByRole('menuitem', { name: /tds/i })).toBeDefined();
    expect(screen.getByRole('menuitem', { name: /flexible benefit plan/i })).toBeDefined();
    expect(screen.getByRole('menuitem', { name: /tax declaration/i })).toBeDefined();
    expect(screen.getByRole('menuitem', { name: /form 16/i })).toBeDefined();
  });

  it('D-141: TDS sits right after Professional Tax and before the Flexible Benefit Plan', () => {
    const keys = SETTINGS_MENU_ITEMS.map((item) => item.key);
    const tds = keys.indexOf('/payroll/settings/tds');
    expect(tds).toBe(keys.indexOf('/payroll/settings/professional-tax') + 1);
    expect(keys[tds + 1]).toBe('/payroll/settings/fbp');
    expect(SETTINGS_MENU_ITEMS[tds].label).toBe('TDS');
  });
});
