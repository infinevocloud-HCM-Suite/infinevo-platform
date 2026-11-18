import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TeamReviewsScreen } from './TeamReviewsScreen.jsx';
import { performanceService } from './performanceService.js';

vi.mock('./performanceService.js', () => ({
  performanceService: {
    getTeamReviews: vi.fn(),
    submitManagerReview: vi.fn(),
  },
}));

describe('TeamReviewsScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders team reviews list with employee names and status tags', async () => {
    performanceService.getTeamReviews.mockResolvedValueOnce([
      {
        id: 'review-1',
        employeeId: 'emp-1',
        employeeName: 'Jane Developer',
        employeeNumber: 'EMP-101',
        cycleName: 'Q4 2026 Appraisal',
        selfRating: 4,
        selfComments: 'Completed performance tuning',
        managerRating: null,
        status: 'PENDING_MANAGER_REVIEW',
      },
    ]);

    render(
      <MemoryRouter>
        <TeamReviewsScreen />
      </MemoryRouter>
    );

    expect(await screen.findByText('Jane Developer')).toBeDefined();
    expect(screen.getByText('Ready for Manager Review')).toBeDefined();
    expect(screen.getByText('(4/5)')).toBeDefined();
  });

  it('opens evaluation drawer when Evaluate button is clicked', async () => {
    performanceService.getTeamReviews.mockResolvedValueOnce([
      {
        id: 'review-1',
        employeeId: 'emp-1',
        employeeName: 'Jane Developer',
        employeeNumber: 'EMP-101',
        cycleName: 'Q4 2026 Appraisal',
        selfRating: 4,
        selfComments: 'Completed performance tuning',
        managerRating: null,
        status: 'PENDING_MANAGER_REVIEW',
      },
    ]);

    render(
      <MemoryRouter>
        <TeamReviewsScreen />
      </MemoryRouter>
    );

    const evaluateBtn = await screen.findByRole('button', { name: /evaluate/i });
    fireEvent.click(evaluateBtn);

    expect(await screen.findByText('Evaluate: Jane Developer')).toBeDefined();
    expect(screen.getByText('Completed performance tuning')).toBeDefined();
    expect(screen.getByText('Submit Performance Rating')).toBeDefined();
  });
});

