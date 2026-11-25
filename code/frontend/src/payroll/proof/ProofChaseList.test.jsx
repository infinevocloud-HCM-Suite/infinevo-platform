import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { NavigationProvider } from '@shell/navigation/useNavigation';
import { ProofChaseList, describeRule } from './ProofChaseList';
import { proofService } from './proofService';
import { successMsg } from '@shared/ui/msgHelper.js';

vi.mock('./proofService', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    proofService: { list: vi.fn(), reminderRules: vi.fn(), createReminderRule: vi.fn() },
  };
});
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));

const PENDING_ROW = {
  content: [
    { employee_id: 'e2', number: 'GLBX-0002', name: 'Ravi Kumar', tax_regime: 'NEW', proof_status: 'NOT_STARTED', proof_id: null, claimed_total: 0 },
  ],
  totalElements: 1,
};

function renderList(actions) {
  return render(
    <NavigationProvider value={{ items: [], actions, loading: false, error: null }}>
      <ProofChaseList fy="2026-27" dueDate="2027-01-31" onOpen={vi.fn()} />
    </NavigationProvider>,
  );
}

describe('ProofChaseList (D-152, W-34.3 screen)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    proofService.list.mockResolvedValue(PENDING_ROW);
  });

  it('lists employees who owe proof, with no open action for one with no proof yet', async () => {
    proofService.reminderRules.mockResolvedValue([]);
    renderList(['payroll.proof.read']);
    expect(await screen.findByText('Ravi Kumar')).toBeTruthy();
    expect(document.querySelector('.ant-table-tbody').textContent).toMatch(/Not started/);
    expect(screen.queryByRole('button', { name: /View/ })).toBeNull();
    expect(proofService.list).toHaveBeenCalledWith('2026-27', expect.objectContaining({ status: 'NOT_STARTED' }));
  });

  it('without core.reminder_rule.manage, explains the reminder and reads no rules', async () => {
    renderList(['payroll.proof.read']);
    await screen.findByText('Ravi Kumar');
    expect(screen.getByText(/administrator who manages reminders/)).toBeTruthy();
    expect(proofService.reminderRules).not.toHaveBeenCalled();
    expect(screen.queryByTestId('proof-reminder-open')).toBeNull();
  });

  it('shows the existing POI reminder rule in words', async () => {
    proofService.reminderRules.mockResolvedValue([
      { id: 'r1', offset_days: 7, send_at_local_time: '10:00:00', repeat_every_days: 2, is_active: true },
    ]);
    renderList(['payroll.proof.read', 'core.reminder_rule.manage']);
    expect((await screen.findByTestId('proof-reminder-rule')).textContent).toBe(
      'Reminds each employee 7 days before the due date at 10:00, then every 2 days.',
    );
    expect(screen.queryByTestId('proof-reminder-open')).toBeNull();
  });

  it('sets up the reminder rule when none exists', async () => {
    proofService.reminderRules.mockResolvedValueOnce([]).mockResolvedValueOnce([
      { id: 'r1', offset_days: 7, send_at_local_time: '10:00', repeat_every_days: 2 },
    ]);
    proofService.createReminderRule.mockResolvedValueOnce({ id: 'r1' });
    renderList(['payroll.proof.read', 'core.reminder_rule.manage']);
    expect(await screen.findByTestId('proof-reminder-none')).toBeTruthy();
    fireEvent.click(screen.getByTestId('proof-reminder-open'));
    fireEvent.click(await screen.findByRole('button', { name: 'Save reminder' }));
    await waitFor(() =>
      expect(proofService.createReminderRule).toHaveBeenCalledWith({ offsetDays: 7, sendAt: '10:00', repeatEveryDays: 2 }),
    );
    await waitFor(() => expect(successMsg).toHaveBeenCalled());
    expect(await screen.findByTestId('proof-reminder-rule')).toBeTruthy();
  });

  it('refuses a bad send time before sending', async () => {
    proofService.reminderRules.mockResolvedValue([]);
    renderList(['payroll.proof.read', 'core.reminder_rule.manage']);
    fireEvent.click(await screen.findByTestId('proof-reminder-open'));
    fireEvent.change(await screen.findByLabelText('Send time'), { target: { value: '25:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save reminder' }));
    expect((await screen.findByTestId('reminder-problem')).textContent).toMatch(/HH:MM/);
    expect(proofService.createReminderRule).not.toHaveBeenCalled();
  });

  it('describeRule reads a one-off and a paused rule', () => {
    expect(describeRule({ offset_days: 3, send_at_local_time: '09:30:00' })).toBe('3 days before the due date at 09:30');
    expect(describeRule({ offset_days: 3, send_at_local_time: '09:30', is_active: false })).toMatch(/paused/);
  });

  it('offers no "Any status" that would list employees who already submitted (D-152)', async () => {
    renderList(['payroll.proof.read']);
    await screen.findByTestId('proof-status-filter');
    fireEvent.mouseDown(screen.getByTestId('proof-status-filter').querySelector('.ant-select-selector'));
    await screen.findAllByText('Draft');
    expect(screen.queryByText('Any status')).toBeNull();
  });
});
