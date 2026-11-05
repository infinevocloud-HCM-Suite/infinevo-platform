/**
 * Ant Design theme tokens (D-29, W-45 §5a, W-80 10/10 UI Redesign, W-83.1).
 * Single source of truth for design tokens across the application.
 * One place, so a restyle is not 158 screens of work.
 * Palette: Sky Blue, Royal Blue, Soft Slate, and Crisp White.
 */
export const theme = {
  token: {
    // Colour
    colorPrimary: '#2563eb', // Royal Blue
    colorPrimaryHover: '#1d4ed8',
    colorSuccess: '#059669', // Emerald
    colorWarning: '#d97706', // Amber
    colorError: '#e11d48', // Rose
    colorInfo: '#0284c7', // Sky Blue
    colorInfoBg: '#e0f2fe',
    colorInfoBorder: '#bae6fd',
    colorInfoText: '#0369a1',
    colorBgLayout: '#f8fafc', // Soft slate
    colorBgContainer: '#ffffff', // Pure white
    colorBorderSecondary: '#e2e8f0', // Hairline border
    colorBorder: '#cbd5e1',
    colorText: '#0f172a', // Dark slate
    colorTextSecondary: '#64748b', // Muted slate

    // Type
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    fontSize: 14,
    fontSizeHeading1: 36,
    fontSizeHeading2: 28,
    fontSizeHeading3: 22,
    fontSizeHeading4: 18,
    lineHeight: 1.5,

    // Space
    sizeUnit: 4,
    sizeStep: 4,
    padding: 16,
    paddingLG: 24,
    margin: 16,
    marginLG: 24,

    // Shape
    borderRadius: 8,
    borderRadiusSM: 6,
    borderRadiusLG: 12,
    controlHeight: 34,
    boxShadowSecondary: '0 1px 3px 0 rgba(15, 23, 42, 0.05), 0 1px 2px -1px rgba(15, 23, 42, 0.05)',
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 64,
      siderWidth: 230,
      siderBg: '#0f172a',
      bodyBg: '#f8fafc',
    },
    Menu: {
      darkItemBg: '#0f172a',
      darkItemSelectedBg: '#2563eb',
      itemBorderRadius: 6,
    },
    Card: {
      headerFontSize: 16,
      borderRadiusLG: 12,
      colorBorderSecondary: '#e2e8f0',
      boxShadowTertiary: '0 0 0 1px #e2e8f0, 0 1px 3px 0 rgba(15, 23, 42, 0.05)',
    },
    Table: {
      headerBg: '#f8fafc',
      headerColor: '#475569',
      rowHoverBg: '#f0f9ff',
      cellPaddingInline: 12,
      borderColor: '#f1f5f9',
      borderRadiusLG: 12,
    },
    Button: {
      borderRadius: 8,
      controlHeight: 34,
      primaryShadow: '0 1px 2px 0 rgba(37, 99, 235, 0.2)',
    },
    Tag: {
      borderRadiusSM: 12,
    },
    Input: {
      borderRadius: 8,
      activeBorderColor: '#2563eb',
      hoverBorderColor: '#93c5fd',
    },
  },
};
