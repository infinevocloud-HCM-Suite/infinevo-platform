/**
 * Decimal comparisons on the strings the server sends (W-75.2 §6).
 *
 * Money and rates arrive as BigDecimal strings (or numbers from JSON). The salary screens only ever
 * compare them - every sum comes from the server's preview - so these helpers compare digit by digit
 * and never turn an amount into a float.
 */

const DECIMAL = /^-?\d+(\.\d+)?$/;

/** True for a plain decimal such as '600000', '12.5' or -3; false for '', 'abc', '1e5'. */
export function isDecimal(value) {
  if (value === null || value === undefined) return false;
  return DECIMAL.test(String(value).trim());
}

function parts(value) {
  const text = String(value).trim();
  const negative = text.startsWith('-');
  const body = negative ? text.slice(1) : text;
  const [whole, fraction = ''] = body.split('.');
  const intPart = whole.replace(/^0+(?=\d)/, '');
  const fracPart = fraction.replace(/0+$/, '');
  const zero = /^0*$/.test(intPart) && fracPart === '';
  return { negative: negative && !zero, intPart, fracPart };
}

function compareMagnitude(a, b) {
  if (a.intPart.length !== b.intPart.length) return a.intPart.length > b.intPart.length ? 1 : -1;
  if (a.intPart !== b.intPart) return a.intPart > b.intPart ? 1 : -1;
  const width = Math.max(a.fracPart.length, b.fracPart.length);
  const fa = a.fracPart.padEnd(width, '0');
  const fb = b.fracPart.padEnd(width, '0');
  if (fa === fb) return 0;
  return fa > fb ? 1 : -1;
}

/**
 * Compares two decimals: -1, 0 or 1. Returns null when either is not a decimal.
 *
 * @param {string|number} a
 * @param {string|number} b
 * @returns {-1|0|1|null}
 */
export function compareDecimal(a, b) {
  if (!isDecimal(a) || !isDecimal(b)) return null;
  const pa = parts(a);
  const pb = parts(b);
  if (pa.negative !== pb.negative) return pa.negative ? -1 : 1;
  const magnitude = compareMagnitude(pa, pb);
  return pa.negative ? -magnitude : magnitude;
}

/** True when the value is a decimal greater than zero. */
export function isPositiveDecimal(value) {
  return compareDecimal(value, '0') === 1;
}

/** '12.0000' -> '12', '0.7500' -> '0.75': a rate as written, without trailing zeros. */
export function trimDecimal(value) {
  if (!isDecimal(value)) return value == null ? '' : String(value);
  const text = String(value).trim();
  if (!text.includes('.')) return text;
  return text.replace(/0+$/, '').replace(/\.$/, '');
}
