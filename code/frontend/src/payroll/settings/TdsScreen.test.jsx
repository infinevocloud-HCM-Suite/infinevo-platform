import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import * as useCanModule from '@shell/screens';
import { TdsScreen } from './TdsScreen.jsx';
import { statutoryService } from './statutoryService.js';

vi.mock('./statutoryService.js', () => ({
  statutoryService: {
    get: vi.fn(),
    save: vi.fn(),
  },
}));

const switchEl = () => document.getElementById('switch-tds-enabled');
const isOn = () => switchEl().getAttribute('aria-checked') === 'true';

describe('TdsScreen (D-141)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => action === 'payroll.settings.manage');
  });

  it('loads the switch state from GET tds', async () => {
    statutoryService.get.mockResolvedValueOnce({ enabled: false, source: 'PERSISTED' });

    render(<TdsScreen />);

    await waitFor(() => expect(switchEl()).toBeTruthy());
    expect(statutoryService.get).toHaveBeenCalledWith('tds');
    expect(isOn()).toBe(false);
    expect(screen.getByText('TDS')).toBeDefined();
    expect(screen.getByText('Deduct TDS in pay runs')).toBeDefined();
    expect(screen.queryByText('Not set yet, TDS is deducted by default')).toBeNull();
  });

  it('shows the not-set text when the source is DEFAULT', async () => {
    statutoryService.get.mockResolvedValueOnce({ enabled: true, source: 'DEFAULT' });

    render(<TdsScreen />);

    await waitFor(() => expect(screen.getByText('Not set yet, TDS is deducted by default')).toBeDefined());
    expect(isOn()).toBe(true);
  });

  it('Save calls save(tds, {enabled:false}) after the switch is turned off', async () => {
    statutoryService.get.mockResolvedValueOnce({ enabled: true, source: 'DEFAULT' });
    statutoryService.save.mockResolvedValueOnce({ enabled: false, source: 'PERSISTED' });

    render(<TdsScreen />);

    await waitFor(() => expect(switchEl()).toBeTruthy());
    fireEvent.click(switchEl());
    expect(isOn()).toBe(false);
    fireEvent.click(document.getElementById('btn-save-tds'));

    await waitFor(() => expect(statutoryService.save).toHaveBeenCalledWith('tds', { enabled: false }));
    await waitFor(() => expect(screen.queryByText('Not set yet, TDS is deducted by default')).toBeNull());
  });

  it('without payroll.settings.manage there is no Save and the switch is disabled', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    statutoryService.get.mockResolvedValueOnce({ enabled: true, source: 'PERSISTED' });

    render(<TdsScreen />);

    await waitFor(() => expect(switchEl()).toBeTruthy());
    expect(document.getElementById('btn-save-tds')).toBeNull();
    expect(switchEl().disabled).toBe(true);
    expect(statutoryService.save).not.toHaveBeenCalled();
  });
});
