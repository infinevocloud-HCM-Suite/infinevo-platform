import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { DeductionGrid, MAX_LINES, addLine, blankLine, toRequestLine } from './DeductionGrid.jsx';
import { deductionService } from './deductionService.js';
import { claimService } from './claimService.js';

vi.mock('./deductionService.js', () => ({
  deductionService: {
    enter: vi.fn(),
  },
}));

vi.mock('./claimService.js', () => ({
  claimService: {
    searchEmployees: vi.fn(),
  },
}));

const postButton = () => screen.getByRole('button', { name: /post$/i });
const addButton = () => screen.getByRole('button', { name: /add line/i });
const dataRows = () => Array.from(document.querySelectorAll('tr.ant-table-row'));

describe('DeductionGrid (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimService.searchEmployees.mockResolvedValue([]);
  });

  it('a 400 with fieldErrors.line = 2 marks row 3 and posts nothing', async () => {
    deductionService.enter.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Line 2: amount must be greater than zero',
      fieldErrors: { line: '2' },
    });
    const onPosted = vi.fn();
    const onClose = vi.fn();
    render(<DeductionGrid open onClose={onClose} onPosted={onPosted} />);

    fireEvent.click(addButton());
    fireEvent.click(addButton());
    expect(dataRows()).toHaveLength(3);

    fireEvent.click(postButton());

    await waitFor(() => expect(deductionService.enter).toHaveBeenCalledTimes(1));
    expect(deductionService.enter.mock.calls[0][0]).toHaveLength(3);
    await waitFor(() => {
      const rows = dataRows();
      expect(rows[2].getAttribute('aria-invalid')).toBe('true');
      expect(rows[2].className).toContain('deduction-line-error');
      expect(rows[0].getAttribute('aria-invalid')).toBeNull();
      expect(rows[1].getAttribute('aria-invalid')).toBeNull();
    });
    expect(screen.getByText('Line 3: nothing was posted')).toBeDefined();
    expect(screen.getByText('Line 2: amount must be greater than zero')).toBeDefined();
    expect(onPosted).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('the 501st line cannot be added', () => {
    let lines = [];
    for (let i = 0; i < MAX_LINES; i += 1) lines = addLine(lines);
    expect(lines).toHaveLength(500);
    const after = addLine(lines);
    expect(after).toBe(lines);
    expect(after).toHaveLength(500);
  });

  it('counts lines against the 500 limit and removes a line', () => {
    render(<DeductionGrid open onClose={vi.fn()} />);
    expect(screen.getByTestId('line-count').textContent).toBe('1 / 500 lines');
    fireEvent.click(addButton());
    expect(screen.getByTestId('line-count').textContent).toBe('2 / 500 lines');
    fireEvent.click(screen.getAllByRole('button', { name: /remove line/i })[0]);
    expect(dataRows()).toHaveLength(1);
  });

  it('Post is disabled in flight and a double click posts once', async () => {
    let resolve;
    deductionService.enter.mockImplementation(
      () =>
        new Promise((r) => {
          resolve = r;
        })
    );
    const onPosted = vi.fn();
    render(<DeductionGrid open onClose={vi.fn()} onPosted={onPosted} />);

    fireEvent.click(postButton());
    fireEvent.click(postButton());

    await waitFor(() => expect(postButton().disabled).toBe(true));
    expect(deductionService.enter).toHaveBeenCalledTimes(1);

    resolve({ count: 1, rows: [] });
    await waitFor(() => expect(onPosted).toHaveBeenCalledWith({ count: 1, rows: [] }));
    expect(deductionService.enter).toHaveBeenCalledTimes(1);
  });

  it('sends each line with the period as YYYY-MM and the amount as text', () => {
    const line = {
      ...blankLine(),
      employee_id: 'emp-1',
      period: dayjs('2026-10-15'),
      deduction_type: 'LOAN_RECOVERY',
      amount: '1200.5',
      reason: '  Salary advance  ',
      remarks: '',
    };
    expect(toRequestLine(line)).toEqual({
      employee_id: 'emp-1',
      period: '2026-10',
      deduction_type: 'LOAN_RECOVERY',
      amount: '1200.50',
      reason: 'Salary advance',
      remarks: null,
      document_id: null,
    });
  });
});
