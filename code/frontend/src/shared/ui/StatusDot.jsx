import PropTypes from 'prop-types';
import { Typography, theme } from 'antd';

const { Text } = Typography;

/**
 * A status as an 8 px dot and plain text (W-83.7). Quieter than a filled Tag in a table, where
 * every row carries one. The colour comes from a theme token, never a literal.
 *
 *   <StatusDot tone="success">Active</StatusDot>
 */
export function StatusDot({ tone, children, ...rest }) {
  const { token } = theme.useToken();
  const colour =
    {
      success: token.colorSuccess,
      warning: token.colorWarning,
      error: token.colorError,
      processing: token.colorPrimary,
    }[tone] || token.colorTextQuaternary;

  return (
    <Text {...rest} style={{ display: 'inline-flex', alignItems: 'center', gap: token.marginXS, whiteSpace: 'nowrap' }}>
      <span
        aria-hidden="true"
        style={{
          width: token.sizeXS,
          height: token.sizeXS,
          borderRadius: '50%',
          background: colour,
          flexShrink: 0,
        }}
      />
      {children}
    </Text>
  );
}

StatusDot.propTypes = {
  tone: PropTypes.oneOf(['success', 'warning', 'error', 'processing', 'default']),
  children: PropTypes.node.isRequired,
};
