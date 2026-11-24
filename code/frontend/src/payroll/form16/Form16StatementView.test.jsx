import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Form16StatementView } from './Form16StatementView';

describe('Form16StatementView', () => {
  const sampleStatement = {
    financialYear: '2026-2027',
    assessmentYear: '2027-2028',
    final: true,
    regime: 'NEW',
    generatedAt: '2026-10-15T12:00:00Z',
    deductor: {
      name: 'Infinevo Test Co',
      tan: 'BLRI12345E',
      pan: 'AAACI1234F',
      tdsCircle: 'TDS Circle 1, Bengaluru',
      signatoryName: 'Mohit Birla',
      signatoryDesignation: 'Payroll Officer',
    },
    employee: {
      name: 'Sahmad Khan',
      pan: 'ABCDE1234F',
      designation: 'Senior Software Engineer',
    },
    totalDeducted: 5280.0,
    annualTax: 5280.0,
    balance: 0.0,
    quarters: [
      { quarter: 'Q1', amountDeducted: 0.0 },
      { quarter: 'Q2', amountDeducted: 0.0 },
      { quarter: 'Q3', amountDeducted: 5280.0 },
      { quarter: 'Q4', amountDeducted: 0.0 },
    ],
    breakdown: {
      grossSalary: 82720.0,
      incomeFromSalary: 7520.0,
      grossTotalIncome: 7520.0,
      hraExemption: 0.0,
      standardDeduction: 75000.0,
      professionalTax: 200.0,
      housePropertyIncome: 0.0,
      otherIncome: 0.0,
      chapterVia: 0.0,
      taxableIncome: 7520.0,
      taxBeforeRebate: 5076.92,
      rebate: 0.0,
      surcharge: 0.0,
      cess: 203.08,
      prevEmployerTds: 0.0,
      annualTax: 5280.0,
    },
  };

  it('renders null when statement is empty', () => {
    const { container } = render(<Form16StatementView statement={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders Form 16 header, status, regime, and financial years', () => {
    render(<Form16StatementView statement={sampleStatement} />);

    expect(screen.getByText('FORM NO. 16')).toBeTruthy();
    expect(screen.getByText('FINAL')).toBeTruthy();
    expect(screen.getByText('NEW REGIME')).toBeTruthy();
    expect(screen.getByText('FY 2026-2027')).toBeTruthy();
    expect(screen.getByText('AY 2027-2028')).toBeTruthy();
  });

  it('renders employer and employee details with the full PAN (D-149)', () => {
    render(<Form16StatementView statement={sampleStatement} />);

    expect(screen.getByText('Infinevo Test Co')).toBeTruthy();
    expect(screen.getByText('BLRI12345E')).toBeTruthy();
    expect(screen.getByText('Sahmad Khan')).toBeTruthy();
    // A Form 16 carries the full PAN; masking is for logs only.
    expect(screen.getByText('ABCDE1234F')).toBeTruthy();
    expect(screen.queryByText('ABCDE****F')).toBeNull();
    expect(screen.getByText('Senior Software Engineer')).toBeTruthy();
  });

  it('renders summary statistics and quarterly deductions', () => {
    render(<Form16StatementView statement={sampleStatement} />);

    expect(screen.getByText('Annual Tax Payable')).toBeTruthy();
    expect(screen.getByText('Total TDS Deducted')).toBeTruthy();
    expect(screen.getByText('Q1')).toBeTruthy();
    expect(screen.getByText('Q2')).toBeTruthy();
    expect(screen.getByText('Q3')).toBeTruthy();
    expect(screen.getByText('Q4')).toBeTruthy();
  });

  it('renders a working whose lines follow from each other (D-149)', () => {
    render(<Form16StatementView statement={sampleStatement} />);

    expect(screen.getByText('1. Gross Salary')).toBeTruthy();
    expect(screen.getByText('5. Income from Salary (1 - 2 - 3 - 4)')).toBeTruthy();
    expect(screen.getByText('8. Gross Total Income (5 + 6 + 7)')).toBeTruthy();
    expect(screen.getByText('3. Less: Standard Deduction (u/s 16(ia))')).toBeTruthy();
    expect(screen.getByText('4. Less: Professional Tax (u/s 16(iii))')).toBeTruthy();
    expect(screen.getByText('10. Total Taxable Income (8 - 9, rounded)')).toBeTruthy();
    expect(screen.getByText('16. Total Annual Tax Payable')).toBeTruthy();
  });

  it('renders officer override alert when breakdownNote is OFFICER_OVERRIDE', () => {
    const overrideStatement = {
      ...sampleStatement,
      breakdownNote: 'OFFICER_OVERRIDE',
    };
    render(<Form16StatementView statement={overrideStatement} />);

    expect(screen.getByText('Officer Adjustment')).toBeTruthy();
  });

  it('says there is no computation, not that an officer adjusted it (D-149)', () => {
    render(
      <Form16StatementView
        statement={{ ...sampleStatement, breakdown: null, breakdownNote: 'NO_COMPUTATION' }}
      />
    );

    expect(screen.getByText('No Tax Computation Yet')).toBeTruthy();
    expect(screen.queryByText('Officer Adjustment')).toBeNull();
  });

  it('prints a house property loss as (-), not with a bare minus sign (D-149)', () => {
    render(
      <Form16StatementView
        statement={{ ...sampleStatement, breakdown: { ...sampleStatement.breakdown, housePropertyIncome: -7600 } }}
      />
    );

    expect(screen.getByText('(-) ₹7,600.00')).toBeTruthy();
  });

  it('invokes window.print when print button is clicked', () => {
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});
    render(<Form16StatementView statement={sampleStatement} />);

    const printButton = screen.getByText('Print Statement');
    fireEvent.click(printButton);

    expect(printSpy).toHaveBeenCalled();
    printSpy.mockRestore();
  });
});
