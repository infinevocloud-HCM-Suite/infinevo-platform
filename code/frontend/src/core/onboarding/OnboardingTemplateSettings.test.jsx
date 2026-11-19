import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OnboardingTemplateSettings } from './OnboardingTemplateSettings.jsx';
import { onboardingService } from './onboardingService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./onboardingService.js', () => ({
  onboardingService: {
    getTemplates: vi.fn(),
    createTemplate: vi.fn(),
    updateTemplate: vi.fn(),
    deleteTemplate: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('OnboardingTemplateSettings', () => {
  const dummyTemplates = [
    {
      id: 'tmpl-1',
      title: 'Submit Identity Proof',
      description: 'Upload passport or aadhaar',
      taskType: 'DOCUMENT_UPLOAD',
      documentLabel: 'ID_PROOF',
      assignedRole: 'EMPLOYEE',
      isMandatory: true,
      dueDaysAfterJoining: 7,
      sortOrder: 0,
      isActive: true,
    },
    {
      id: 'tmpl-2',
      title: 'IT Hardware Handover',
      description: 'Issue laptop and peripherals',
      taskType: 'MANUAL_TASK',
      documentLabel: null,
      assignedRole: 'IT',
      isMandatory: true,
      dueDaysAfterJoining: 3,
      sortOrder: 1,
      isActive: true,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders loading state, then table of templates', async () => {
    onboardingService.getTemplates.mockResolvedValueOnce(dummyTemplates);

    render(<OnboardingTemplateSettings />);

    expect(await screen.findByText('Submit Identity Proof')).toBeDefined();
    expect(screen.getByText('IT Hardware Handover')).toBeDefined();
    expect(screen.getByText(/ID_PROOF/)).toBeDefined();
    expect(screen.getByText('IT')).toBeDefined();
    expect(screen.getByText('7 days')).toBeDefined();
  });

  it('renders error state with retry button on load failure', async () => {
    onboardingService.getTemplates.mockRejectedValueOnce(new Error('Network error'));

    render(<OnboardingTemplateSettings />);

    expect(await screen.findByText('Failed to load onboarding templates')).toBeDefined();
    const retryBtn = screen.getByRole('button', { name: /retry/i });
    expect(retryBtn).toBeDefined();

    onboardingService.getTemplates.mockResolvedValueOnce(dummyTemplates);
    fireEvent.click(retryBtn);

    expect(await screen.findByText('Submit Identity Proof')).toBeDefined();
  });

  it('allows opening Add Template modal and submitting new template', async () => {
    onboardingService.getTemplates.mockResolvedValue(dummyTemplates);
    onboardingService.createTemplate.mockResolvedValueOnce({
      id: 'tmpl-3',
      title: 'Company Policy Review',
      taskType: 'POLICY_ACKNOWLEDGEMENT',
    });

    render(<OnboardingTemplateSettings />);

    expect(await screen.findByText('Submit Identity Proof')).toBeDefined();
    const addBtn = screen.getByRole('button', { name: /add template/i });
    fireEvent.click(addBtn);

    expect(await screen.findByText('Add Onboarding Template')).toBeDefined();

    const titleInput = screen.getByLabelText(/title/i);
    fireEvent.change(titleInput, { target: { value: 'Company Policy Review' } });

    const okBtn = screen.getByRole('button', { name: 'OK' });
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(onboardingService.createTemplate).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Company Policy Review',
        })
      );
    });
  });

  it('allows deactivating an active template', async () => {
    onboardingService.getTemplates.mockResolvedValue(dummyTemplates);
    onboardingService.updateTemplate.mockResolvedValueOnce({
      ...dummyTemplates[0],
      isActive: false,
    });

    render(<OnboardingTemplateSettings />);

    expect(await screen.findByText('Submit Identity Proof')).toBeDefined();
    const deactivateBtns = screen.getAllByRole('button', { name: /deactivate/i });
    fireEvent.click(deactivateBtns[0]);

    // Popconfirm confirm button
    const confirmBtn = await screen.findByRole('button', { name: 'Deactivate' });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(onboardingService.updateTemplate).toHaveBeenCalledWith(
        'tmpl-1',
        { isActive: false }
      );
    });
  });

  it('swaps sortOrder when clicking Move Down', async () => {
    onboardingService.getTemplates.mockResolvedValue(dummyTemplates);
    onboardingService.updateTemplate.mockResolvedValue({});

    render(<OnboardingTemplateSettings />);

    expect(await screen.findByText('Submit Identity Proof')).toBeDefined();
    const moveDownBtns = screen.getAllByRole('button', { name: /arrow-down/i });
    fireEvent.click(moveDownBtns[0]);

    await waitFor(() => {
      expect(onboardingService.updateTemplate).toHaveBeenCalledWith('tmpl-1', { sortOrder: 1 });
      expect(onboardingService.updateTemplate).toHaveBeenCalledWith('tmpl-2', { sortOrder: 0 });
    });
  });

  it('displays server validation error on field when creation fails', async () => {
    onboardingService.getTemplates.mockResolvedValue(dummyTemplates);
    const serverErr = new Error('Template title is required');
    serverErr.response = { data: { message: 'Template title is required' } };
    onboardingService.createTemplate.mockRejectedValueOnce(serverErr);

    render(<OnboardingTemplateSettings />);

    expect(await screen.findByText('Submit Identity Proof')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: /add template/i }));

    const titleInput = await screen.findByLabelText(/title/i);
    fireEvent.change(titleInput, { target: { value: 'Valid Title' } });

    fireEvent.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(screen.getAllByText(/Template title is required/i).length).toBeGreaterThan(0);
    });
  });
});
