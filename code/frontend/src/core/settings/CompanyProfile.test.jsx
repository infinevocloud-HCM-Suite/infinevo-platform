import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CompanyProfile } from './CompanyProfile.jsx';
import { companyProfileService } from './companyProfileService.js';
import { documentService } from '../document/documentService.js';
import * as shellScreens from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

vi.mock('./companyProfileService.js', () => ({
  companyProfileService: { get: vi.fn(), update: vi.fn() },
}));

vi.mock('../document/documentService.js', () => ({
  documentService: { upload: vi.fn(), get: vi.fn() },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(undefined),
  errorMsg: vi.fn(),
}));

function can(codes) {
  return vi.spyOn(shellScreens, 'useCan').mockImplementation((code) => codes.includes(code));
}

describe('CompanyProfile (W-73.1 §5)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
    companyProfileService.get.mockResolvedValue({
      name: 'Acme Ltd',
      tagline: null,
      logoDocumentId: null,
      logoUrl: null,
    });
  });

  it('refuses a caller without core.tenant.read', () => {
    can([]);
    render(<CompanyProfile />);
    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
    expect(companyProfileService.get).not.toHaveBeenCalled();
  });

  it('shows the name read-only, the initials when there is no logo, and no Save for a reader', async () => {
    can(['core.tenant.read']);
    render(<CompanyProfile />);

    await waitFor(() => expect(screen.getByDisplayValue('Acme Ltd')).toBeDefined());
    expect(screen.getByDisplayValue('Acme Ltd').hasAttribute('disabled')).toBe(true);
    expect(screen.getByTestId('logo-initials').textContent).toBe('AL');
    expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
    expect(screen.queryByText(/upload logo/i)).toBeNull();
  });

  it('an admin saves the tagline and a freshly uploaded logo', async () => {
    can(['core.tenant.read', 'core.tenant.manage']);
    documentService.upload.mockResolvedValue({ id: 'doc-logo-1' });
    companyProfileService.update.mockResolvedValue({
      name: 'Acme Ltd',
      tagline: 'People first',
      logoDocumentId: 'doc-logo-1',
      logoUrl: '/api/v1/documents/download?t=signed',
    });
    render(<CompanyProfile />);
    await waitFor(() => expect(screen.getByDisplayValue('Acme Ltd')).toBeDefined());

    fireEvent.change(screen.getByLabelText('Tagline'), { target: { value: '  People first ' } });

    const file = new File([new Uint8Array(100)], 'logo.png', { type: 'image/png' });
    const input = document.querySelector('input[type="file"]');
    fireEvent.change(input, { target: { files: [file] } });
    await waitFor(() => expect(documentService.upload).toHaveBeenCalledWith(expect.any(File), 'TENANT_LOGO'));

    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(companyProfileService.update).toHaveBeenCalledWith({
        tagline: 'People first',
        logoDocumentId: 'doc-logo-1',
      }),
    );
    await waitFor(() => expect(successMsg).toHaveBeenCalled());
    expect(screen.getByTestId('logo-preview').getAttribute('src')).toBe('/api/v1/documents/download?t=signed');
  });

  it('refuses a file that is not a PNG or JPG without uploading', async () => {
    can(['core.tenant.read', 'core.tenant.manage']);
    render(<CompanyProfile />);
    await waitFor(() => expect(screen.getByDisplayValue('Acme Ltd')).toBeDefined());

    fireEvent.change(document.querySelector('input[type="file"]'), {
      target: { files: [new File(['x'], 'logo.pdf', { type: 'application/pdf' })] },
    });
    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith('Logo not accepted', 'The logo must be a PNG or JPG image'));
    expect(documentService.upload).not.toHaveBeenCalled();
  });

  it('refuses a file over 512 KB without uploading', async () => {
    can(['core.tenant.read', 'core.tenant.manage']);
    render(<CompanyProfile />);
    await waitFor(() => expect(screen.getByDisplayValue('Acme Ltd')).toBeDefined());

    fireEvent.change(document.querySelector('input[type="file"]'), {
      target: { files: [new File([new Uint8Array(512 * 1024 + 1)], 'big.png', { type: 'image/png' })] },
    });
    await waitFor(() => expect(errorMsg).toHaveBeenCalledWith('Logo not accepted', 'The logo must be at most 512 KB'));
    expect(documentService.upload).not.toHaveBeenCalled();
  });

  it('Remove logo clears the document and saves null', async () => {
    can(['core.tenant.read', 'core.tenant.manage']);
    companyProfileService.get.mockResolvedValue({
      name: 'Acme Ltd',
      tagline: 'People first',
      logoDocumentId: 'doc-logo-1',
      logoUrl: '/api/v1/documents/download?t=signed',
    });
    companyProfileService.update.mockResolvedValue({ name: 'Acme Ltd', tagline: 'People first' });
    render(<CompanyProfile />);
    await waitFor(() => expect(screen.getByTestId('logo-preview')).toBeDefined());

    fireEvent.click(screen.getByRole('button', { name: /remove logo/i }));
    expect(screen.getByTestId('logo-initials').textContent).toBe('AL');

    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() =>
      expect(companyProfileService.update).toHaveBeenCalledWith({ tagline: 'People first', logoDocumentId: null }),
    );
  });
});
