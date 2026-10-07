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
  describe('Employment section (D-40, D-41)', () => {
    it('renders time zone as a searchable select defaulting to the browser zone when blank', async () => {
      vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
      const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      employeeService.section.mockResolvedValueOnce({ payGrade: 'L5' });
      employeeService.saveSection.mockResolvedValueOnce({});

      render(<SectionTab employeeId="emp-1" sectionName="employment" />);

      await waitFor(() => expect(screen.getByDisplayValue('L5')).toBeDefined());

      const zoneInput = document.getElementById('field-timeZone');
      expect(zoneInput.getAttribute('role')).toBe('combobox');
      // A Select, not a text box: the typed text is a search, not the value.
      expect(zoneInput.closest('.ant-select-show-search')).toBeTruthy();
      await waitFor(() => expect(screen.getByTitle(browserZone)).toBeDefined());

      fireEvent.click(screen.getByRole('button', { name: /save employment details/i }));
      await waitFor(() =>
        expect(employeeService.saveSection).toHaveBeenCalledWith(
          'emp-1',
          'employment',
          expect.objectContaining({ timeZone: browserZone })
        )
      );
    });

    it('shows a stored zone and lets the user search and pick another', async () => {
      vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
      employeeService.section.mockResolvedValueOnce({ timeZone: 'Asia/Kolkata' });
      employeeService.saveSection.mockResolvedValueOnce({});

      render(<SectionTab employeeId="emp-1" sectionName="employment" />);

      await waitFor(() => expect(screen.getByTitle('Asia/Kolkata')).toBeDefined());

      const zoneInput = document.getElementById('field-timeZone');
      fireEvent.mouseDown(zoneInput);
      fireEvent.change(zoneInput, { target: { value: 'Europe/Lon' } });
      const option = await waitFor(() => {
        const found = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
          (el) => el.getAttribute('title') === 'Europe/London'
        );
        if (!found) throw new Error('Europe/London not offered');
        return found;
      });
      fireEvent.click(option);

      fireEvent.click(screen.getByRole('button', { name: /save employment details/i }));
      await waitFor(() =>
        expect(employeeService.saveSection).toHaveBeenCalledWith(
          'emp-1',
          'employment',
          expect.objectContaining({ timeZone: 'Europe/London' })
        )
      );
    });

    it('does not invent a zone for a read-only viewer', async () => {
      vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
      employeeService.section.mockResolvedValueOnce({ payGrade: 'L5' });

      render(<SectionTab employeeId="emp-1" sectionName="employment" />);

      await waitFor(() => expect(screen.getByDisplayValue('L5')).toBeDefined());
      const select = document.getElementById('field-timeZone').closest('.ant-select');
      expect(select.querySelector('.ant-select-selection-item')).toBeNull();
    });

    it('renders shift start and end as HH:mm time pickers and saves HH:mm', async () => {
      vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
      employeeService.section.mockResolvedValueOnce({
        timeZone: 'Asia/Kolkata',
        shiftStartTime: '09:00:00',
        shiftEndTime: '18:00',
      });
      employeeService.saveSection.mockResolvedValueOnce({});

      render(<SectionTab employeeId="emp-1" sectionName="employment" />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('09:00')).toBeDefined();
        expect(screen.getByDisplayValue('18:00')).toBeDefined();
      });
      const start = document.getElementById('field-shiftStartTime');
      expect(start.closest('.ant-picker')).toBeTruthy();
      expect(start.getAttribute('placeholder')).toBe('HH:mm');

      fireEvent.mouseDown(start);
      fireEvent.change(start, { target: { value: '10:30' } });
      fireEvent.keyDown(start, { key: 'Enter', code: 'Enter' });

      fireEvent.click(screen.getByRole('button', { name: /save employment details/i }));
      await waitFor(() =>
        expect(employeeService.saveSection).toHaveBeenCalledWith(
          'emp-1',
          'employment',
          expect.objectContaining({ shiftStartTime: '10:30', shiftEndTime: '18:00' })
        )
      );
    });

    it('carries employment type, probation end and notice period', async () => {
      vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
      employeeService.section.mockResolvedValueOnce({
        timeZone: 'Asia/Kolkata',
        employmentType: 'CONTRACT',
        probationEndDate: '2026-10-01',
        noticePeriodDays: 30,
      });
      employeeService.saveSection.mockResolvedValueOnce({});

      render(<SectionTab employeeId="emp-1" sectionName="employment" />);

      await waitFor(() => {
        expect(screen.getByTitle('Contract')).toBeDefined();
        expect(screen.getByDisplayValue('2026-10-01')).toBeDefined();
        expect(screen.getByDisplayValue('30')).toBeDefined();
      });

      const notice = document.getElementById('field-noticePeriodDays');
      fireEvent.change(notice, { target: { value: '60' } });
      fireEvent.blur(notice);

      fireEvent.click(screen.getByRole('button', { name: /save employment details/i }));
      await waitFor(() =>
        expect(employeeService.saveSection).toHaveBeenCalledWith(
          'emp-1',
          'employment',
          expect.objectContaining({
            employmentType: 'CONTRACT',
            probationEndDate: '2026-10-01',
            noticePeriodDays: 60,
          })
        )
      );
    });
  });
});

