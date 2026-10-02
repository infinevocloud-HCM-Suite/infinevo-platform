import { describe, it, expect } from 'vitest';
import taxReducer, {
  setFy,
  setHeader,
  setSectionData,
  setItems,
  setLoading,
  setError,
  resetTaxState,
} from './taxSlice';

describe('taxSlice', () => {
  const initial = {
    fy: null,
    header: null,
    sections: {
      housing: null,
      deductions: null,
      otherIncome: null,
      summary: null,
    },
    items: [],
    itemsRegime: null,
    loading: false,
    error: null,
  };

  it('initializes with default empty state', () => {
    expect(taxReducer(undefined, { type: '@@INIT' })).toEqual(initial);
  });

  describe('setFy', () => {
    it('sets financial year and clears stale sections/header if fy changed', () => {
      const stateWithData = {
        ...initial,
        fy: '2025-26',
        header: { regime: 'OLD' },
        sections: { housing: { house_rent: [] }, deductions: null, otherIncome: null, summary: null },
        error: 'previous error',
      };

      const next = taxReducer(stateWithData, setFy('2026-27'));
      expect(next.fy).toBe('2026-27');
      expect(next.header).toBeNull();
      expect(next.sections.housing).toBeNull();
      expect(next.error).toBeNull();
    });

    it('does not reset state if fy has not changed', () => {
      const stateWithData = {
        ...initial,
        fy: '2026-27',
        header: { regime: 'OLD' },
      };

      const next = taxReducer(stateWithData, setFy('2026-27'));
      expect(next.header).toEqual({ regime: 'OLD' });
    });
  });

  describe('setHeader', () => {
    it('stores header data', () => {
      const header = { regime: 'NEW', window_open: true };
      const next = taxReducer(initial, setHeader(header));
      expect(next.header).toEqual(header);
    });
  });

  describe('setSectionData', () => {
    it('updates specified section if section key is valid', () => {
      const housingData = { house_rent: [{ month: 4, rent_amount: 20000 }] };
      const next = taxReducer(initial, setSectionData({ section: 'housing', data: housingData }));
      expect(next.sections.housing).toEqual(housingData);
    });

    it('replaces a loaded section with the save response, leaving the others alone', () => {
      const loaded = taxReducer(
        taxReducer(initial, setSectionData({ section: 'housing', data: { house_rent: [{ id: 'old' }] } })),
        setSectionData({ section: 'otherIncome', data: [{ id: 'oi-1' }] }),
      );
      const saved = { house_rent: [{ id: 'new' }], home_loans: [], let_out_properties: [] };
      const next = taxReducer(loaded, setSectionData({ section: 'housing', data: saved }));
      expect(next.sections.housing).toEqual(saved);
      expect(next.sections.otherIncome).toEqual([{ id: 'oi-1' }]);
    });

    it('ignores unknown section key', () => {
      const next = taxReducer(initial, setSectionData({ section: 'unknownSection', data: { foo: 'bar' } }));
      expect(next.sections).toEqual(initial.sections);
    });
  });

  describe('setItems', () => {
    it('updates 6a catalogue items', () => {
      const items = [{ code: '80C', name: 'LIC' }];
      const next = taxReducer(initial, setItems(items));
      expect(next.items).toEqual(items);
    });

    it('records the regime the catalogue was read for', () => {
      const next = taxReducer(initial, setItems({ items: [{ id: 'a' }], regime: 'NEW' }));
      expect(next.items).toEqual([{ id: 'a' }]);
      expect(next.itemsRegime).toBe('NEW');
    });

    it('falls back to empty array if payload is nullish', () => {
      const next = taxReducer(initial, setItems(null));
      expect(next.items).toEqual([]);
    });
  });

  describe('setLoading & setError', () => {
    it('sets loading boolean', () => {
      expect(taxReducer(initial, setLoading(true)).loading).toBe(true);
      expect(taxReducer(initial, setLoading(false)).loading).toBe(false);
    });

    it('sets error string or object', () => {
      const next = taxReducer(initial, setError('Network failure'));
      expect(next.error).toBe('Network failure');
    });
  });

  describe('resetTaxState', () => {
    it('resets state completely back to initial', () => {
      const dirtyState = {
        fy: '2026-27',
        header: { regime: 'OLD' },
        sections: { housing: {}, deductions: {}, otherIncome: {}, summary: {} },
        items: [{ code: '80C' }],
        itemsRegime: 'OLD',
        loading: true,
        error: 'err',
      };
      const next = taxReducer(dirtyState, resetTaxState());
      expect(next).toEqual(initial);
    });
  });
});
