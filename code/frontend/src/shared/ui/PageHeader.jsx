import PropTypes from 'prop-types';
import { Button, Tag, Typography, theme } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

/**
 * Reusable PageHeader for all screens (W-83.1, W-80.1).
 *
 * Provides title (level 4), optional subtitle, real data count badge,
 * onBack button, and actions area.
 * The outer row wraps (`flexWrap: 'wrap'`) so actions drop to the next line on mobile phones.
 */
export function PageHeader({ title, subtitle, count, onBack, backLabel, backProps, actions }) {
  const { token } = theme.useToken();

  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: token.marginSM,
        paddingBottom: token.paddingSM,
        marginBottom: token.margin,
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: token.marginSM,
          flexWrap: 'wrap',
        }}
      >
        {onBack && (
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={onBack}
            aria-label={backLabel || 'Back'}
            {...(backProps || {})}
          >
            {backLabel || null}
          </Button>
        )}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: token.marginXS }}>
            <Title level={4} style={{ margin: 0 }}>
              {title}
            </Title>
            {count && (
              <Tag
                style={{
                  backgroundColor: token.colorInfoBg,
                  borderColor: token.colorInfoBorder,
                  color: token.colorInfoText,
                  marginInlineStart: token.marginXXS,
                }}
              >
                {count}
              </Tag>
            )}
          </div>
          {subtitle && (
            <div style={{ marginTop: token.marginXXS }}>
              {typeof subtitle === 'string' ? (
                <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                  {subtitle}
                </Text>
              ) : (
                subtitle
              )}
            </div>
          )}
        </div>
      </div>
      {actions && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: token.marginSM,
            flexWrap: 'wrap',
          }}
        >
          {actions}
        </div>
      )}
    </div>
  );
}

PageHeader.propTypes = {
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.node,
  count: PropTypes.string,
  onBack: PropTypes.func,
  backLabel: PropTypes.string,
  backProps: PropTypes.object,
  actions: PropTypes.node,
};

