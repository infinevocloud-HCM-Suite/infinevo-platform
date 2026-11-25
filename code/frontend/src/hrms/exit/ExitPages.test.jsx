import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ResignationPage } from './ResignationPage.jsx';
import { ExitDashboard } from './ExitDashboard.jsx';
import { ClearanceDrawer } from './ClearanceDrawer.jsx';
import { exitService } from './exitService.js';

vi.mock('./exitService.js', () => ({
  exitService: {
    myExits: vi.fn(),
    submitResignation: vi.fn(),
    withdrawMyExit: vi.fn(),
    listExits: vi.fn(),
    approveExit: vi.fn(),
    signOffDepartment: vi.fn(),
    updateClearanceItem: vi.fn(),
    handOffToPayroll: vi.fn(),
  },
}));

vi.mock('@shell/screens', () => ({
  useCan: () => true,
  NotEntitled: () => <div>NotEntitled</div>,
}));

describe('W-103 Exit Process UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders ResignationPage and loads employee exit requests', async () => {
    exitService.myExits.mockResolvedValue([
      {
        id: 'ex-1',
        resignationDate: '2026-10-01',
        requestedLwd: '2026-10-31',
        approvedLwd: null,
        reasonCategory: 'PERSONAL',
        status: 'SUBMITTED',
      },
    ]);

    render(
      <MemoryRouter>
        <ResignationPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Resignation & Exit')).toBeTruthy();
    await waitFor(() => {
      expect(screen.getByText('SUBMITTED')).toBeTruthy();
    });
  });

  it('renders ExitDashboard and approves a SUBMITTED exit', async () => {
    const row = {
      id: 'ex-2',
      employeeName: 'Rohan Deshmukh',
      resignationDate: '2026-10-01',
      requestedLwd: '2026-10-31',
      approvedLwd: null,
      noticePeriodDays: 30,
      status: 'SUBMITTED',
      clearanceItems: [],
    };
    exitService.listExits.mockResolvedValue([row]);
    exitService.approveExit.mockResolvedValue({ ...row, status: 'CLEARANCE_IN_PROGRESS' });

    render(
      <MemoryRouter>
        <ExitDashboard />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Rohan Deshmukh')).toBeTruthy();
    });

    fireEvent.click(screen.getByText('Approve'));
    await waitFor(() => {
      expect(exitService.approveExit).toHaveBeenCalledWith('ex-2', '2026-10-31');
    });
  });

  it('renders ClearanceDrawer and signs off IT department', async () => {
    const exit = {
      id: 'ex-3',
      employeeName: 'Asha Kulkarni',
      status: 'CLEARANCE_IN_PROGRESS',
      clearanceItems: [
        {
          id: 'item-1',
          department: 'IT',
          itemName: 'Laptop & Hardware Return',
          status: 'PENDING',
        },
      ],
    };
    const onUpdated = vi.fn();
    exitService.signOffDepartment.mockResolvedValue({ ...exit, status: 'READY_FOR_FNF' });

    render(<ClearanceDrawer exit={exit} open onClose={() => {}} onUpdated={onUpdated} />);

    expect(screen.getByText('Laptop & Hardware Return')).toBeTruthy();
    fireEvent.click(screen.getByText('Sign Off IT'));

    await waitFor(() => {
      expect(exitService.signOffDepartment).toHaveBeenCalledWith('ex-3', 'IT', 'CLEARED', 'Department cleared');
      expect(onUpdated).toHaveBeenCalled();
    });
  });
});
