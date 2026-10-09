import { describe, it, expect, vi, beforeEach } from 'vitest';
import { companyProfileService } from './companyProfileService.js';
import { logoFileProblem, resizeLogo } from './logoImage.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), put: vi.fn() },
}));

describe('companyProfileService (W-73.1)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('reads the signed-in tenant profile', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { name: 'Acme Ltd' } });
    expect(await companyProfileService.get()).toEqual({ name: 'Acme Ltd' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/tenants/current/profile');
  });

  it('writes the tagline and the logo document', async () => {
    apiClient.put.mockResolvedValueOnce({ data: { tagline: 'x' } });
    expect(await companyProfileService.update({ tagline: 'x', logoDocumentId: null })).toEqual({ tagline: 'x' });
    expect(apiClient.put).toHaveBeenCalledWith('/v1/tenants/current/profile', { tagline: 'x', logoDocumentId: null });
  });
});

describe('logo file rules (W-73.1 §2, §9)', () => {
  it('accepts a small PNG or JPG and refuses anything else', () => {
    expect(logoFileProblem(new File(['x'], 'a.png', { type: 'image/png' }))).toBeNull();
    expect(logoFileProblem(new File(['x'], 'a.jpg', { type: 'image/jpeg' }))).toBeNull();
    expect(logoFileProblem(new File(['x'], 'a.svg', { type: 'image/svg+xml' }))).toMatch(/PNG or JPG/);
    expect(logoFileProblem(new File([new Uint8Array(512 * 1024 + 1)], 'a.png', { type: 'image/png' }))).toMatch(
      /512 KB/,
    );
    expect(logoFileProblem(null)).toBe('Choose a file');
  });

  it('returns the file itself where the browser cannot draw it', async () => {
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    expect(await resizeLogo(file)).toBe(file);
  });
});
