import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SectionTab } from './SectionTab.jsx';
import { employeeService } from '../employeeService.js';
import * as useCanModule from '@shell/screens';

vi.mock('../employeeService.js', () => ({
  employeeService: {
    section: vi.fn(),
    saveSection: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('SectionTab component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads section on mount and saves updated fields', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.section.mockResolvedValueOnce({
      nationality: 'Indian',
      fatherName: 'Ramesh Rao',
    });
    employeeService.saveSection.mockResolvedValueOnce({});

    render(<SectionTab employeeId="emp-1" sectionName="personal" />);

    await waitFor(() => {
      expect(employeeService.section).toHaveBeenCalledWith('emp-1', 'personal');
      expect(screen.getByDisplayValue('Indian')).toBeDefined();
    });

    const nationalityInput = screen.getByDisplayValue('Indian');
    fireEvent.change(nationalityInput, { target: { value: 'Global' } });

    const saveBtn = screen.getByRole('button', { name: /save personal details/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(employeeService.saveSection).toHaveBeenCalledWith(
        'emp-1',
        'personal',
        expect.objectContaining({ nationality: 'Global' })
      );
    });
  });

  it.each([
    ['contact', 'personalEmail', 'user@home.com', 'Save Contact Details'],
    ['identification', 'panNumber', 'ABCDE1234F', 'Save Identification Details'],
    ['employment', 'payGrade', 'L5', 'Save Employment Details'],
    ['bank', 'bankName', 'State Bank', 'Save Bank Details'],
  ])('loads and saves %s section', async (sectionName, fieldName, fieldValue, buttonName) => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    employeeService.section.mockResolvedValueOnce({
      [fieldName]: fieldValue,
    });
    employeeService.saveSection.mockResolvedValueOnce({});

    render(<SectionTab employeeId="emp-1" sectionName={sectionName} />);

    await waitFor(() => {
      expect(employeeService.section).toHaveBeenCalledWith('emp-1', sectionName);
      expect(screen.getByDisplayValue(fieldValue)).toBeDefined();
    });

    const saveBtn = screen.getByRole('button', {
      name: (content) => content.toLowerCase().includes(buttonName.toLowerCase()),
    });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(employeeService.saveSection).toHaveBeenCalledWith(
        'emp-1',
        sectionName,
        expect.objectContaining({ [fieldName]: fieldValue })
      );
    });
  });

  it('renders inputs as disabled and hides save button when permission is missing', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    employeeService.section.mockResolvedValueOnce({
      nationality: 'Indian',
    });

    render(<SectionTab employeeId="emp-1" sectionName="personal" />);

    await waitFor(() => {
      const input = screen.getByDisplayValue('Indian');
      expect(input.disabled || input.hasAttribute('disabled')).toBe(true);
    });

    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
  });

  it('renders read-only masked fields with reveal toggle when permission is missing', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    employeeService.section.mockResolvedValueOnce({
      panNumber: 'ABCDE1234F',
    });

    render(<SectionTab employeeId="emp-1" sectionName="identification" />);

    await waitFor(() => {
      expect(screen.getByText('••••••••••••')).toBeDefined();
    });

    // Toggle reveal
    const revealBtn = screen.getByRole('button', { name: /reveal pan number/i });
    fireEvent.click(revealBtn);

    expect(screen.getByText('ABCDE1234F')).toBeDefined();
    expect(screen.queryByText('••••••••••••')).toBeNull();

    // Toggle hide
    const hideBtn = screen.getByRole('button', { name: /hide pan number/i });
    fireEvent.click(hideBtn);

    expect(screen.getByText('••••••••••••')).toBeDefined();
  });
});

