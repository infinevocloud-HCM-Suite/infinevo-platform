import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PerformanceCyclesScreen } from './PerformanceCyclesScreen.jsx';
import { performanceService } from './performanceService.js';

vi.mock('./performanceService.js', () => ({
  performanceService: {
    listCycles: vi.fn(),
    createCycle: vi.fn(),
    activateCycle: vi.fn(),
    closeCycle: vi.fn(),
    getCycleSummary: vi.fn(),
  },
}));

describe('PerformanceCyclesScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders cycles list and displays status tags', async () => {
    performanceService.listCycles.mockResolvedValueOnce([
      {
        id: 'cycle-1',
        name: 'Q4 2026 Appraisal',
        periodStart: '2026-10-01',
        periodEnd: '2026-12-31',
        selfReviewDeadline: '2026-11-30',
        managerReviewDeadline: '2026-12-15',
        status: 'ACTIVE',
        ratingScaleMax: 5,
      },
    ]);

    render(
      <MemoryRouter>
        <PerformanceCyclesScreen />
      </MemoryRouter>
    );

    expect(await screen.findByText('Q4 2026 Appraisal')).toBeDefined();
    expect(screen.getByText('Active')).toBeDefined();
  });

  it('opens create cycle modal on button click', async () => {
    performanceService.listCycles.mockResolvedValueOnce([]);

    render(
      <MemoryRouter>
        <PerformanceCyclesScreen />
      </MemoryRouter>
    );

    expect(await screen.findByText('Performance Review Cycles')).toBeDefined();
    const createBtn = screen.getByTestId('create-cycle-button');
    fireEvent.click(createBtn);

    expect(await screen.findByText('Create Performance Review Cycle')).toBeDefined();
  });

  it('opens summary drawer when Summary button is clicked', async () => {
    performanceService.listCycles.mockResolvedValueOnce([
      {
        id: 'cycle-1',
        name: 'Annual 2026 Review',
        periodStart: '2026-01-01',
        periodEnd: '2026-12-31',
        selfReviewDeadline: '2026-11-30',
        managerReviewDeadline: '2026-12-15',
        status: 'ACTIVE',
        ratingScaleMax: 5,
      },
    ]);
    performanceService.getCycleSummary.mockResolvedValueOnce({
      cycleId: 'cycle-1',
      cycleName: 'Annual 2026 Review',
      status: 'ACTIVE',
      totalReviews: 20,
      completedReviews: 15,
      pendingSelfReviews: 2,
      pendingManagerReviews: 3,
      completionPercentage: 75.0,
      averageRating: 4.2,
    });

    render(
      <MemoryRouter>
        <PerformanceCyclesScreen />
      </MemoryRouter>
    );

    const summaryBtn = await screen.findByRole('button', { name: /summary/i });
    fireEvent.click(summaryBtn);

    await waitFor(() => {
      expect(performanceService.getCycleSummary).toHaveBeenCalledWith('cycle-1');
    });
  });
});

