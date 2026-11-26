import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MySurveys } from './MySurveys.jsx';
import { surveyService } from '../../../hrms/surveys/surveyService.js';

vi.mock('../../../hrms/surveys/surveyService.js', () => ({
  surveyService: {
    listActive: vi.fn(),
    getForTaking: vi.fn(),
    submit: vi.fn(),
  },
}));

describe('MySurveys component (W-110)', () => {
  const mockActiveSurveys = [
    {
      id: 'srv-1',
      title: 'Employee Well-being Check',
      description: 'Quick check-in on work balance',
      category: 'PULSE_POLL',
      isAnonymous: true,
      endDate: '2026-12-20',
      questionCount: 3,
      completed: false,
    },
    {
      id: 'srv-2',
      title: 'Company Culture Poll',
      description: 'Annual cultural assessment',
      category: 'GENERAL',
      isAnonymous: false,
      endDate: '2026-12-30',
      questionCount: 5,
      completed: true,
    },
  ];

  const mockSurveyDetail = {
    id: 'srv-1',
    title: 'Employee Well-being Check',
    description: 'Quick check-in on work balance',
    isAnonymous: true,
    questions: [
      {
        id: 'q-1',
        questionText: 'How is your workload this week?',
        questionType: 'RATING_1_5',
        isRequired: true,
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    surveyService.listActive.mockResolvedValue(mockActiveSurveys);
    surveyService.getForTaking.mockResolvedValue(mockSurveyDetail);
  });

  it('renders pending and completed surveys in employee self-service', async () => {
    render(<MySurveys />);

    await waitFor(() => {
      expect(screen.getByText('Employee Well-being Check')).toBeTruthy();
      expect(screen.getByText('Company Culture Poll')).toBeTruthy();
      expect(screen.getByText('Pending')).toBeTruthy();
      expect(screen.getByText('Completed')).toBeTruthy();
    });

    expect(screen.getByText('Take Survey')).toBeTruthy();
    expect(screen.getByText('Submitted')).toBeTruthy();
  });

  it('opens survey modal when Take Survey button is clicked', async () => {
    render(<MySurveys />);

    await waitFor(() => {
      expect(screen.getByText('Take Survey')).toBeTruthy();
    });

    const takeBtn = screen.getByText('Take Survey');
    fireEvent.click(takeBtn);

    await waitFor(() => {
      expect(surveyService.getForTaking).toHaveBeenCalledWith('srv-1');
      expect(screen.getByText('Submit Response')).toBeTruthy();
    });
  });
});

