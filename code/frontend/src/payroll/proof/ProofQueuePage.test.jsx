import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { NavigationProvider } from '@shell/navigation/useNavigation';
import { ProofQueuePage } from './ProofQueuePage';
import { proofService } from './proofService';
import { currentFy } from '../tax/financialYear';

vi.mock('./proofService', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    proofService: {
      list: vi.fn(),
      summary: vi.fn(),
      review: vi.fn(),
      of: vi.fn(),
      reminderRules: vi.fn(),
      createReminderRule: vi.fn(),
    },
  };
});
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));

const ROWS = {
  content: [
    {
      employee_id: 'e1',
      number: 'GLBX-0001',
      name: 'Asha Rao',
      tax_regime: 'OLD',
      proof_status: 'SUBMITTED',
      proof_id: 'p1',
      submitted_at: '2027-01-12T10:00:00Z',
      claimed_total: 70000,
      approved_total: null,
    },
  ],
  totalElements: 1,
};

const SUMMARY = { counts: { NOT_STARTED: 3, DRAFT: 1, SUBMITTED: 1, APPROVED: 0, REJECTED: 0 }, due_date: '2027-01-31', proof_open: true };

function renderPage(actions) {
  return render(
    <NavigationProvider value={{ items: [], actions, loading: false, error: null }}>
      <ProofQueuePage />
    </NavigationProvider>,
  );
}

describe('ProofQueuePage (D-152, W-47.3c)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    proofService.list.mockResolvedValue(ROWS);
    proofService.summary.mockResolvedValue(SUMMARY);
    proofService.reminderRules.mockResolvedValue([]);
    proofService.review.mockResolvedValue({ id: 'p1', status: 'SUBMITTED', items: [] });
    proofService.of.mockResolvedValue({ id: 'p1', status: 'SUBMITTED', items: [] });
  });

  it('refuses without payroll.proof.read and reads nothing', () => {
    renderPage([]);
    expect(screen.getByText('Module Not Subscribed')).toBeTruthy();
    expect(proofService.list).not.toHaveBeenCalled();
  });

  it('opens on the verification queue: submitted proofs, status counts, words not codes', async () => {
    renderPage(['payroll.proof.read', 'payroll.proof.review']);
    expect(await screen.findByText('Asha Rao')).toBeTruthy();
    expect(proofService.list).toHaveBeenCalledWith(currentFy(), {
      status: 'SUBMITTED',
      search: '',
      page: 0,
      size: 25,
    });
    expect(screen.getByTestId('proof-summary').textContent).toMatch(/Not started: 3/);
    expect(screen.getByText('Old')).toBeTruthy();
    expect(screen.queryByText('SUBMITTED')).toBeNull();
  });

  it('a reviewer opens the review drawer from a row', async () => {
    renderPage(['payroll.proof.read', 'payroll.proof.review']);
    fireEvent.click(await screen.findByRole('button', { name: /Review/ }));
    await waitFor(() => expect(proofService.review).toHaveBeenCalledWith('p1'));
  });

  it('an officer without review sees View and reads the proof read-only', async () => {
    renderPage(['payroll.proof.read']);
    fireEvent.click(await screen.findByRole('button', { name: /View/ }));
    await waitFor(() => expect(proofService.of).toHaveBeenCalledWith('e1', currentFy()));
    expect(proofService.review).not.toHaveBeenCalled();
  });

  it('searches by name or number', async () => {
    renderPage(['payroll.proof.read']);
    await screen.findByText('Asha Rao');
    const box = screen.getByLabelText('Search employees');
    fireEvent.change(box, { target: { value: 'GLBX' } });
    fireEvent.keyDown(box, { key: 'Enter', code: 'Enter', keyCode: 13 });
    await waitFor(() =>
      expect(proofService.list).toHaveBeenLastCalledWith(currentFy(), expect.objectContaining({ search: 'GLBX' })),
    );
  });

  it('the chase list asks for employees who have not started', async () => {
    renderPage(['payroll.proof.read']);
    await screen.findByText('Asha Rao');
    fireEvent.click(screen.getByText('Chase list'));
    await waitFor(() =>
      expect(proofService.list).toHaveBeenLastCalledWith(currentFy(), expect.objectContaining({ status: 'NOT_STARTED' })),
    );
    expect(screen.getByTestId('proof-reminder')).toBeTruthy();
  });

  it('opened from the approvals inbox with a proof id, opens that proof in its year (itemRoutes.js)', async () => {
    proofService.review.mockResolvedValue({
      id: 'p9',
      employee_id: 'e9',
      financial_year: '2025-2026',
      status: 'SUBMITTED',
      items: [],
    });
    render(
      <NavigationProvider value={{ items: [], actions: ['payroll.proof.read', 'payroll.proof.review'], loading: false, error: null }}>
        <MemoryRouter initialEntries={['/payroll/proof-of-investment/p9']}>
          <Routes>
            <Route path="/payroll/proof-of-investment/:proofId" element={<ProofQueuePage />} />
          </Routes>
        </MemoryRouter>
      </NavigationProvider>,
    );
    await waitFor(() => expect(proofService.review).toHaveBeenCalledWith('p9'));
    await waitFor(() =>
      expect(proofService.list).toHaveBeenLastCalledWith('2025-26', expect.objectContaining({ status: 'SUBMITTED' })),
    );
    expect(await screen.findByText('Financial Year 2025-26')).toBeTruthy();
  });

  it('an inbox link opened without the review permission says so and reads nothing by id (D-152)', async () => {
    render(
      <NavigationProvider value={{ items: [], actions: ['payroll.proof.read'], loading: false, error: null }}>
        <MemoryRouter initialEntries={['/payroll/proof-of-investment/p9']}>
          <Routes>
            <Route path="/payroll/proof-of-investment/:proofId" element={<ProofQueuePage />} />
          </Routes>
        </MemoryRouter>
      </NavigationProvider>,
    );
    expect(await screen.findByText(/Only a proof reviewer can open this proof/)).toBeTruthy();
    expect(proofService.review).not.toHaveBeenCalled();
  });
});
