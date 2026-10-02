import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { HousingSection } from './HousingSection';
import { declarationService } from './declarationService';
import taxReducer from './taxSlice';
import { errorMsg } from '@shared/ui/msgHelper.js';

vi.mock('./declarationService', () => ({
  declarationService: {
    housing: vi.fn(),
    saveHouseRent: vi.fn(),
    saveHomeLoan: vi.fn(),
    saveLetOut: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

function makeStore() {
  return configureStore({ reducer: { tax: taxReducer } });
}

function renderSection(props, store = makeStore()) {
  const utils = render(
    <Provider store={store}>
      <HousingSection fy="2026-27" editable={true} {...props} />
    </Provider>,
  );
  return { ...utils, store };
}

// 2026-04..2026-08 is 5 months; 5 x 25000 = 125000 a year.
const rentRow = (overrides = {}) => ({
  from_month: '2026-04',
  to_month: '2026-08',
  address: 'Flat 101, Palm Heights, Mumbai',
  landlord_name: 'Rajesh Sharma',
  landlord_pan: '',
  is_metro: true,
  amount_per_month: 25000,
  ...overrides,
});

const housingWith = (house_rent, extra = {}) => ({
  house_rent,
  home_loans: [],
  let_out_properties: [],
  ...extra,
});

describe('HousingSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders existing housing declarations on load and stores them in the slice', async () => {
    const data = housingWith([rentRow({ landlord_pan: 'ABCDE1234F' })], {
      home_loans: [
        {
          lender_name: 'HDFC Bank',
          lender_pan: 'AAACH1234K',
          principal_paid: 60000,
          interest_paid: 180000,
          is_first_time_buyer: false,
          loan_sanctioned_on: '2023-05-10',
        },
      ],
    });
    declarationService.housing.mockResolvedValueOnce(data);

    const { store } = renderSection();

    expect(declarationService.housing).toHaveBeenCalledWith('2026-27');
    await waitFor(() => {
      expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy();
      expect(screen.getByDisplayValue('HDFC Bank')).toBeTruthy();
    });
    expect(store.getState().tax.sections.housing).toEqual(data);
  });

  it('reads the section from the slice instead of the server when it is already loaded', async () => {
    const store = makeStore();
    store.dispatch({ type: 'tax/setSectionData', payload: { section: 'housing', data: housingWith([rentRow()]) } });

    renderSection({}, store);

    expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy();
    expect(declarationService.housing).not.toHaveBeenCalled();
  });

  describe('landlord PAN, against the threshold the server returns', () => {
    const header = (threshold) => ({
      pan_required_for_rent_over_threshold: true,
      rent_pan_threshold: threshold,
    });

    it('is required when annual rent is above the server threshold', async () => {
      declarationService.housing.mockResolvedValueOnce(housingWith([rentRow()]));
      renderSection({ header: header(100000) });

      await waitFor(() => expect(screen.getByTestId('rent-pan-required')).toBeTruthy());
      expect(screen.getByTestId('rent-pan-0').getAttribute('aria-required')).toBe('true');

      // Saving without the PAN marks the row and sends nothing.
      fireEvent.click(screen.getByTestId('save-rent-btn'));
      await waitFor(() => expect(screen.getByTestId('rent-row-error-0')).toBeTruthy());
      expect(declarationService.saveHouseRent).not.toHaveBeenCalled();
    });

    it('is not required when annual rent is below the server threshold', async () => {
      // 2026-04..2026-06 = 3 x 25000 = 75000
      declarationService.housing.mockResolvedValueOnce(housingWith([rentRow({ to_month: '2026-06' })]));
      declarationService.saveHouseRent.mockResolvedValueOnce(housingWith([rentRow({ to_month: '2026-06' })]));
      renderSection({ header: header(100000) });

      await waitFor(() => expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy());
      expect(screen.queryByTestId('rent-pan-required')).toBeNull();
      fireEvent.click(screen.getByTestId('save-rent-btn'));
      await waitFor(() => expect(declarationService.saveHouseRent).toHaveBeenCalled());
    });

    it('follows a different server threshold, so no constant can pass', async () => {
      // The same 125000 that is above 100000 is below 240000.
      declarationService.housing.mockResolvedValueOnce(housingWith([rentRow()]));
      renderSection({ header: header(240000) });

      await waitFor(() => expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy());
      expect(screen.queryByTestId('rent-pan-required')).toBeNull();
      expect(screen.getByTestId('rent-pan-0').getAttribute('aria-required')).toBe('false');
    });

    it('requires nothing client-side when the server returns no threshold', async () => {
      declarationService.housing.mockResolvedValueOnce(housingWith([rentRow({ amount_per_month: 900000 })]));
      declarationService.saveHouseRent.mockResolvedValueOnce(housingWith([]));
      renderSection({ header: header(null) });

      await waitFor(() => expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy());
      expect(screen.queryByTestId('rent-pan-required')).toBeNull();
      fireEvent.click(screen.getByTestId('save-rent-btn'));
      await waitFor(() => expect(declarationService.saveHouseRent).toHaveBeenCalled());
    });

    it('requires nothing when the rule is off, whatever the threshold', async () => {
      declarationService.housing.mockResolvedValueOnce(housingWith([rentRow()]));
      renderSection({ header: { pan_required_for_rent_over_threshold: false, rent_pan_threshold: 1 } });

      await waitFor(() => expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy());
      expect(screen.queryByTestId('rent-pan-required')).toBeNull();
    });
  });

  it('highlights the row a 400 names, with its message', async () => {
    declarationService.housing.mockResolvedValueOnce(
      housingWith([rentRow({ landlord_name: 'First' }), rentRow({ from_month: '2026-09', to_month: '2026-12', landlord_name: '' })]),
    );
    // The apiClient envelope: rows are numbered from 1 in the message (HousingRules).
    declarationService.saveHouseRent.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Row 2: landlord_name is required',
      fieldErrors: {},
    });

    renderSection();
    await waitFor(() => expect(screen.getByDisplayValue('First')).toBeTruthy());

    fireEvent.click(screen.getByTestId('save-rent-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('rent-row-error-1').textContent).toContain('Row 2: landlord_name is required');
    });
    expect(screen.getByTestId('rent-row-1').getAttribute('data-row-error')).toBe('true');
    expect(screen.getByTestId('rent-row-0').getAttribute('data-row-error')).toBe('false');
    expect(screen.queryByTestId('rent-row-error-0')).toBeNull();
    expect(errorMsg).toHaveBeenCalledWith(expect.objectContaining({ code: 'VALIDATION_FAILED' }));
  });

  it('highlights the let-out property a 400 names', async () => {
    declarationService.housing.mockResolvedValueOnce(
      housingWith([], {
        let_out_properties: [
          { id: 'p1', property_name: 'One', address: '', lines: [] },
          { id: 'p2', property_name: 'Two', address: '', lines: [] },
        ],
      }),
    );
    declarationService.saveLetOut.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Property 2, Line 1: lender_pan is invalid: XX',
    });

    renderSection();
    await waitFor(() => expect(screen.getByDisplayValue('Two')).toBeTruthy());
    fireEvent.click(screen.getByTestId('save-letout-btn'));

    await waitFor(() => expect(screen.getByTestId('letout-row-error-1')).toBeTruthy());
    expect(screen.getByTestId('letout-row-1').getAttribute('data-row-error')).toBe('true');
    expect(screen.getByTestId('letout-row-0').getAttribute('data-row-error')).toBe('false');
  });

  it('a save replaces the section in the slice with the response', async () => {
    declarationService.housing.mockResolvedValueOnce(housingWith([]));
    const saved = housingWith([rentRow({ id: 'r1', landlord_pan: 'ABCDE1234F' })]);
    declarationService.saveHouseRent.mockResolvedValueOnce(saved);

    const { store } = renderSection();
    await waitFor(() => expect(declarationService.housing).toHaveBeenCalled());

    fireEvent.click(screen.getByTestId('add-rent-row-btn'));
    fireEvent.click(screen.getByTestId('save-rent-btn'));

    await waitFor(() => {
      expect(declarationService.saveHouseRent).toHaveBeenCalledWith('2026-27', expect.any(Array));
      expect(store.getState().tax.sections.housing).toEqual(saved);
    });
    expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy();
  });

  it('adds and saves a home loan', async () => {
    declarationService.housing.mockResolvedValueOnce(housingWith([]));
    declarationService.saveHomeLoan.mockResolvedValueOnce(housingWith([]));

    renderSection();
    await waitFor(() => expect(declarationService.housing).toHaveBeenCalled());

    fireEvent.click(screen.getByTestId('add-home-loan-btn'));
    fireEvent.click(screen.getByTestId('save-loan-btn'));

    await waitFor(() => {
      expect(declarationService.saveHomeLoan).toHaveBeenCalledWith('2026-27', expect.any(Array));
    });
  });

  it('keeps and edits lender_name and lender_pan on a LOAN_INTEREST line', async () => {
    const property = {
      id: 'prop-1',
      property_name: 'Property #1',
      address: '221B Baker St',
      net_income_loss: 140000,
      lines: [
        { line_type: 'ANNUAL_RENT', amount: 300000, lender_name: null, lender_pan: null },
        { line_type: 'MUNICIPAL_TAX', amount: 20000, lender_name: null, lender_pan: null },
        { line_type: 'LOAN_INTEREST', amount: 50000, lender_name: 'SBI', lender_pan: 'AAACS1234K' },
      ],
    };
    declarationService.housing.mockResolvedValueOnce(housingWith([], { let_out_properties: [property] }));
    declarationService.saveLetOut.mockResolvedValueOnce(housingWith([], { let_out_properties: [property] }));

    renderSection();

    await waitFor(() => {
      expect(screen.getByDisplayValue('300000')).toBeTruthy();
      expect(screen.getByDisplayValue('20000')).toBeTruthy();
      expect(screen.getByDisplayValue('50000')).toBeTruthy();
      expect(screen.getByTestId('letout-lender-name-0').value).toBe('SBI');
    });

    fireEvent.change(screen.getByTestId('letout-lender-pan-0'), { target: { value: 'aaach1234k' } });
    fireEvent.click(screen.getByTestId('save-letout-btn'));

    await waitFor(() => {
      expect(declarationService.saveLetOut).toHaveBeenCalledWith('2026-27', [
        {
          property_name: 'Property #1',
          address: '221B Baker St',
          lines: [
            { line_type: 'ANNUAL_RENT', amount: 300000 },
            { line_type: 'MUNICIPAL_TAX', amount: 20000 },
            { line_type: 'LOAN_INTEREST', amount: 50000, lender_name: 'SBI', lender_pan: 'AAACH1234K' },
          ],
        },
      ]);
    });
  });

  it('disables every save and add button when not editable', async () => {
    declarationService.housing.mockResolvedValueOnce(housingWith([rentRow()]));
    renderSection({ editable: false });
    await waitFor(() => expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy());

    ['save-rent-btn', 'save-loan-btn', 'save-letout-btn', 'add-rent-row-btn', 'add-home-loan-btn', 'add-letout-btn'].forEach(
      (id) => expect(screen.getByTestId(id).hasAttribute('disabled')).toBe(true),
    );
  });
});
