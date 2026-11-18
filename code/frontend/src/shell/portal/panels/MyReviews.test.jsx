import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyReviews } from './MyReviews.jsx';
import { performanceService } from '../../../hrms/performance/performanceService.js';

vi.mock('../../../hrms/performance/performanceService.js', () => ({
  performanceService: {
    getMyReviews: vi.fn(),
    submitSelfReview: vi.fn(),
  },
}));

describe('MyReviews', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders pending self-review banner when an active review requires self-appraisal', async () => {
    performanceService.getMyReviews.mockResolvedValueOnce([
      {
        id: 'review-1',
        cycleName: 'Q4 2026 Appraisal',
        managerName: 'Alice Manager',
        selfRating: null,
        selfComments: null,
        managerRating: null,
        status: 'PENDING_SELF_REVIEW',
      },
    ]);

    render(
      <MemoryRouter>
        <MyReviews />
      </MemoryRouter>
    );

    expect(await screen.findByText('Self-Appraisal Action Required')).toBeDefined();
    expect(screen.getByRole('button', { name: /fill self-review/i })).toBeDefined();
  });

  it('opens self-appraisal modal and shows rating input', async () => {
    performanceService.getMyReviews.mockResolvedValueOnce([
      {
        id: 'review-1',
        cycleName: 'Q4 2026 Appraisal',
        managerName: 'Alice Manager',
        selfRating: null,
        selfComments: null,
        managerRating: null,
        status: 'PENDING_SELF_REVIEW',
      },
    ]);

    render(
      <MemoryRouter>
        <MyReviews />
      </MemoryRouter>
    );

    const fillBtn = await screen.findByRole('button', { name: /fill self-review/i });
    fireEvent.click(fillBtn);

    expect(await screen.findByText('Self-Appraisal: Q4 2026 Appraisal')).toBeDefined();
    expect(screen.getByText('Key Achievements & Self-Assessment')).toBeDefined();
  });

  it('displays completed appraisal with manager feedback', async () => {
    performanceService.getMyReviews.mockResolvedValueOnce([
      {
        id: 'review-done',
        cycleName: 'Q3 2026 Appraisal',
        managerName: 'Alice Manager',
        selfRating: 4,
        selfComments: 'Delivered customer portal on time',
        managerRating: 5,
        managerComments: 'Exceptional ownership and delivery',
        status: 'COMPLETED',
      },
    ]);

    render(
      <MemoryRouter>
        <MyReviews />
      </MemoryRouter>
    );

    expect(await screen.findByText('Q3 2026 Appraisal')).toBeDefined();
    expect(screen.getByText('Appraisal Completed')).toBeDefined();
    expect(screen.getByText('(5/5)')).toBeDefined();
  });
});

