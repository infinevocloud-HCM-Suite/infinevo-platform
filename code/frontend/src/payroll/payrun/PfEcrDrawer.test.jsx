import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { PfEcrDrawer } from './PfEcrDrawer.jsx';
import { pfEcrService } from './pfEcrService.js';
import * as bankFileModule from './bankFileService.js';

vi.mock('./pfEcrService.js', () => ({
  pfEcrService: {
    getPreview: vi.fn(),
    downloadFile: vi.fn(),
  },
}));

describe('PfEcrDrawer', () => {
  const payrunId = 'run-123';
  const runPeriod = '2026-10';
  const onClose = vi.fn();

  const mockPreviewData = {
    payrunId,
    period: '2026-10',
    establishmentCode: 'DLCPM1234567000',
    totalMembers: 2,
    totalGrossWages: 79000,
    totalEpfWages: 29000,
    totalEpsWages: 29000,
    totalEdliWages: 29000,
    totalEeShare: 3480,
    totalEpsShare: 2416,
    totalErShare: 1064,
    totalAdminCharges: 145,
    totalEdliCharges: 145,
    grandTotalChallan: 7250,
    warnings: [
      {
        employeeId: 'emp-2',
        employeeNumber: 'EMP-002',
        employeeName: 'Bob Smith',
        warningCode: 'MISSING_UAN',
        message: 'Active PF contributor has no valid 12-digit UAN recorded in statutory profile',
      },
    ],
    rows: [
      {
        uan: '100123456789',
        memberName: 'ASHA RAO',
        grossWages: 65000,
        epfWages: 15000,
        epsWages: 15000,
        edliWages: 15000,
        eeShare: 1800,
        epsShare: 1250,
        erShare: 550,
        ncpDays: 0,
        refundOfAdvances: 0,
      },
      {
        uan: '',
        memberName: 'BOB SMITH',
        grossWages: 14000,
        epfWages: 14000,
        epsWages: 14000,
        edliWages: 14000,
        eeShare: 1680,
        epsShare: 1166,
        erShare: 514,
        ncpDays: 2,
        refundOfAdvances: 0,
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(bankFileModule, 'saveBlob').mockImplementation(() => {});
  });

  it('renders summary cards, warnings, and table rows on load', async () => {
    pfEcrService.getPreview.mockResolvedValueOnce(mockPreviewData);

    render(
      <PfEcrDrawer
        open={true}
        payrunId={payrunId}
        runPeriod={runPeriod}
        onClose={onClose}
      />
    );

    // Summary cards
    await waitFor(() => {
      expect(screen.getByText('DLCPM1234567000')).toBeDefined();
    });

    expect(screen.getByText('Total Members')).toBeDefined();
    expect(screen.getAllByText('2').length).toBeGreaterThanOrEqual(1);

    // Warnings
    expect(screen.getByText(/Pre-filing Validation Warnings/)).toBeDefined();
    expect(screen.getByText(/Active PF contributor has no valid 12-digit UAN/)).toBeDefined();

    // Table rows
    expect(screen.getByText('ASHA RAO')).toBeDefined();
    expect(screen.getByText('100123456789')).toBeDefined();
    expect(screen.getByText('BOB SMITH')).toBeDefined();
  });

  it('triggers TXT download when clicking Download ECR File button', async () => {
    pfEcrService.getPreview.mockResolvedValueOnce(mockPreviewData);
    pfEcrService.downloadFile.mockResolvedValueOnce(new Blob(['fake-txt']));

    render(
      <PfEcrDrawer
        open={true}
        payrunId={payrunId}
        runPeriod={runPeriod}
        onClose={onClose}
      />
    );

    const txtBtn = await screen.findByTestId('ecr-download-txt-btn');
    fireEvent.click(txtBtn);

    await waitFor(() => {
      expect(pfEcrService.downloadFile).toHaveBeenCalledWith(payrunId, 'TXT');
      expect(bankFileModule.saveBlob).toHaveBeenCalledWith(
        expect.any(Blob),
        'ECR_DLCPM1234567000_2026_10.txt'
      );
    });
  });

  it('triggers XLSX export when clicking Export Excel button', async () => {
    pfEcrService.getPreview.mockResolvedValueOnce(mockPreviewData);
    pfEcrService.downloadFile.mockResolvedValueOnce(new Blob(['fake-xlsx']));

    render(
      <PfEcrDrawer
        open={true}
        payrunId={payrunId}
        runPeriod={runPeriod}
        onClose={onClose}
      />
    );

    const xlsxBtn = await screen.findByTestId('ecr-download-xlsx-btn');
    fireEvent.click(xlsxBtn);

    await waitFor(() => {
      expect(pfEcrService.downloadFile).toHaveBeenCalledWith(payrunId, 'XLSX');
      expect(bankFileModule.saveBlob).toHaveBeenCalledWith(
        expect.any(Blob),
        'ECR_DLCPM1234567000_2026_10.xlsx'
      );
    });
  });
});

