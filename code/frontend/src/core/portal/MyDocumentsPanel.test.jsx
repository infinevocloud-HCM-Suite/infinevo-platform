import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MyDocumentsPanel } from './MyDocumentsPanel.jsx';
import { portalService } from '@shell/portal/portalService.js';
import { documentService } from '../document/documentService.js';

vi.mock('@shell/portal/portalService.js', () => ({
  portalService: {
    getDocuments: vi.fn(),
  },
}));

vi.mock('../document/documentService.js', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    documentService: {
      link: vi.fn(),
    },
  };
});

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('MyDocumentsPanel (W-73.5 §2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists own documents with label text and downloads through a signed link', async () => {
    portalService.getDocuments.mockResolvedValue([
      {
        id: 'doc-7',
        employeeId: 'emp-1',
        kind: 'EMPLOYEE_DOCUMENT',
        label: 'ID_PROOF',
        fileName: 'passport.pdf',
        createdAt: '2026-10-07T09:30:00Z',
      },
    ]);
    documentService.link.mockResolvedValue({ url: 'https://blob.example/own', expiresAt: '2026-10-07T09:45:00Z' });
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);

    render(<MyDocumentsPanel />);

    expect(await screen.findByText('passport.pdf')).toBeDefined();
    expect(screen.getByText('ID proof')).toBeDefined();
    expect(screen.getByText('EMPLOYEE_DOCUMENT')).toBeDefined();
    expect(screen.getByTestId('panel-documents')).toBeDefined();

    fireEvent.click(document.getElementById('btn-download-doc-7'));

    await waitFor(() => {
      expect(documentService.link).toHaveBeenCalledWith('doc-7');
      expect(open).toHaveBeenCalledWith('https://blob.example/own', '_blank', 'noopener');
    });
  });

  it('shows the empty state when the employee has no documents', async () => {
    portalService.getDocuments.mockResolvedValue([]);

    render(<MyDocumentsPanel />);

    expect(await screen.findByText('No documents yet.')).toBeDefined();
  });
});
