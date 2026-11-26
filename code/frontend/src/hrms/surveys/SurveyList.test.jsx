import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { SurveyList } from './SurveyList.jsx';
import { surveyService } from './surveyService.js';

vi.mock('./surveyService.js', () => ({
  surveyService: {
    list: vi.fn(),
    updateStatus: vi.fn(),
  },
}));

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
}));

describe('SurveyList component (W-110)', () => {
  const mockSurveys = [
    {
      id: 'srv-1',
      title: 'Q4 Pulse Check',
      category: 'PULSE_POLL',
      targetType: 'ALL_EMPLOYEES',
      startDate: '2026-12-01',
      endDate: '2026-12-15',
      isAnonymous: true,
      status: 'PUBLISHED',
      totalResponses: 12,
    },
    {
      id: 'srv-2',
      title: 'Manager Effectiveness',
      category: 'SATISFACTION',
      targetType: 'DEPARTMENT',
      targetName: 'Engineering',
      startDate: '2026-12-05',
      endDate: '2026-12-25',
      isAnonymous: false,
      status: 'DRAFT',
      totalResponses: 0,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    surveyService.list.mockResolvedValue(mockSurveys);
  });

  it('renders survey list with titles, categories, audience, and status badges', async () => {
    render(
      <MemoryRouter>
        <SurveyList />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Q4 Pulse Check')).toBeTruthy();
      expect(screen.getByText('Manager Effectiveness')).toBeTruthy();
      expect(screen.getByText('Anonymous')).toBeTruthy();
      expect(screen.getByText('All Employees')).toBeTruthy();
      expect(screen.getByText('Dept: Engineering')).toBeTruthy();
      expect(screen.getByText('PUBLISHED')).toBeTruthy();
      expect(screen.getByText('DRAFT')).toBeTruthy();
    });

    expect(screen.getByText('Create Survey')).toBeTruthy();
  });
});

