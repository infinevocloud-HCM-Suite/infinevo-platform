import PropTypes from 'prop-types';

const VARIANT_CLASS_MAP = {
  success: 'pill-success',
  warning: 'pill-warning',
  danger: 'pill-danger',
  error: 'pill-danger',
  info: 'pill-info',
  purple: 'pill-purple',
  neutral: 'pill-neutral',
  default: 'pill-neutral',
};

/**
 * Modern pill badge for status, tags, and category chips (W-80 10/10 UI).
 * Renders with soft backgrounds, subtle border, rounded-full shape, and optional dot.
 */
export function PillBadge({ variant = 'neutral', dot = false, icon, children, style, className = '' }) {
  const variantClass = VARIANT_CLASS_MAP[variant] || 'pill-neutral';

  return (
    <span className={`pill-badge ${variantClass} ${className}`} style={style}>
      {dot && (
        <span
          aria-hidden="true"
          className="pill-dot"
        />
      )}
      {icon && <span style={{ display: 'inline-flex', alignItems: 'center' }}>{icon}</span>}
      {children}
    </span>
  );
}

PillBadge.propTypes = {
  variant: PropTypes.oneOf(['success', 'warning', 'danger', 'error', 'info', 'purple', 'neutral', 'default']),
  dot: PropTypes.bool,
  icon: PropTypes.node,
  children: PropTypes.node.isRequired,
  style: PropTypes.object,
  className: PropTypes.string,
};
