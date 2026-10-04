import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SetupCard } from './SetupCard.jsx';
import { dashboardService } from './dashboardService.js';
import { useCan } from '@shell/screens';

vi.mock('./dashboardService.js', () => ({
  dashboardService: { setupChecklist: vi.fn() },
}));
vi.mock('@shell/screens', () => ({ useCan: vi.fn() }));

const step = (code, module, completed, skipped) => ({ code, module, completed, skipped });

const renderCard = () =>
  render(
    <MemoryRouter>
      <SetupCard />
    </MemoryRouter>
  );

describe('SetupCard (W-47.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCan.mockImplementation((code) => code === 'core.tenant.read');
  });

  it('counts payroll steps only, and a skipped step counts as done', async () => {
    dashboardService.setupChecklist.mockResolvedValue({
      steps: [
        step('ORG', null, false, false),
        step('HOLIDAYS', 'HRMS', false, false),
        step('PAY_SCHEDULE', 'PAYROLL', true, false),
        step('EPF', 'PAYROLL', false, true),
        step('PRIOR_PAYROLL', 'PAYROLL', false, false),
        step('PT', 'PAYROLL', false, false),
      ],
    });
    renderCard();
    expect(await screen.findByText('2 of 4 payroll setup steps left')).toBeDefined();
    expect(screen.getByRole('link', { name: 'Open setup' }).getAttribute('href')).toBe('/setup');
  });

  it('renders nothing when no payroll step is left', async () => {
    dashboardService.setupChecklist.mockResolvedValue({
      steps: [
        step('PAY_SCHEDULE', 'PAYROLL', true, false),
        step('EPF', 'PAYROLL', false, true),
        step('ORG', null, false, false),
      ],
    });
    const { container } = renderCard();
    await waitFor(() => expect(dashboardService.setupChecklist).toHaveBeenCalled());
    await Promise.resolve();
    expect(container.textContent).toBe('');
  });

  it('renders nothing and does not call without core.tenant.read', () => {
    useCan.mockReturnValue(false);
    const { container } = renderCard();
    expect(dashboardService.setupChecklist).not.toHaveBeenCalled();
    expect(container.textContent).toBe('');
  });

  it('renders nothing when the call fails', async () => {
    dashboardService.setupChecklist.mockRejectedValue(new Error('boom'));
    const { container } = renderCard();
    await waitFor(() => expect(dashboardService.setupChecklist).toHaveBeenCalled());
    await Promise.resolve();
    expect(container.textContent).toBe('');
  });
});
