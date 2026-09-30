/**
 * Ant Design theme tokens (D-29, W-45 §5a).
 * Single source of truth for design tokens across the application.
 * One place, so a restyle is not 158 screens of work.
 */
export const theme = {
  token: {
    // Colour
    colorPrimary: '#1677ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorInfo: '#1677ff',
    colorBgLayout: '#f5f5f5',
    colorBgContainer: '#ffffff',
    colorBorderSecondary: '#f0f0f0',
    colorTextSecondary: '#8c8c8c',

    // Type
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    fontSize: 14,
    fontSizeHeading1: 38,
    fontSizeHeading2: 30,
    fontSizeHeading3: 24,
    fontSizeHeading4: 20,
    lineHeight: 1.5714285714285714,

    // Space
    sizeUnit: 4,
    sizeStep: 4,
    padding: 16,
    paddingLG: 24,
    margin: 16,
    marginLG: 24,

    // Shape
    borderRadius: 6,
    borderRadiusLG: 8,
    controlHeight: 32,
    boxShadowSecondary: '0 1px 4px rgba(0,0,0,0.06)',
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 64,
      siderWidth: 220,
      siderBg: '#001529',
      bodyBg: '#f5f5f5',
    },
    Menu: {
      darkItemBg: '#001529',
      darkItemSelectedBg: '#1677ff',
    },
  },
};
