import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TeamGoalsScreen } from './TeamGoalsScreen.jsx';
import { goalService } from './goalService.js';

vi.mock('./goalService.js', () => ({
  goalService: {
    getTeamGoals: vi.fn(),
    getCheckIns: vi.fn(),
  },
}));

describe('TeamGoalsScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    goalService.getCheckIns.mockResolvedValue([]);
  });

  it('renders team goals list with employee name, goal title and metrics', async () => {
    goalService.getTeamGoals.mockResolvedValueOnce([
      {
        id: 'team-goal-1',
        employeeId: 'emp-101',
        employeeName: 'Sarah Jenkins',
        employeeNumber: 'EMP-042',
        title: 'Optimize Database Query Indexes',
        description: 'Tune PostgreSQL execution plans',
        category: 'TEAM',
        metricType: 'PERCENTAGE',
        targetValue: 100,
        currentValue: 80,
        metricUnit: '%',
        progressPercentage: 80,
        startDate: '2026-01-01',
        dueDate: '2026-05-30',
        status: 'IN_PROGRESS',
      },
    ]);

    render(
      <MemoryRouter>
        <TeamGoalsScreen />
      </MemoryRouter>
    );

    expect(await screen.findByText('Sarah Jenkins')).toBeDefined();
    expect(screen.getByText('EMP-042')).toBeDefined();
    expect(screen.getByText('Optimize Database Query Indexes')).toBeDefined();
    expect(screen.getByText('Total Team Goals')).toBeDefined();
  });

  it('shows error state with Retry button when team goals fail to load', async () => {
    goalService.getTeamGoals.mockRejectedValueOnce(new Error('Failed to fetch'));

    render(
      <MemoryRouter>
        <TeamGoalsScreen />
      </MemoryRouter>
    );

    expect(await screen.findByText(/failed to load team goals/i)).toBeDefined();
    expect(screen.getByRole('button', { name: /retry/i })).toBeDefined();
  });

  it('opens goal detail drawer with check-in history when View button is clicked', async () => {
    goalService.getTeamGoals.mockResolvedValueOnce([
      {
        id: 'team-goal-2',
        employeeId: 'emp-102',
        employeeName: 'Michael Scott',
        employeeNumber: 'EMP-007',
        title: 'Increase Sales Leads',
        description: 'Target 50 new inbound leads',
        category: 'COMPANY',
        metricType: 'NUMERIC',
        targetValue: 50,
        currentValue: 50,
        metricUnit: 'leads',
        progressPercentage: 100,
        startDate: '2026-01-01',
        dueDate: '2026-12-31',
        status: 'ACHIEVED',
      },
    ]);
    goalService.getCheckIns.mockResolvedValueOnce([
      {
        id: 'ci-2',
        goalId: 'team-goal-2',
        currentValue: 50,
        progressPercentage: 100,
        notes: 'Reached quarterly quota of leads',
        createdAt: '2026-04-10T12:00:00Z',
      },
    ]);

    render(
      <MemoryRouter>
        <TeamGoalsScreen />
      </MemoryRouter>
    );

    const viewBtn = await screen.findByRole('button', { name: /view/i });
    fireEvent.click(viewBtn);

    expect(await screen.findByText('Goal & OKR Details')).toBeDefined();
    expect(screen.getAllByText('Increase Sales Leads').length).toBeGreaterThanOrEqual(1);
    expect(await screen.findByText('Reached quarterly quota of leads')).toBeDefined();
  });
});
