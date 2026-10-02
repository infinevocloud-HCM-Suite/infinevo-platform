import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { DeductionsSection } from './DeductionsSection';
import { declarationService } from './declarationService';
import taxReducer from './taxSlice';
import { errorMsg } from '@shared/ui/msgHelper.js';

vi.mock('./declarationService', () => ({
  declarationService: {
    items: vi.fn(),
    deductions: vi.fn(),
    save6a: vi.fn(),
    savePreTax: vi.fn(),
    savePrevEmployment: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

// The shape of GET .../section6a-items (Section6AItemResponse).
const ITEM_80C = {
  id: 'item-80c',
  section_code: '80C',
  category: 'INVESTMENT',
  name: 'Investments under Section 80C',
  max_limit: 150000,
  category_group_code: '80C_GROUP',
};
const ITEM_80CCD2 = {
  id: 'item-80ccd2',
  section_code: '80CCD(2)',
  category: 'INVESTMENT',
  name: 'NPS, employer contribution',
  max_limit: null,
  category_group_code: null,
};
const ITEM_80D = {
  id: 'item-80d',
  section_code: '80D',
  category: 'HEALTH_INSURANCE',
  name: 'Health insurance premium',
  max_limit: 25000,
  category_group_code: '80D_GROUP',
};
const OLD_ITEMS = [ITEM_80C, ITEM_80CCD2, ITEM_80D];
const NEW_ITEMS = [ITEM_80CCD2];

const sampleDeductions = {
  section6a: [
    { id: 'l1', section6a_item_id: 'item-80c', section_code: '80C', name: ITEM_80C.name, description: 'LIC policy', amount: 50000 },
    { id: 'l2', section6a_item_id: 'item-80c', section_code: '80C', name: ITEM_80C.name, description: 'PPF', amount: 40000 },
  ],
  pre_tax_deductions: [{ id: 'p1', kind: 'VPF', amount: 10000 }],
  previous_employment: [
    { id: 'e1', kind: 'INCOME', amount: 250000, employer_name: 'Previous Tech Corp', employer_tan: 'PUNE12345A', entered_by: 'EMPLOYEE' },
    { id: 'e2', kind: 'EMPLOYEE_PF', amount: 9000, employer_name: 'Second Corp', employer_tan: 'MUMB12345B', entered_by: 'EMPLOYEE' },
  ],
};

function makeStore() {
  return configureStore({ reducer: { tax: taxReducer } });
}

function renderSection(props = {}, store = makeStore()) {
  const ui = (p) => (
    <Provider store={store}>
      <DeductionsSection fy="2026-27" editable={true} regime="OLD" {...props} {...p} />
    </Provider>
  );
  const utils = render(ui());
  return { ...utils, store, rerenderWith: (p) => utils.rerender(ui(p)) };
}

describe('DeductionsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('groups the item picker by category and shows the server max_limit beside it', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);

    renderSection();

    expect(declarationService.items).toHaveBeenCalledWith('2026-27');
    expect(declarationService.deductions).toHaveBeenCalledWith('2026-27');

    // The limit is the server's number, beside the picker of each row.
    await waitFor(() => expect(screen.getByTestId('item-cap-0').textContent).toContain('1,50,000'));

    fireEvent.mouseDown(within(screen.getByTestId('input-6a-item-0')).getByRole('combobox'));
    await waitFor(() => {
      const groups = [...document.querySelectorAll('.ant-select-item-group')].map((g) => g.textContent);
      expect(groups).toEqual(['Investment', 'Health Insurance']);
    });
    fireEvent.click(screen.getByText('Health insurance premium (80D)'));
    await waitFor(() => expect(screen.getByTestId('item-cap-0').textContent).toContain('25,000'));
  });

  it('keeps two rows for the same item and sends both', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    declarationService.save6a.mockResolvedValueOnce(sampleDeductions);

    renderSection();
    await waitFor(() => expect(screen.getByTestId('input-6a-desc-1').value).toBe('PPF'));
    expect(screen.getByTestId('input-6a-desc-0').value).toBe('LIC policy');

    fireEvent.change(screen.getByTestId('input-6a-desc-1'), { target: { value: 'PPF account' } });
    fireEvent.click(screen.getByTestId('save-6a-btn'));

    await waitFor(() => {
      expect(declarationService.save6a).toHaveBeenCalledWith('2026-27', [
        { section6a_item_id: 'item-80c', description: 'LIC policy', amount: 50000 },
        { section6a_item_id: 'item-80c', description: 'PPF account', amount: 40000 },
      ]);
    });
  });

  it('refuses to send an empty description and marks the field', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);

    renderSection();
    await waitFor(() => expect(screen.getByTestId('input-6a-desc-0').value).toBe('LIC policy'));

    fireEvent.change(screen.getByTestId('input-6a-desc-0'), { target: { value: '   ' } });
    fireEvent.click(screen.getByTestId('save-6a-btn'));

    await waitFor(() => expect(screen.getByTestId('error-6a-desc-0').textContent).toBe('Description is required'));
    expect(screen.getByTestId('input-6a-desc-0').className).toContain('ant-input-status-error');
    expect(declarationService.save6a).not.toHaveBeenCalled();
  });

  it('marks the rows a 400 names when an item is over its limit', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    declarationService.save6a.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Total declared amount for section 80C (200000) exceeds statutory limit (150000)',
    });

    renderSection();
    await waitFor(() => expect(screen.getByTestId('input-6a-desc-0').value).toBe('LIC policy'));
    fireEvent.change(screen.getByTestId('input-6a-amount-1'), { target: { value: '150000' } });
    fireEvent.click(screen.getByTestId('save-6a-btn'));

    await waitFor(() => expect(screen.getByTestId('error-6a-row-1').textContent).toContain('exceeds statutory limit'));
    expect(screen.getByTestId('6a-row-0').getAttribute('data-row-error')).toBe('true');
    expect(errorMsg).toHaveBeenCalled();
  });

  it('re-reads the catalogue on a regime switch and lets the user remove rows no longer allowed', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS).mockResolvedValueOnce(NEW_ITEMS);
    declarationService.deductions.mockResolvedValueOnce({
      ...sampleDeductions,
      section6a: [
        sampleDeductions.section6a[0],
        { id: 'l3', section6a_item_id: 'item-80ccd2', section_code: '80CCD(2)', name: ITEM_80CCD2.name, description: 'Employer NPS', amount: 30000 },
      ],
    });
    declarationService.save6a.mockResolvedValueOnce({ ...sampleDeductions, section6a: [] });

    const { rerenderWith } = renderSection({ regime: 'OLD' });
    await waitFor(() => expect(screen.getByTestId('input-6a-desc-0').value).toBe('LIC policy'));
    expect(screen.queryByTestId('disallowed-6a-0')).toBeNull();

    rerenderWith({ regime: 'NEW' });

    await waitFor(() => expect(declarationService.items).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByTestId('disallowed-6a-0')).toBeTruthy());
    expect(screen.getByTestId('disallowed-6a-banner')).toBeTruthy();
    expect(screen.queryByTestId('disallowed-6a-1')).toBeNull();

    // Saving with the row still there sends nothing and says why.
    fireEvent.click(screen.getByTestId('save-6a-btn'));
    await waitFor(() => expect(screen.getByTestId('error-6a-row-0').textContent).toContain('NEW regime'));
    expect(declarationService.save6a).not.toHaveBeenCalled();

    // Removing it lets the save through with only the allowed row.
    fireEvent.click(screen.getByTestId('delete-6a-row-0'));
    fireEvent.click(screen.getByTestId('save-6a-btn'));
    await waitFor(() => {
      expect(declarationService.save6a).toHaveBeenCalledWith('2026-27', [
        { section6a_item_id: 'item-80ccd2', description: 'Employer NPS', amount: 30000 },
      ]);
    });
  });

  it('saves pre-tax rows and replaces the section with the response', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    const saved = { ...sampleDeductions, pre_tax_deductions: [{ id: 'p9', kind: 'VPF', amount: 12000 }] };
    declarationService.savePreTax.mockResolvedValueOnce(saved);

    const { store } = renderSection();
    await waitFor(() => expect(screen.getByTestId('input-pretax-amount-0').value).toBe('10000'));

    fireEvent.change(screen.getByTestId('input-pretax-amount-0'), { target: { value: '12000' } });
    fireEvent.click(screen.getByTestId('save-pretax-btn'));

    await waitFor(() => {
      expect(declarationService.savePreTax).toHaveBeenCalledWith('2026-27', [{ kind: 'VPF', amount: 12000 }]);
      expect(store.getState().tax.sections.deductions).toEqual(saved);
    });
  });

  it('keeps each previous employer on its own row', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    declarationService.savePrevEmployment.mockResolvedValueOnce(sampleDeductions);

    renderSection();
    await waitFor(() => expect(screen.getByTestId('input-prevemp-employer-1').value).toBe('Second Corp'));

    fireEvent.click(screen.getByTestId('save-prevemp-btn'));
    await waitFor(() => {
      expect(declarationService.savePrevEmployment).toHaveBeenCalledWith('2026-27', [
        { kind: 'INCOME', amount: 250000, employer_name: 'Previous Tech Corp', employer_tan: 'PUNE12345A' },
        { kind: 'EMPLOYEE_PF', amount: 9000, employer_name: 'Second Corp', employer_tan: 'MUMB12345B' },
      ]);
    });
  });

  it('shows officer-entered rows read-only, leaves them out of the save, and shows OFFICER_ENTERED', async () => {
    declarationService.items.mockResolvedValueOnce(OLD_ITEMS);
    declarationService.deductions.mockResolvedValueOnce({
      ...sampleDeductions,
      previous_employment: [
        sampleDeductions.previous_employment[0],
        { id: 'e3', kind: 'INCOME_TAX_DEDUCTED', amount: 20000, employer_name: 'Previous Tech Corp', employer_tan: 'PUNE12345A', entered_by: 'OFFICER' },
      ],
    });
    declarationService.savePrevEmployment.mockRejectedValueOnce({
      status: 409,
      code: 'OFFICER_ENTERED',
      message: 'Previous employment details entered by payroll officer cannot be edited in self-service',
    });

    renderSection();

    await waitFor(() => expect(screen.getByTestId('officer-badge-1')).toBeTruthy());
    expect(screen.getByTestId('input-prevemp-amount-1').hasAttribute('disabled')).toBe(true);
    expect(screen.getByTestId('input-prevemp-employer-1').hasAttribute('disabled')).toBe(true);
    expect(screen.getByTestId('input-prevemp-amount-0').hasAttribute('disabled')).toBe(false);

    fireEvent.click(screen.getByTestId('save-prevemp-btn'));

    await waitFor(() => {
      expect(declarationService.savePrevEmployment).toHaveBeenCalledWith('2026-27', [
        { kind: 'INCOME', amount: 250000, employer_name: 'Previous Tech Corp', employer_tan: 'PUNE12345A' },
      ]);
    });
    await waitFor(() => {
      expect(screen.getByTestId('officer-entered-message').textContent).toContain(
        'entered by payroll officer cannot be edited',
      );
    });
    expect(errorMsg).toHaveBeenCalledWith(expect.objectContaining({ code: 'OFFICER_ENTERED' }));
  });
});
