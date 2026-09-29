import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { TaxWindowScreen } from './TaxWindowScreen';
import { taxSettingsService } from './taxSettingsService';

vi.mock('./taxSettingsService', () => ({
  taxSettingsService: {
    get: vi.fn(),
    save: vi.fn(),
  },
}));

describe('TaxWindowScreen', () => {
  const sampleSettings = {
    financial_year: '2026-27',
    window_opens_on: '2026-04-01',
    window_closes_on: '2026-04-30',
    is_locked: false,
    default_tax_regime: 'NEW',
    can_change_tax_regime: true,
    pan_required_for_rent_over_threshold: 100000,
    updated_at: '2026-04-01T10:00:00Z',
    updated_by: 'admin@infinevo.com',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders and fetches settings on mount', async () => {
    taxSettingsService.get.mockResolvedValueOnce(sampleSettings);

    render(<TaxWindowScreen initialFy="2026-27" />);

    expect(screen.getByText('Tax Declaration Window Configuration')).toBeTruthy();
    expect(taxSettingsService.get).toHaveBeenCalledWith('2026-27');

    await waitFor(() => {
      expect(screen.getByText('Last updated:', { exact: false })).toBeTruthy();
      expect(screen.getByText('admin@infinevo.com', { exact: false })).toBeTruthy();
    });
  });

  it('displays error alert when settings fetch fails', async () => {
    taxSettingsService.get.mockRejectedValueOnce(new Error('Network error loading settings'));

    render(<TaxWindowScreen initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByText('Network error loading settings')).toBeTruthy();
    });
  });

  it('submits updated settings successfully', async () => {
    taxSettingsService.get.mockResolvedValueOnce(sampleSettings);
    taxSettingsService.save.mockResolvedValueOnce({
      ...sampleSettings,
      pan_required_for_rent_over_threshold: 150000,
    });

    render(<TaxWindowScreen initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByText('admin@infinevo.com', { exact: false })).toBeTruthy();
    });

    const saveBtn = screen.getByRole('button', { name: /save settings/i });
    expect(saveBtn.hasAttribute('disabled')).toBe(false);

    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(taxSettingsService.save).toHaveBeenCalledWith('2026-27', expect.objectContaining({
        window_opens_on: '2026-04-01',
        window_closes_on: '2026-04-30',
        default_tax_regime: 'NEW',
        can_change_tax_regime: true,
        is_locked: false,
      }));
    });
  });

  it('reloads settings on clicking reload button', async () => {
    taxSettingsService.get.mockResolvedValue(sampleSettings);

    render(<TaxWindowScreen initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByText('admin@infinevo.com', { exact: false })).toBeTruthy();
    });

    const reloadBtn = screen.getByRole('button', { name: /reload/i });
    fireEvent.click(reloadBtn);

    await waitFor(() => {
      expect(taxSettingsService.get).toHaveBeenCalledTimes(2);
    });
  });
});
