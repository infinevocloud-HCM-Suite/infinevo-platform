import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import dayjs from 'dayjs';
import { ProfessionalTaxScreen } from './ProfessionalTaxScreen.jsx';
import { ptService } from './ptService.js';

vi.mock('./ptService.js', () => ({
  ptService: {
    list: vi.fn(),
    get: vi.fn(),
    override: vi.fn(),
    removeOverride: vi.fn(),
    history: vi.fn(),
  },
}));

const MOCK_STATES = [
  {
    state_code: 'MH',
    state_name: 'Maharashtra',
    source: 'REFERENCE',
    registration_number: null,
    effective_from: '2026-04-01',
    slabs: [
      {
        id: 'slab-1',
        from_amount: 0,
        to_amount: 7500,
        amount: 0,
        is_female_exempt: false,
        deduction_months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      },
      {
        id: 'slab-2',
        from_amount: 7501,
        to_amount: 10000,
        amount: 175,
        is_female_exempt: true,
        deduction_months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      },
    ],
  },
  {
    state_code: 'KA',
    state_name: 'Karnataka',
    source: 'OVERRIDE',
    registration_number: 'KA-PT-998877',
    effective_from: '2026-04-01',
    slabs: [
      {
        id: 'slab-ka-1',
        from_amount: 0,
        to_amount: null,
        amount: 200,
        is_female_exempt: false,
        deduction_months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      },
    ],
  },
];

describe('ProfessionalTaxScreen (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    ptService.list.mockResolvedValue(MOCK_STATES);
  });

  it('renders one panel per state with source tags and slabs table', async () => {
    render(<ProfessionalTaxScreen />);

    await waitFor(() => {
      expect(screen.getByText(/Maharashtra \(MH\)/)).toBeDefined();
      expect(screen.getByText(/Karnataka \(KA\)/)).toBeDefined();
      expect(screen.getByTestId('pt-source-tag-MH')).toBeDefined();
      expect(screen.getByTestId('pt-source-tag-KA')).toBeDefined();
      expect(screen.getByTestId('pt-source-tag-MH').textContent).toBe('REFERENCE');
      expect(screen.getByTestId('pt-source-tag-KA').textContent).toBe('OVERRIDE');
    });
  });

  it('override drawer PUTs slabs on submission', async () => {
    ptService.override.mockResolvedValue({ status: 200 });

    render(<ProfessionalTaxScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('pt-override-btn-MH')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('pt-override-btn-MH'));

    await waitFor(() => {
      expect(screen.getByTestId('pt-override-drawer')).toBeDefined();
      expect(screen.getByTestId('save-override-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-override-button'));

    await waitFor(() => {
      expect(ptService.override).toHaveBeenCalledTimes(1);
    });

    const [stateCode, payload] = ptService.override.mock.calls[0];
    expect(stateCode).toBe('MH');
    expect(payload.slabs).toBeDefined();
    expect(Array.isArray(payload.slabs)).toBe(true);
    expect(payload.slabs.length).toBeGreaterThanOrEqual(1);
  });

  it('remove override calls DELETE .../override', async () => {
    ptService.removeOverride.mockResolvedValue({ status: 204 });

    render(<ProfessionalTaxScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('pt-remove-btn-KA')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('pt-remove-btn-KA'));

    await waitFor(() => {
      expect(screen.getByText('Yes, Remove')).toBeDefined();
    });

    fireEvent.click(screen.getByText('Yes, Remove'));

    await waitFor(() => {
      expect(ptService.removeOverride).toHaveBeenCalledWith('KA');
    });
  });

  it('D-69: the history drawer shows each PtHistoryResponse — operation, when, who, slabs before and after', async () => {
    // Shaped as statutory/pt/PtHistoryResponse serialises it (its @JsonProperty names).
    ptService.history.mockResolvedValue([
      {
        id: 'hist-2',
        state_code: 'KA',
        operation: 'OVERRIDE_SET',
        before_slabs: [
          { from_amount: 0, to_amount: null, amount: 150, is_female_exempt: false, deduction_months: [] },
        ],
        after_slabs: [
          { from_amount: 0, to_amount: null, amount: 200, is_female_exempt: false, deduction_months: [] },
        ],
        changed_at: '2026-04-01T10:00:00Z',
        changed_by: 'user-11111111',
      },
      {
        id: 'hist-1',
        state_code: 'KA',
        operation: 'OVERRIDE_RESET',
        before_slabs: [],
        after_slabs: [],
        changed_at: '2026-03-15T08:30:00Z',
        changed_by: 'user-22222222',
      },
    ]);

    render(<ProfessionalTaxScreen />);

    await waitFor(() => {
      expect(screen.getByTestId('pt-history-btn-KA')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('pt-history-btn-KA'));

    await waitFor(() => {
      expect(ptService.history).toHaveBeenCalledWith('KA');
      expect(screen.getByTestId('pt-history-entry-0')).toBeDefined();
    });

    const first = within(screen.getByTestId('pt-history-entry-0'));
    expect(first.getByText('OVERRIDE_SET')).toBeDefined();
    expect(first.getByText(dayjs('2026-04-01T10:00:00Z').format('D MMM YYYY, HH:mm'))).toBeDefined();
    expect(first.getByText(/user-11111111/)).toBeDefined();
    expect(within(screen.getByTestId('pt-history-before-0')).getByText('₹150')).toBeDefined();
    expect(within(screen.getByTestId('pt-history-after-0')).getByText('₹200')).toBeDefined();

    const second = within(screen.getByTestId('pt-history-entry-1'));
    expect(second.getByText('OVERRIDE_RESET')).toBeDefined();
    expect(second.getByText(dayjs('2026-03-15T08:30:00Z').format('D MMM YYYY, HH:mm'))).toBeDefined();
    expect(second.getByText(/user-22222222/)).toBeDefined();
  });
});
