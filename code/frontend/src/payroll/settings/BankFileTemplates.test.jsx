import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import * as useCanModule from '@shell/screens';
import { BankFileTemplates } from './BankFileTemplates.jsx';
import { bankFileService } from '../payrun/bankFileService.js';

vi.mock('../payrun/bankFileService.js', () => ({
  bankFileService: {
    templates: vi.fn(),
    fields: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    copy: vi.fn(),
    remove: vi.fn(),
  },
}));

const BUILT_IN = { id: 'generic-neft', name: 'Generic bulk NEFT', fileFormat: 'CSV', columns: new Array(10), builtIn: true };
const MINE = {
  id: 't1',
  name: 'HDFC bulk',
  fileFormat: 'XLSX',
  columns: [{ heading: 'A', source: 'SERIAL' }],
  builtIn: false,
  updatedAt: '2026-11-25T10:00:00Z',
  updatedBy: 'officer',
};

describe('BankFileTemplates (W-88.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    bankFileService.templates.mockResolvedValue([BUILT_IN, MINE]);
    bankFileService.fields.mockResolvedValue([]);
  });

  it('lists the built-in with a lock and only Copy; the company template has Edit, Copy and Delete', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    render(<MemoryRouter><BankFileTemplates /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('HDFC bulk')).toBeDefined());
    expect(screen.getByTestId('builtin-generic-neft')).toBeDefined();
    expect(screen.queryByTestId('btn-edit-generic-neft')).toBeNull();
    expect(screen.queryByTestId('btn-delete-generic-neft')).toBeNull();
    expect(screen.getByTestId('btn-copy-generic-neft')).toBeDefined();
    expect(screen.getByTestId('btn-edit-t1')).toBeDefined();
    expect(screen.getByText('Excel')).toBeDefined();
  });

  it('copy calls the API and reloads', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    bankFileService.copy.mockResolvedValue({ name: 'Copy of Generic bulk NEFT' });
    render(<MemoryRouter><BankFileTemplates /></MemoryRouter>);
    await waitFor(() => expect(screen.getByTestId('btn-copy-generic-neft')).toBeDefined());

    fireEvent.click(screen.getByTestId('btn-copy-generic-neft'));

    await waitFor(() => expect(bankFileService.copy).toHaveBeenCalledWith('generic-neft'));
    await waitFor(() => expect(bankFileService.templates).toHaveBeenCalledTimes(2));
  });

  it('without payroll.settings.manage the list is read-only', async () => {
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(false);
    render(<MemoryRouter><BankFileTemplates /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('HDFC bulk')).toBeDefined());
    expect(screen.queryByTestId('btn-new-template')).toBeNull();
    expect(screen.queryByTestId('btn-copy-generic-neft')).toBeNull();
  });
});
