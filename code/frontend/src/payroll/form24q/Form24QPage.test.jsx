import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import dayjs from 'dayjs';
import * as useCanModule from '@shell/screens';
import { Form24QPage, quarterOf } from './Form24QPage';
import { form24QService, saveFile } from './form24QService';

vi.mock('./form24QService', () => ({
  form24QService: {
    listChallans: vi.fn(),
    getAnnexure1Preview: vi.fn(),
    returns: vi.fn(),
    exportAnnexure1: vi.fn(),
    exportAnnexure2: vi.fn(),
    markFiled: vi.fn(),
    reopen: vi.fn(),
    deleteChallan: vi.fn(),
    candidates: vi.fn(),
    allocate: vi.fn(),
  },
  saveFile: vi.fn(),
}));

const challan = {
  id: 'c1',
  financialYear: '2026-2027',
  quarter: 'Q3',
  bsrCode: '0510304',
  challanSerialNo: '12',
  tenderDate: '2026-11-07',
  allocatableAmount: '1500.00',
  totalAmount: '1500.00',
  allocatedAmount: '1000.00',
  allocationStatus: 'PARTIAL',
  filed: false,
};
const preview = (problems) => ({
  totalTdsDeducted: '1500.00',
  totalTdsDeposited: '1000.00',
  unallocatedTds: '500.00',
  missingPanCount: 1,
  portalFileProblems: problems,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <Form24QPage />
    </MemoryRouter>,
  );

describe('Form24QPage (W-89)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    form24QService.listChallans.mockResolvedValue([challan]);
    form24QService.returns.mockResolvedValue([]);
  });

  it('opens on the quarter today is in', () => {
    expect(quarterOf(dayjs('2026-05-10'))).toBe('Q1');
    expect(quarterOf(dayjs('2026-11-25'))).toBe('Q3');
    expect(quarterOf(dayjs('2027-02-01'))).toBe('Q4');
  });

  it('shows the challan status and every problem, and keeps the portal file off until they are fixed', async () => {
    form24QService.getAnnexure1Preview.mockResolvedValue(preview(['Allocate the remaining 500.00 of TDS']));
    renderPage();
    await waitFor(() => expect(screen.getByTestId('status-c1').textContent).toBe('Partly allocated'));
    expect(screen.getByTestId('portal-problems').textContent).toContain('Allocate the remaining 500.00');
    expect(screen.getByTestId('btn-txt').disabled).toBe(true);
    expect(screen.getByText('Not yet covered')).toBeDefined();
  });

  it('downloads the Form 138 file when nothing is in the way', async () => {
    form24QService.getAnnexure1Preview.mockResolvedValue(preview([]));
    const blob = new Blob(['FH']);
    form24QService.exportAnnexure1.mockResolvedValue(blob);
    renderPage();
    await waitFor(() => expect(screen.getByTestId('btn-txt').disabled).toBe(false));
    fireEvent.click(screen.getByTestId('btn-txt'));
    await waitFor(() => expect(saveFile).toHaveBeenCalled());
    expect(saveFile.mock.calls[0][1]).toMatch(/_Form138\.txt$/);
  });

  it('a filed quarter shows its token and locks recording', async () => {
    form24QService.getAnnexure1Preview.mockResolvedValue(preview([]));
    form24QService.returns.mockResolvedValue([
      { quarter: quarterOf(), tokenNumber: '123456789012345', filedOn: '2026-10-30' },
    ]);
    renderPage();
    await waitFor(() => expect(screen.getByTestId('quarter-filed').textContent).toContain('123456789012345'));
    expect(screen.getByTestId('btn-add-challan').disabled).toBe(true);
    expect(screen.queryByTestId('btn-mark-filed')).toBeNull();
  });

  it('marking filed needs the 15-digit token', async () => {
    form24QService.getAnnexure1Preview.mockResolvedValue(preview([]));
    form24QService.markFiled.mockResolvedValue({});
    renderPage();
    await waitFor(() => expect(screen.getByTestId('btn-mark-filed')).toBeDefined());
    fireEvent.click(screen.getByTestId('btn-mark-filed'));
    const confirm = await screen.findByTestId('btn-confirm-filed');
    expect(confirm.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Token number'), { target: { value: '1234-5678 9012345' } });
    expect(confirm.disabled).toBe(false);
    fireEvent.click(confirm);
    await waitFor(() =>
      expect(form24QService.markFiled).toHaveBeenCalledWith(
        expect.any(String),
        quarterOf(),
        '123456789012345',
        expect.any(String),
      ),
    );
  });

  it('hides the officer actions from someone who can only view', async () => {
    useCanModule.useCan.mockReturnValue(false);
    form24QService.getAnnexure1Preview.mockResolvedValue(preview(['Fill in: Deductor email']));
    renderPage();
    await waitFor(() => expect(screen.getByTestId('status-c1')).toBeDefined());
    expect(screen.queryByTestId('btn-add-challan')).toBeNull();
    expect(screen.queryByTestId('btn-allocate-c1')).toBeNull();
    // No empty column and no link to a settings screen they cannot open.
    expect(screen.queryByText('Actions')).toBeNull();
    expect(screen.queryByText('Deductor details')).toBeNull();
  });
});
