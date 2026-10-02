/**
 * Reading a rejected call, whichever shape it arrives in (W-47.3).
 *
 * `apiClient` rejects with the flattened envelope `{ code, message, fieldErrors, status }`
 * (`src/shared/api/client.js`); a raw axios error carries the same envelope under
 * `response.data`. Both are read here so the screens look in one place.
 */
export function readError(err, fallback) {
  const data = err?.response?.data;
  return {
    status: err?.status ?? err?.response?.status,
    code: data?.code ?? err?.code,
    message: data?.message ?? err?.message ?? fallback,
    fieldErrors: err?.fieldErrors ?? data?.fieldErrors ?? {},
    traceId: err?.traceId ?? data?.traceId,
  };
}

/**
 * The rows a `400` names. The declaration endpoints report row-level failures in the message
 * only, numbering rows from 1: "Row 2: landlord_name is required", "Landlord PAN is mandatory
 * on row 3 because ...", "Rent periods overlap between row 1 (...) and row 2 (...)", and for
 * let-out properties "Property 2, Line 1: ..." (`HousingRules.java`, `DeductionRules.java`).
 *
 * @param {string} message the error message
 * @param {string} noun 'row' or 'property'
 * @returns {number[]} zero-based indexes, in the order named, without duplicates
 */
const NAMED = {
  row: /\brow\s+(\d+)\b/gi,
  property: /\bproperty\s+(\d+)\b/gi,
};

export function rowsNamedIn(message, noun = 'row') {
  if (typeof message !== 'string' || message === '') return [];
  const pattern = NAMED[noun];
  if (!pattern) return [];
  const found = [];
  for (const match of message.matchAll(pattern)) {
    const index = Number(match[1]) - 1;
    if (index >= 0 && !found.includes(index)) found.push(index);
  }
  return found;
}

/**
 * Maps a rejected save to `{ [rowIndex]: message }` for the rows it names; empty when it names
 * none.
 */
export function rowErrorsFrom(err, noun = 'row') {
  const { status, message } = readError(err, '');
  if (status !== undefined && status !== 400) return {};
  const errors = {};
  rowsNamedIn(message, noun).forEach((index) => {
    errors[index] = message;
  });
  return errors;
}
