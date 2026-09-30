import Swal from 'sweetalert2';

/**
 * Standard outcome alert helpers (W-45 §5, CONVENTIONS.md §4).
 * Built on SweetAlert2 for user-facing outcome messages.
 */

/**
 * Displays a success alert dialog.
 *
 * @param {string} title Dialog title
 * @param {string} [text] Dialog message
 * @returns {Promise} SweetAlert2 promise
 */
export function successMsg(title, text) {
  return Swal.fire({
    title: title || 'Success',
    text: text || '',
    icon: 'success',
    confirmButtonText: 'OK',
  });
}

/**
 * Displays an error alert dialog.
 * Accepts either (title, text) or a rejected client error object.
 * When given an error object, formats err.message and appends traceId if present.
 *
 * @param {string|Object} titleOrErr Title string or client error object { message, traceId, code }
 * @param {string} [text] Detailed error text if title is a string
 * @returns {Promise} SweetAlert2 promise
 */
const CODE_TITLES = {
  VALIDATION_FAILED: 'Validation Error',
  NOT_FOUND: 'Not Found',
  CONFLICT: 'Conflict',
  UNAUTHENTICATED: 'Authentication Required',
  FORBIDDEN: 'Access Denied',
  TENANT_NOT_BOUND: 'Tenant Not Bound',
  MODULE_NOT_ENTITLED: 'Module Not Available',
  TENANT_SUSPENDED: 'Subscription Suspended',
  INTERNAL: 'System Error',
};

export function formatCodeTitle(code) {
  if (!code) return 'Error';
  if (CODE_TITLES[code]) return CODE_TITLES[code];
  return code
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
}

export function errorMsg(titleOrErr, text) {
  let title = 'Error';
  let message = text;

  if (titleOrErr && typeof titleOrErr === 'object') {
    title = formatCodeTitle(titleOrErr.code);
    message = titleOrErr.message || 'Something went wrong';
    if (titleOrErr.traceId) {
      message = `${message} (Trace ID: ${titleOrErr.traceId})`;
    }
  } else if (typeof titleOrErr === 'string') {
    title = titleOrErr;
    message = text || '';
  }

  return Swal.fire({
    title,
    text: message,
    icon: 'error',
    confirmButtonText: 'OK',
  });
}
