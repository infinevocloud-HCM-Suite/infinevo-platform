/**
 * The logo file rules (W-73.1 §2, §9): PNG or JPG, at most 512 KB after a client-side resize to
 * 256 px on the longer side.
 */
export const LOGO_MAX_BYTES = 512 * 1024;
export const LOGO_MAX_EDGE = 256;
export const LOGO_TYPES = ['image/png', 'image/jpeg'];

/** The reason a file is refused, or null when it may be uploaded. */
export function logoFileProblem(file) {
  if (!file) return 'Choose a file';
  if (!LOGO_TYPES.includes(file.type)) return 'The logo must be a PNG or JPG image';
  if (file.size > LOGO_MAX_BYTES) return 'The logo must be at most 512 KB';
  return null;
}

/**
 * Shrinks an image so its longer side is at most `maxEdge` pixels, keeping its type and name.
 * Returns the file itself when it is already small enough, or when the browser cannot draw it
 * (no canvas in a test runner) - the server's 512 KB cap is the backstop either way.
 */
export function resizeLogo(file, maxEdge = LOGO_MAX_EDGE) {
  if (typeof window === 'undefined' || typeof window.createImageBitmap !== 'function') {
    return Promise.resolve(file);
  }
  return window
    .createImageBitmap(file)
    .then((bitmap) => {
      const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height));
      if (scale >= 1) return file;
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(bitmap.width * scale);
      canvas.height = Math.round(bitmap.height * scale);
      const context = canvas.getContext('2d');
      if (!context) return file;
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      return new Promise((resolve) => {
        canvas.toBlob(
          (blob) => resolve(blob ? new File([blob], file.name, { type: file.type }) : file),
          file.type,
          0.92,
        );
      });
    })
    .catch(() => file);
}
