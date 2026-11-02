import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { StatutoryContributionsTable, shownInStructure } from './StatutoryContributionsTable.jsx';

const LINES = [
  {
    id: 's1',
    componentCode: 'EPF_EMPLOYER',
    share: 'EMPLOYER',
    wageBase: '15000.0000',
    rate: '3.6700',
    monthlyAmount: '550.5000',
    annualAmount: '6606.0000',
    includedInCtc: true,
  },
  {
    id: 's2',
    componentCode: 'EPS_EMPLOYER',
    share: 'EMPLOYER',
    wageBase: '15000.0000',
    rate: '8.3300',
    monthlyAmount: '1249.5000',
    annualAmount: '14994.0000',
    includedInCtc: true,
  },
  {
    id: 's3',
    componentCode: 'EDLI',
    share: 'EMPLOYER',
    wageBase: '15000.0000',
    rate: '0.5000',
    monthlyAmount: '75.0000',
    annualAmount: '900.0000',
    includedInCtc: false,
  },
];

describe('StatutoryContributionsTable (W-75.2 §7)', () => {
  it('renders EPF_EMPLOYER, EPS_EMPLOYER and EDLI rows with names, rates, amounts and the In CTC tag', () => {
    render(<StatutoryContributionsTable statutory={LINES} />);

    const table = document.getElementById('table-salary-statutory');
    expect(table).toBeTruthy();
    const rows = table.querySelectorAll('tbody tr.ant-table-row');
    expect(rows).toHaveLength(3);

    const epf = within(rows[0]);
    expect(epf.getByText('Employer PF')).toBeDefined();
    expect(epf.getByText('Employer')).toBeDefined();
    expect(epf.getByText('3.67% of ₹ 15,000.00')).toBeDefined();
    expect(epf.getByText('₹ 550.50')).toBeDefined();
    expect(epf.getByText('₹ 6,606.00')).toBeDefined();
    expect(epf.getByText('In CTC')).toBeDefined();

    const eps = within(rows[1]);
    expect(eps.getByText('Employer pension (EPS)')).toBeDefined();
    expect(eps.getByText('8.33% of ₹ 15,000.00')).toBeDefined();
    expect(eps.getByText('₹ 14,994.00')).toBeDefined();

    const edli = within(rows[2]);
    expect(edli.getByText('EDLI')).toBeDefined();
    expect(edli.getByText('0.5% of ₹ 15,000.00')).toBeDefined();
    expect(edli.getByText('Not in CTC')).toBeDefined();

    // Names, not codes.
    expect(screen.queryByText('EPF_EMPLOYER')).toBeNull();
    expect(screen.queryByText('EPS_EMPLOYER')).toBeNull();
  });

  it('hides a zero line such as EPS for an employee not in EPS (W-76.2)', () => {
    const zeroEps = { ...LINES[1], id: 'zero-eps', monthlyAmount: '0.0000', annualAmount: '0.0000' };
    render(<StatutoryContributionsTable statutory={[LINES[0], zeroEps]} />);

    const rows = document.getElementById('table-salary-statutory').querySelectorAll('tbody tr.ant-table-row');
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('Employer PF')).toBeDefined();
  });

  it('hides EDLI and admin outside CTC when the company keeps them out of the structure (W-76.2)', () => {
    const admin = { ...LINES[2], id: 's4', componentCode: 'EPF_ADMIN' };
    const epf = { includeEmployerInStructure: true, includeEdliInStructure: false, includeAdminInStructure: false };
    render(<StatutoryContributionsTable statutory={[...LINES, admin]} epfSetting={epf} />);

    const rows = document.getElementById('table-salary-statutory').querySelectorAll('tbody tr.ant-table-row');
    expect(rows).toHaveLength(2);
    expect(screen.queryByText('EDLI')).toBeNull();
  });

  it('shows the empty state when the version has no statutory lines', () => {
    render(<StatutoryContributionsTable statutory={[]} />);

    expect(screen.getByText('No statutory contributions on this version')).toBeDefined();
  });
});

describe('shownInStructure (W-76.2)', () => {
  const edliOut = { componentCode: 'EDLI', share: 'EMPLOYER', includedInCtc: false };

  it('follows the switch for a line outside CTC', () => {
    expect(shownInStructure(edliOut, { epf: { includeEdliInStructure: false } })).toBe(false);
    expect(shownInStructure(edliOut, { epf: { includeEdliInStructure: true } })).toBe(true);
  });

  it('EDLI with its switch off is hidden even when counted in CTC (W-76.3)', () => {
    expect(shownInStructure({ ...edliOut, includedInCtc: true }, { epf: { includeEdliInStructure: false } })).toBe(false);
  });

  it('always shows employer PF counted in CTC', () => {
    const erPf = { componentCode: 'EPF_EMPLOYER', includedInCtc: true };
    expect(shownInStructure(erPf, { epf: { includeEmployerInStructure: false } })).toBe(true);
  });

  it('always shows employee lines, and every line when settings are not loaded', () => {
    expect(shownInStructure({ componentCode: 'EPF_EMPLOYEE', includedInCtc: false }, { epf: {} })).toBe(true);
    expect(shownInStructure(edliOut, {})).toBe(true);
  });

  it('employer ESI follows the ESI structure switch', () => {
    const esiOut = { componentCode: 'ESI_EMPLOYER', includedInCtc: false };
    expect(shownInStructure(esiOut, { esi: { includeInStructure: false } })).toBe(false);
    expect(shownInStructure(esiOut, { esi: { includeInStructure: true } })).toBe(true);
  });
});
