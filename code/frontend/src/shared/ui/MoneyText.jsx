import PropTypes from 'prop-types';
import { formatMoney } from './format.js';

/**
 * Renders a monetary value using `formatMoney` with tabular numeric styling (W-83.1).
 *
 * Tabular figures (`tabular-nums`) ensure uniform digit widths so values align cleanly in columns.
 * Performs no client-side arithmetic.
 */
export function MoneyText({ value, symbol = '₹', className, style }) {
  return (
    <span
      className={className}
      style={{
        fontVariantNumeric: 'tabular-nums',
        ...style,
      }}
    >
      {formatMoney(value, symbol)}
    </span>
  );
}

MoneyText.propTypes = {
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  symbol: PropTypes.string,
  className: PropTypes.string,
  style: PropTypes.object,
};

