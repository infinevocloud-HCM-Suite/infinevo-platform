import PropTypes from 'prop-types';
import { Card, Typography, theme } from 'antd';

const { Text } = Typography;

/**
 * Modern 10/10 SaaS Metric Card (W-80 Standard).
 * Displays a bold metric KPI with soft hover lift, optional icon badge, subtitle, and badge trend.
 */
export function MetricCard({
  title,
  value,
  subtitle,
  icon,
  badge,
  iconBg,
  iconColor,
  onClick,
  style,
}) {
  const { token } = theme.useToken();

  return (
    <Card
      className={`saas-card ${onClick ? 'elevate-card' : ''}`}
      onClick={onClick}
      style={{
        borderRadius: 12,
        border: `1px solid ${token.colorBorderSecondary}`,
        padding: 0,
        ...style,
      }}
      styles={{ body: { padding: '16px 20px' } }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            <Text
              style={{
                fontSize: 13,
                fontWeight: 600,
                color: token.colorTextSecondary,
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}
            >
              {title}
            </Text>
            {badge && <span>{badge}</span>}
          </div>
          <div
            style={{
              fontSize: 26,
              fontWeight: 800,
              color: token.colorText,
              letterSpacing: '-0.02em',
              lineHeight: 1.2,
            }}
          >
            {value}
          </div>
          {subtitle && (
            <div style={{ marginTop: 4 }}>
              <Text style={{ fontSize: 12, color: token.colorTextSecondary }}>{subtitle}</Text>
            </div>
          )}
        </div>
        {icon && (
          <div
            className="metric-card-icon"
            style={{
              ...(iconBg ? { background: iconBg } : {}),
              ...(iconColor ? { color: iconColor } : {}),
            }}
          >
            {icon}
          </div>
        )}
      </div>
    </Card>
  );
}

MetricCard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  subtitle: PropTypes.string,
  icon: PropTypes.node,
  badge: PropTypes.node,
  iconBg: PropTypes.string,
  iconColor: PropTypes.string,
  onClick: PropTypes.func,
  style: PropTypes.object,
};
