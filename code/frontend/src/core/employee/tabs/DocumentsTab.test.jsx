import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DocumentsTab } from './DocumentsTab.jsx';
import { documentService, MAX_DOCUMENT_BYTES } from '../../document/documentService.js';
import { successMsg } from '@shared/ui/msgHelper.js';
import * as useCanModule from '@shell/screens';

vi.mock('../../document/documentService.js', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    documentService: {
      listForEmployee: vi.fn(),
      uploadForEmployee: vi.fn(),
      link: vi.fn(),
      remove: vi.fn(),
    },
  };
});

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

const offerLetter = {
  id: 'doc-1',
  fileName: 'offer-letter.pdf',
  label: 'OFFER_LETTER',
  sizeBytes: 250 * 1024,
  // The server resolves the uploader's subject to their account name (W-73.5 merge review F-1).
  uploadedBy: 'Hema Iyer',
  uploadedAt: '2026-10-07T09:30:00Z',
};

async function openDrawer() {
  fireEvent.click(await screen.findByRole('button', { name: /upload/i }));
  await waitFor(() => expect(document.getElementById('btn-submit-upload')).not.toBeNull());
}

async function chooseLabel(text) {
  const selector = document.querySelector('#select-document-label')?.closest('.ant-select')?.querySelector('.ant-select-selector');
  fireEvent.mouseDown(selector);
  fireEvent.click(await screen.findByTitle(text));
}

// antd's Upload hands the file to onChange asynchronously (after beforeUpload settles), so wait
// for it to reach the list before submitting.
async function chooseFile(file) {
  const input = document.querySelector('.ant-drawer input[type=file]');
  fireEvent.change(input, { target: { files: [file] } });
  await waitFor(() => expect(document.querySelector('.ant-upload-list')?.textContent).toContain(file.name));
}

describe('DocumentsTab (W-73.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    documentService.listForEmployee.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the empty state when the employee has no documents', async () => {
    render(<DocumentsTab employeeId="emp-1" />);

    expect(await screen.findByText('No documents uploaded yet.')).toBeDefined();
    expect(documentService.listForEmployee).toHaveBeenCalledWith('emp-1');
  });

  it('renders each document with its file name, label text, size and uploader', async () => {
    documentService.listForEmployee.mockResolvedValue([offerLetter]);

    render(<DocumentsTab employeeId="emp-1" />);

    expect(await screen.findByText('offer-letter.pdf')).toBeDefined();
    expect(screen.getByText('Offer letter')).toBeDefined();
    expect(screen.getByText('250.0 KB')).toBeDefined();
    expect(screen.getByText('Hema Iyer')).toBeDefined();
  });

  it('requires a label before uploading', async () => {
    render(<DocumentsTab employeeId="emp-1" />);
    await openDrawer();

    fireEvent.click(document.getElementById('btn-submit-upload'));

    await waitFor(() => {
      expect(document.getElementById('error-document-label')?.textContent).toBe('Choose a label');
    });
    expect(documentService.uploadForEmployee).not.toHaveBeenCalled();
  });

  it('refuses a file over 10 MB with the size message', async () => {
    render(<DocumentsTab employeeId="emp-1" />);
    await openDrawer();

    await chooseLabel('ID proof');
    const big = new File(['x'], 'scan.pdf', { type: 'application/pdf' });
    Object.defineProperty(big, 'size', { value: MAX_DOCUMENT_BYTES + 1 });
    await chooseFile(big);

    fireEvent.click(document.getElementById('btn-submit-upload'));

    await waitFor(() => {
      expect(document.getElementById('error-document-file')?.textContent).toBe('The file is larger than 10 MB');
    });
    expect(documentService.uploadForEmployee).not.toHaveBeenCalled();
  });

  it('uploads a valid file with its label and reloads the list', async () => {
    documentService.uploadForEmployee.mockResolvedValue({ id: 'doc-2' });

    render(<DocumentsTab employeeId="emp-1" />);
    await openDrawer();
    expect(documentService.listForEmployee).toHaveBeenCalledTimes(1);

    await chooseLabel('ID proof');
    const passport = new File(['pdf'], 'passport.pdf', { type: 'application/pdf' });
    await chooseFile(passport);

    fireEvent.click(document.getElementById('btn-submit-upload'));

    await waitFor(() => {
      expect(documentService.uploadForEmployee).toHaveBeenCalledWith(expect.any(File), 'emp-1', 'ID_PROOF');
    });
    const sent = documentService.uploadForEmployee.mock.calls[0][0];
    expect(sent.name).toBe('passport.pdf');
    await waitFor(() => expect(documentService.listForEmployee).toHaveBeenCalledTimes(2));
    expect(successMsg).toHaveBeenCalledWith('Document uploaded', 'passport.pdf filed as ID proof.');
  });

  it('downloads through a signed link opened in a new tab', async () => {
    documentService.listForEmployee.mockResolvedValue([offerLetter]);
    documentService.link.mockResolvedValue({ url: 'https://blob.example/signed', expiresAt: '2026-10-07T09:45:00Z' });
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);

    render(<DocumentsTab employeeId="emp-1" />);
    fireEvent.click(await screen.findByRole('button', { name: /download/i }));

    await waitFor(() => {
      expect(documentService.link).toHaveBeenCalledWith('doc-1');
      expect(open).toHaveBeenCalledWith('https://blob.example/signed', '_blank', 'noopener');
    });
  });

  it('hides Delete when the caller cannot update the employee', async () => {
    documentService.listForEmployee.mockResolvedValue([offerLetter]);
    vi.spyOn(useCanModule, 'useCan').mockImplementation((perm) => perm !== 'core.employee.update');

    render(<DocumentsTab employeeId="emp-1" />);

    expect(await screen.findByText('offer-letter.pdf')).toBeDefined();
    expect(screen.getByRole('button', { name: /download/i })).toBeDefined();
    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
  });

  it('shows Delete when the caller holds both update and delete', async () => {
    documentService.listForEmployee.mockResolvedValue([offerLetter]);

    render(<DocumentsTab employeeId="emp-1" />);

    expect(await screen.findByRole('button', { name: /delete/i })).toBeDefined();
  });
});
