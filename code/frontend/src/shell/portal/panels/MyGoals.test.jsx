import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyGoals } from './MyGoals.jsx';
import { goalService } from '../../../hrms/performance/goalService.js';

vi.mock('../../../hrms/performance/goalService.js', () => ({
  goalService: {
    getMyGoals: vi.fn(),
    createGoal: vi.fn(),
    editGoal: vi.fn(),
    startGoal: vi.fn(),
    cancelGoal: vi.fn(),
    checkIn: vi.fn(),
    getCheckIns: vi.fn(),
  },
}));

vi.mock('../../../hrms/performance/performanceService.js', () => ({
  performanceService: {
    listCycles: vi.fn().mockResolvedValue([]),
  },
}));

describe('MyGoals', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    goalService.getCheckIns.mockResolvedValue([]);
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
        readOnly: false,
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

  it('shows error alert with Retry button on load failure', async () => {
    goalService.getMyGoals.mockRejectedValueOnce(new Error('Network error'));

    render(
      <MemoryRouter>
        <MyGoals />
      </MemoryRouter>
    );

    expect(await screen.findByText(/failed to load your goals/i)).toBeDefined();
    expect(screen.getByRole('button', { name: /retry/i })).toBeDefined();
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
    expect(screen.getByRole('button', { name: /save as draft/i })).toBeDefined();
  });

  it('opens check-in modal and displays past check-in history', async () => {
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
        readOnly: false,
      },
    ]);
    goalService.getCheckIns.mockResolvedValueOnce([
      {
        id: 'ci-1',
        goalId: 'goal-2',
        currentValue: 5,
        progressPercentage: 50,
        notes: 'Passed initial 5 practice tests',
        createdAt: '2026-03-01T10:00:00Z',
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
    expect(screen.getByRole('button', { name: /save check-in/i })).toBeDefined();
    expect(await screen.findByText('Passed initial 5 practice tests')).toBeDefined();
  });

  it('renders read-only goal with History button and no mutation actions', async () => {
    goalService.getMyGoals.mockResolvedValueOnce([
      {
        id: 'goal-3',
        title: 'Archived Milestone',
        description: 'Completed goal in closed cycle',
        category: 'INDIVIDUAL',
        metricType: 'MILESTONE',
        targetValue: 1,
        currentValue: 1,
        metricUnit: '',
        progressPercentage: 100,
        startDate: '2026-01-01',
        dueDate: '2026-03-31',
        status: 'ACHIEVED',
        readOnly: true,
      },
    ]);

    render(
      <MemoryRouter>
        <MyGoals />
      </MemoryRouter>
    );

    expect(await screen.findByText('Archived Milestone')).toBeDefined();
    expect(screen.getByRole('button', { name: /history/i })).toBeDefined();
    expect(screen.queryByRole('button', { name: /check-in/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /start/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /^edit$/i })).toBeNull();
  });
});
