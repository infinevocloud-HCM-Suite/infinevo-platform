import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyGoals } from './MyGoals.jsx';
import { goalService } from '../../../hrms/performance/goalService.js';

vi.mock('../../../hrms/performance/goalService.js', () => ({
  goalService: {
    getMyGoals: vi.fn(),
    createGoal: vi.fn(),
    updateProgress: vi.fn(),
  },
}));

describe('MyGoals', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders goals list and statistic cards', async () => {
    goalService.getMyGoals.mockResolvedValueOnce([
      {
        id: 'goal-1',
        title: 'Launch OKR Feature',
        description: 'Implement frontend and backend for W-106.2',
        category: 'INDIVIDUAL',
        metricType: 'PERCENTAGE',
        targetValue: 100,
        currentValue: 60,
        metricUnit: '%',
        progressPercentage: 60,
        startDate: '2026-01-01',
        dueDate: '2026-06-30',
        status: 'IN_PROGRESS',
      },
    ]);

    render(
      <MemoryRouter>
        <MyGoals />
      </MemoryRouter>
    );

    expect(await screen.findByText('Launch OKR Feature')).toBeDefined();
    expect(screen.getByText('Active Goals')).toBeDefined();
    expect(screen.getByText('Achieved Goals')).toBeDefined();
    expect(screen.getByText('Average Progress')).toBeDefined();
    expect(screen.getByText('60%')).toBeDefined();
  });

  it('opens create goal modal when Add Goal button is clicked', async () => {
    goalService.getMyGoals.mockResolvedValueOnce([]);

    render(
      <MemoryRouter>
        <MyGoals />
      </MemoryRouter>
    );

    const addBtn = await screen.findByRole('button', { name: /add goal/i });
    fireEvent.click(addBtn);

    expect(await screen.findByText('Create New Goal / OKR')).toBeDefined();
    expect(screen.getByLabelText(/goal title/i)).toBeDefined();
    expect(screen.getByRole('button', { name: /save goal/i })).toBeDefined();
  });

  it('opens progress check-in modal when Check-in button is clicked', async () => {
    goalService.getMyGoals.mockResolvedValueOnce([
      {
        id: 'goal-2',
        title: 'Complete Certification',
        description: 'Pass advanced cloud exam',
        category: 'INDIVIDUAL',
        metricType: 'NUMERIC',
        targetValue: 10,
        currentValue: 5,
        metricUnit: 'modules',
        progressPercentage: 50,
        startDate: '2026-02-01',
        dueDate: '2026-08-31',
        status: 'IN_PROGRESS',
      },
    ]);

    render(
      <MemoryRouter>
        <MyGoals />
      </MemoryRouter>
    );

    const checkInBtn = await screen.findByRole('button', { name: /check-in/i });
    fireEvent.click(checkInBtn);

    expect(await screen.findByText(/goal progress check-in/i)).toBeDefined();
    expect(screen.getByText(/target: 10 modules/i)).toBeDefined();
    expect(screen.getByRole('button', { name: /save check-in/i })).toBeDefined();
  });
});

