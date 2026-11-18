import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ChallanList } from './ChallanList';

describe('ChallanList component (W-89.4)', () => {
  it('renders empty placeholder and Record Challan button when list is empty', () => {
    const onRecord = vi.fn();
    render(<ChallanList challans={[]} loading={false} onRecordClick={onRecord} />);

    expect(screen.getByText('No TDS challans recorded for this quarter')).toBeTruthy();
    const btn = screen.getByRole('button', { name: /Record Challan/i });
    expect(btn).toBeTruthy();
    fireEvent.click(btn);
    expect(onRecord).toHaveBeenCalledTimes(1);
  });

  it('renders challan details and allocation status tags', () => {
    const mockChallans = [
      {
        id: 'ch-1',
        bsrCode: '0510304',
        challanSerialNo: '00123',
        tenderDate: '2026-05-07',
        minorHead: '200',
        taxAmount: 10000,
        cessAmount: 400,
        totalAmount: 10400,
        allocatedAmount: 10400,
        unallocatedAmount: 0,
        allocationStatus: 'FULLY_ALLOCATED',
        isFiled: false,
      },
      {
        id: 'ch-2',
        bsrCode: '0210045',
        challanSerialNo: '00456',
        tenderDate: '2026-06-07',
        minorHead: '400',
        taxAmount: 5000,
        cessAmount: 200,
        totalAmount: 5200,
        allocatedAmount: 2000,
        unallocatedAmount: 3200,
        allocationStatus: 'PARTIALLY_ALLOCATED',
        isFiled: true,
      },
    ];

    render(<ChallanList challans={mockChallans} loading={false} />);

    expect(screen.getByText('0510304')).toBeTruthy();
    expect(screen.getByText('00123')).toBeTruthy();
    expect(screen.getByText('Fully Allocated')).toBeTruthy();

    expect(screen.getByText('0210045')).toBeTruthy();
    expect(screen.getByText('00456')).toBeTruthy();
    expect(screen.getByText(/Unallocated/i)).toBeTruthy();
    expect(screen.getByText('Filed')).toBeTruthy();
  });
});
