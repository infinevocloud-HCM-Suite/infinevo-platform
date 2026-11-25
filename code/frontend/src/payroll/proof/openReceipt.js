/**
 * Opens a receipt the authenticated client fetched (D-152, closes D-95's "receipt links carried no token").
 *
 * The proof download endpoints need the bearer token, so a link straight to them answers 401. The
 * file is fetched through apiClient instead and shown from a local object URL. The tab is opened
 * inside the click, before the fetch, so a pop-up blocker lets it through; when it is blocked
 * anyway the file is saved instead.
 *
 * @param {() => Promise<Blob>} fetchBlob fetches the file through apiClient
 * @param {{ file_name?: string, content_type?: string }} doc the document's metadata
 */
export async function openReceipt(fetchBlob, doc = {}) {
  const tab = typeof window.open === 'function' ? window.open('', '_blank') : null;
  try {
    const body = await fetchBlob();
    const type = (body && body.type) || doc.content_type || 'application/octet-stream';
    const blob = new Blob([body], { type });
    const url = URL.createObjectURL(blob);
    if (tab) {
      tab.opener = null;
      tab.location.href = url;
    } else {
      const link = document.createElement('a');
      link.href = url;
      link.download = doc.file_name || 'receipt';
      document.body.appendChild(link);
      link.click();
      link.remove();
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (err) {
    if (tab) tab.close();
    throw err;
  }
}
