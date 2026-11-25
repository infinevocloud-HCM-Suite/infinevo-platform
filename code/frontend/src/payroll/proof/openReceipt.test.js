import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { openReceipt } from './openReceipt';

describe('openReceipt (D-152: receipts open with the token, never from a bare API link)', () => {
  let createObjectURL;
  let revokeObjectURL;

  beforeEach(() => {
    createObjectURL = vi.fn(() => 'blob:local/1');
    revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
  });

  afterEach(() => vi.restoreAllMocks());

  it('opens the tab inside the click, then points it at a local copy of the fetched file', async () => {
    const tab = { location: { href: '' }, close: vi.fn(), opener: 'x' };
    vi.spyOn(window, 'open').mockReturnValue(tab);
    const fetchBlob = vi.fn().mockResolvedValue(new Blob(['%PDF-'], { type: 'application/pdf' }));

    await openReceipt(fetchBlob, { file_name: 'rent.pdf', content_type: 'application/pdf' });

    expect(window.open).toHaveBeenCalledWith('', '_blank');
    expect(fetchBlob).toHaveBeenCalled();
    expect(createObjectURL.mock.calls[0][0].type).toBe('application/pdf');
    expect(tab.location.href).toBe('blob:local/1');
    expect(tab.opener).toBeNull();
  });

  it('saves the file when the tab is blocked', async () => {
    vi.spyOn(window, 'open').mockReturnValue(null);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await openReceipt(() => Promise.resolve(new Blob(['x'])), { file_name: 'r.png', content_type: 'image/png' });
    expect(click).toHaveBeenCalled();
    expect(createObjectURL.mock.calls[0][0].type).toBe('image/png');
  });

  it('closes the tab and rethrows when the fetch fails', async () => {
    const tab = { location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(tab);
    const failure = { status: 404, message: 'No such file' };
    await expect(openReceipt(() => Promise.reject(failure), {})).rejects.toBe(failure);
    expect(tab.close).toHaveBeenCalled();
  });
});
