import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { OffCycleCreate, INPUT_KINDS, parseEmployeeError } from './OffCycleCreate.jsx';
import { payrunService } from './payrunService.js';

vi.mock('./payrunService.js', () => ({
  payrunService: {
    searchEmployees: vi.fn(),
    createOffCycle: vi.fn(),
    addInputs: vi.fn(),
  },
}));

const ALICE = '3f1c2a64-7d0e-4b8a-9c11-0a2b3c4d5e01';
const BOB = '3f1c2a64-7d0e-4b8a-9c11-0a2b3c4d5e02';
const STRANGER = '9a9a9a9a-0000-4000-8000-000000000009';

describe('OffCycleCreate component (W-47.2 §7)', () => {
  const mockEmployees = [
    { id: ALICE, employeeNumber: 'E01', firstName: 'Alice', lastName: 'Smith' },
    { id: BOB, employeeNumber: 'E02', firstName: 'Bob', lastName: 'Jones' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    payrunService.searchEmployees.mockResolvedValue(mockEmployees);
  });

  // Fills step 1 with a pay date and the given employees, then submits it.
  const submitStep1 = async (labels) => {
    render(
      <MemoryRouter>
        <OffCycleCreate />
      </MemoryRouter>
    );

    const dateInput = screen.getByPlaceholderText('Select payment date');
    fireEvent.change(dateInput, { target: { value: '2026-10-25' } });
    fireEvent.keyDown(dateInput, { key: 'Enter' });

    const select = screen.getByRole('combobox');
    for (const label of labels) {
      fireEvent.mouseDown(select);
      fireEvent.click(await screen.findByText(label));
    }

    const submitBtn = screen.getByRole('button', { name: /create run & enter inputs/i });
    expect(submitBtn.hasAttribute('disabled')).toBe(false);
    fireEvent.click(submitBtn);
  };

  const goToStep2 = async () => {
    payrunService.createOffCycle.mockResolvedValueOnce({
      id: 'run-off-1',
      period: '2026-10',
      run_type: 'OFF_CYCLE',
    });
    await submitStep1(['E01 — Alice Smith']);
    await screen.findByText(/Step 2: Tagged Inputs for Run 2026-10/i);
    fireEvent.change(screen.getByPlaceholderText('0.00'), { target: { value: '7500' } });
  };

  it('names each employee from a 400 that carries only a message with their ids', async () => {
    // The real apiClient error for EmployeeNotInRunException: a message, nothing else.
    payrunService.createOffCycle.mockRejectedValueOnce({
      code: 'VALIDATION_FAILED',
      message: `No such employee in this tenant: [${ALICE}, ${BOB}, ${STRANGER}]`,
      status: 400,
      isModuleNotEntitled: false,
      isForbidden: false,
    });

    await submitStep1(['E01 — Alice Smith', 'E02 — Bob Jones']);

    await waitFor(() => {
      expect(screen.getByText('No such employee in this tenant')).toBeDefined();
      expect(screen.getByText('Alice Smith (E01)')).toBeDefined();
      expect(screen.getByText('Bob Jones (E02)')).toBeDefined();
      expect(screen.getByText(`Unknown employee (${STRANGER})`)).toBeDefined();
    });
    // The raw id list is never the only thing shown.
    expect(screen.queryByText(new RegExp(`\\[${ALICE}`))).toBeNull();
  });

  it('posts [{employeeId, kind, amount, sourceRef}] and marks a DUPLICATE row', async () => {
    await goToStep2();
    payrunService.addInputs.mockResolvedValueOnce([
      { employee_id: ALICE, source_ref: 'OT-SEP-2026', result: 'DUPLICATE', pay_input_id: null },
    ]);

    fireEvent.change(screen.getByPlaceholderText('e.g. OT-SEP-2026'), { target: { value: 'OT-SEP-2026' } });
    fireEvent.click(screen.getByRole('button', { name: /save inputs/i }));

    await waitFor(() => {
      expect(payrunService.addInputs).toHaveBeenCalledWith('run-off-1', [
        { employeeId: ALICE, kind: 'ONE_TIME_PAYOUT', amount: 7500, sourceRef: 'OT-SEP-2026' },
      ]);
      expect(screen.getByText('DUPLICATE')).toBeDefined();
    });
  });

  it('trims sourceRef before posting, so the result lands on its row', async () => {
    await goToStep2();
    payrunService.addInputs.mockResolvedValueOnce([
      {
        employee_id: ALICE,
        source_ref: 'OT-SEP-2026',
        result: 'RECORDED',
        pay_input_id: 'a1b2c3d4-0000-4000-8000-000000000001',
      },
    ]);

    fireEvent.change(screen.getByPlaceholderText('e.g. OT-SEP-2026'), {
      target: { value: '  OT-SEP-2026 ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /save inputs/i }));

    await waitFor(() => {
      expect(payrunService.addInputs).toHaveBeenCalledWith('run-off-1', [
        { employeeId: ALICE, kind: 'ONE_TIME_PAYOUT', amount: 7500, sourceRef: 'OT-SEP-2026' },
      ]);
      expect(screen.getByText('ID: a1b2c3d4')).toBeDefined();
    });
  });

  it('names nothing as a bonus (W-47.2 §14 decision 5)', () => {
    const { container } = render(
      <MemoryRouter>
        <OffCycleCreate />
      </MemoryRouter>
    );
    expect(container.innerHTML.toLowerCase()).not.toContain('bonus');
  });
});

describe('parseEmployeeError', () => {
  it('splits the reason from the ids', () => {
    expect(
      parseEmployeeError(`Not employed between 2026-10-01 and 2026-10-31, so not payable on this run: [${BOB}]`)
    ).toEqual({
      reason: 'Not employed between 2026-10-01 and 2026-10-31, so not payable on this run',
      ids: [BOB],
    });
  });

  it('leaves a message without ids untouched', () => {
    expect(parseEmployeeError('pay_date is required')).toEqual({ reason: 'pay_date is required', ids: [] });
    expect(parseEmployeeError(undefined)).toEqual({ reason: '', ids: [] });
  });
});

describe('off-cycle input kinds (W-30.2 §4)', () => {
  it('offers exactly the server PayInputKind values an off-cycle run accepts', () => {
    // core.payinput.PayInputKind without LOP_DAYS; anything else is a 400 from the server.
    expect(INPUT_KINDS.map((k) => k.value).sort()).toEqual(
      ['AD_HOC_DEDUCTION', 'ONE_TIME_PAYOUT', 'OVERTIME', 'REIMBURSEMENT'].sort()
    );
  });
});
