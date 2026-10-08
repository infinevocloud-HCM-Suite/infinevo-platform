import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { EmployeeImport, errorsFirst, isStalled, STALL_MS } from './EmployeeImport.jsx';
import { employeeImportService } from './employeeImportService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./employeeImportService.js', () => ({
  employeeImportService: {
    template: vi.fn(),
    dryRun: vi.fn(),
    importFile: vi.fn(),
    jobs: vi.fn(),
    resultFile: vi.fn(),
  },
  saveCsv: vi.fn(),
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

const DRY_RUN = [
  { row: 1, employeeNumber: 'E1', status: 'OK', message: 'Will be created' },
  { row: 2, employeeNumber: 'E2', status: 'OK', message: 'Will be created and invited' },
  { row: 3, employeeNumber: 'E3', status: 'ERROR', message: 'date_of_joining x is not a date' },
];

function renderScreen() {
  return render(
    <MemoryRouter>
      <EmployeeImport />
    </MemoryRouter>,
  );
}

async function chooseFile(container) {
  const input = container.querySelector('input[type="file"]');
  const file = new File(['employee_number\n'], 'staff.csv', { type: 'text/csv' });
  fireEvent.change(input, { target: { files: [file] } });
  await waitFor(() => expect(screen.getByTestId('import-file-name').textContent).toBe('staff.csv'));
  return file;
}

function importButton() {
  return document.getElementById('btn-import-start');
}

describe('EmployeeImport', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeImportService.jobs.mockResolvedValue([
      {
        jobId: 'job-1',
        kind: 'IMPORT',
        status: 'COMPLETED',
        createdCount: 48,
        invitedCount: 30,
        failedCount: 2,
        resultDocumentId: 'doc-1',
        startedAt: '2026-10-08T10:00:00Z',
      },
    ]);
  });

  it('orders error rows first, then by row number', () => {
    expect(errorsFirst(DRY_RUN).map((r) => r.row)).toEqual([3, 1, 2]);
  });

  it('treats a queued or running job untouched for ten minutes as stalled, and nothing else', () => {
    const now = Date.parse('2026-10-08T12:00:00Z');
    const old = new Date(now - STALL_MS - 1000).toISOString();
    const fresh = new Date(now - 60 * 1000).toISOString();
    expect(isStalled({ status: 'RUNNING', updatedAt: old }, now)).toBe(true);
    expect(isStalled({ status: 'QUEUED', startedAt: old }, now)).toBe(true);
    expect(isStalled({ status: 'RUNNING', updatedAt: fresh }, now)).toBe(false);
    expect(isStalled({ status: 'COMPLETED', updatedAt: old }, now)).toBe(false);
  });

  it('shows a stalled job as stalled and stops polling', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      employeeImportService.jobs.mockResolvedValue([
        { jobId: 'job-9', kind: 'IMPORT', status: 'RUNNING', progressPercentage: 40, updatedAt: '2020-01-01T00:00:00Z' },
      ]);
      renderScreen();
      await waitFor(() => expect(screen.getByText('Stalled')).toBeDefined());
      const calls = employeeImportService.jobs.mock.calls.length;

      await vi.advanceTimersByTimeAsync(10_000);

      expect(employeeImportService.jobs.mock.calls.length).toBe(calls);
    } finally {
      vi.useRealTimers();
    }
  });

  it('shows the history with counts and a result download', async () => {
    renderScreen();

    await waitFor(() => expect(screen.getByText('48')).toBeDefined());
    fireEvent.click(screen.getByTestId('btn-result-job-1'));
    await waitFor(() => expect(employeeImportService.resultFile).toHaveBeenCalledWith('job-1'));
  });

  it('shows error rows first and keeps Import disabled until "import valid rows only" is ticked', async () => {
    employeeImportService.dryRun.mockResolvedValue(DRY_RUN);
    employeeImportService.importFile.mockResolvedValue('job-2');
    const { container } = renderScreen();

    expect(importButton().disabled).toBe(true);
    const file = await chooseFile(container);
    expect(importButton().disabled).toBe(true);

    fireEvent.click(screen.getByText('Dry run'));
    await waitFor(() => expect(screen.getByText('2 OK')).toBeDefined());
    expect(screen.getByText('1 with errors')).toBeDefined();

    const table = screen.getByTestId('import-dry-run-table');
    const firstRow = table.querySelector('tbody tr');
    expect(within(firstRow).getByText('E3')).toBeDefined();

    expect(importButton().disabled).toBe(true);
    fireEvent.click(screen.getByText('Import valid rows only'));
    await waitFor(() => expect(importButton().disabled).toBe(false));

    fireEvent.click(importButton());
    await waitFor(() => expect(employeeImportService.importFile).toHaveBeenCalledWith(file, true));
  });

  it('enables Import straight after a clean dry run', async () => {
    employeeImportService.dryRun.mockResolvedValue(DRY_RUN.slice(0, 2));
    const { container } = renderScreen();
    await chooseFile(container);

    fireEvent.click(screen.getByText('Dry run'));
    await waitFor(() => expect(screen.getByText('2 OK')).toBeDefined());

    expect(screen.queryByText('Import valid rows only')).toBeNull();
    expect(importButton().disabled).toBe(false);
  });

  it('shows the server refusal, such as the role permission, instead of results', async () => {
    employeeImportService.dryRun.mockRejectedValue({ message: 'Not permitted: core.role.assign' });
    const { container } = renderScreen();
    await chooseFile(container);

    fireEvent.click(screen.getByText('Dry run'));

    await waitFor(() => expect(screen.getByText('Not permitted: core.role.assign')).toBeDefined());
    expect(importButton().disabled).toBe(true);
  });

  it('refuses a file that is not a CSV', async () => {
    const { container } = renderScreen();
    const input = container.querySelector('input[type="file"]');
    fireEvent.change(input, { target: { files: [new File(['x'], 'staff.xlsx')] } });

    await waitFor(() => expect(screen.getByText('Choose a .csv file')).toBeDefined());
  });

  it('renders NotEntitled without core.employee.create', () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    renderScreen();

    expect(screen.queryByText('Import Employees')).toBeNull();
    expect(employeeImportService.jobs).not.toHaveBeenCalled();
  });
});
