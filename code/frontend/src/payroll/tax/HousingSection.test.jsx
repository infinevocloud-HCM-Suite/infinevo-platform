import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { HousingSection } from './HousingSection';
import { declarationService } from './declarationService';

vi.mock('./declarationService', () => ({
  declarationService: {
    housing: vi.fn(),
    saveHouseRent: vi.fn(),
    saveHomeLoan: vi.fn(),
    saveLetOut: vi.fn(),
  },
}));

describe('HousingSection', () => {
  const sampleHousing = {
    house_rent: [
      {
        from_month: '2026-04',
        to_month: '2026-08', // 5 months * 25,000 = 125,000 > 100,000
        address: 'Flat 101, Palm Heights, Mumbai',
        landlord_name: 'Rajesh Sharma',
        landlord_pan: 'ABCDE1234F',
        is_metro: true,
        amount_per_month: 25000,
      },
    ],
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
    let_out_properties: [],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders existing housing declarations on load', async () => {
    declarationService.housing.mockResolvedValueOnce(sampleHousing);

    render(<HousingSection fy="2026-27" editable={true} />);

    expect(declarationService.housing).toHaveBeenCalledWith('2026-27');

    await waitFor(() => {
      expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy();
      expect(screen.getByDisplayValue('HDFC Bank')).toBeTruthy();
    });
  });

  it('computes rent threshold warning using actual month span (from_month..to_month)', async () => {
    // 3 months * 25,000 = 75,000 <= 100,000 -> should NOT warn
    declarationService.housing.mockResolvedValueOnce({
      house_rent: [
        {
          from_month: '2026-04',
          to_month: '2026-06',
          address: 'Mumbai',
          landlord_name: 'Rajesh Sharma',
          landlord_pan: '',
          is_metro: true,
          amount_per_month: 25000,
        },
      ],
      home_loans: [],
      let_out_properties: [],
    });

    const { unmount } = render(
      <HousingSection
        fy="2026-27"
        editable={true}
        header={{ pan_required_for_rent_over_threshold: true, rent_pan_threshold: 100000 }}
      />,
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue('Rajesh Sharma')).toBeTruthy();
    });
    expect(screen.queryByText('Landlord PAN Required')).toBeNull();
    unmount();

    // 5 months * 25,000 = 125,000 > 100,000 -> SHOULD warn
    declarationService.housing.mockResolvedValueOnce(sampleHousing);
    render(
      <HousingSection
        fy="2026-27"
        editable={true}
        header={{ pan_required_for_rent_over_threshold: true, rent_pan_threshold: 100000 }}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('Landlord PAN Required')).toBeTruthy();
    });
  });

  it('adds and saves house rent row', async () => {
    declarationService.housing.mockResolvedValueOnce({ house_rent: [], home_loans: [], let_out_properties: [] });
    declarationService.saveHouseRent.mockResolvedValueOnce({ success: true });

    render(<HousingSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(declarationService.housing).toHaveBeenCalled();
    });

    const addBtn = screen.getByTestId('add-rent-row-btn');
    fireEvent.click(addBtn);

    const saveRentBtn = screen.getByTestId('save-rent-btn');
    fireEvent.click(saveRentBtn);

    await waitFor(() => {
      expect(declarationService.saveHouseRent).toHaveBeenCalledWith('2026-27', expect.any(Array));
    });
  });

  it('adds and saves home loan', async () => {
    declarationService.housing.mockResolvedValueOnce({ house_rent: [], home_loans: [], let_out_properties: [] });
    declarationService.saveHomeLoan.mockResolvedValueOnce({ success: true });

    render(<HousingSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(declarationService.housing).toHaveBeenCalled();
    });

    const addLoanBtn = screen.getByTestId('add-home-loan-btn');
    fireEvent.click(addLoanBtn);

    const saveLoanBtn = screen.getByTestId('save-loan-btn');
    fireEvent.click(saveLoanBtn);

    await waitFor(() => {
      expect(declarationService.saveHomeLoan).toHaveBeenCalledWith('2026-27', expect.any(Array));
    });
  });

  it('loads let-out properties with backend line types and displays amounts without resetting to 0 (B-6 fix)', async () => {
    declarationService.housing.mockResolvedValueOnce({
      house_rent: [],
      home_loans: [],
      let_out_properties: [
        {
          id: 'prop-1',
          property_name: 'Property #1',
          address: '221B Baker St',
          net_income_loss: 140000,
          lines: [
            { line_type: 'ANNUAL_RENT', amount: 300000 },
            { line_type: 'MUNICIPAL_TAX', amount: 20000 },
            { line_type: 'LOAN_INTEREST', amount: 50000 },
          ],
        },
      ],
    });

    render(<HousingSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByDisplayValue('300000')).toBeTruthy();
      expect(screen.getByDisplayValue('20000')).toBeTruthy();
      expect(screen.getByDisplayValue('50000')).toBeTruthy();
    });
  });

  it('saves let-out properties using backend enums ANNUAL_RENT, MUNICIPAL_TAX, LOAN_INTEREST (B-5 fix)', async () => {
    declarationService.housing.mockResolvedValueOnce({
      house_rent: [],
      home_loans: [],
      let_out_properties: [
        {
          id: 'prop-1',
          property_name: 'Property #1',
          address: '221B Baker St',
          net_income_loss: 140000,
          lines: [
            { line_type: 'ANNUAL_RENT', amount: 300000 },
            { line_type: 'MUNICIPAL_TAX', amount: 20000 },
            { line_type: 'LOAN_INTEREST', amount: 50000 },
          ],
        },
      ],
    });
    declarationService.saveLetOut.mockResolvedValueOnce({
      let_out_properties: [
        {
          id: 'prop-1',
          property_name: 'Property #1',
          address: '221B Baker St',
          net_income_loss: 140000,
          lines: [
            { line_type: 'ANNUAL_RENT', amount: 300000 },
            { line_type: 'MUNICIPAL_TAX', amount: 20000 },
            { line_type: 'LOAN_INTEREST', amount: 50000 },
          ],
        },
      ],
    });

    render(<HousingSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByDisplayValue('300000')).toBeTruthy();
    });

    const saveLetOutBtn = screen.getByTestId('save-letout-btn');
    fireEvent.click(saveLetOutBtn);

    await waitFor(() => {
      expect(declarationService.saveLetOut).toHaveBeenCalledWith('2026-27', [
        {
          property_name: 'Property #1',
          address: '221B Baker St',
          lines: [
            { line_type: 'ANNUAL_RENT', amount: 300000 },
            { line_type: 'MUNICIPAL_TAX', amount: 20000 },
            { line_type: 'LOAN_INTEREST', amount: 50000 },
          ],
        },
      ]);
    });
  });
});
