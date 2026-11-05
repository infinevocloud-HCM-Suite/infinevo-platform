/**
 * Ant Design theme tokens (D-29, W-45 §5a, W-80 10/10 UI Redesign, W-83.1, W-83.5, W-83.7).
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
    // W-83.7: Inter, self-hosted through @fontsource-variable/inter (imported in main.jsx)
    fontFamily:
      "'Inter Variable', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    fontSize: 14,
    fontSizeHeading1: 36,
    fontSizeHeading2: 28,
    fontSizeHeading3: 20,
    fontSizeHeading4: 16,
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
    // W-83.7: borders, not shadows; a visible focus ring
    boxShadowSecondary: 'none',
    controlOutline: 'rgba(37, 99, 235, 0.3)',
    controlOutlineWidth: 2,
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 64,
      siderWidth: 230,
      siderBg: '#ffffff', // W-83.7: white side menu
      bodyBg: '#f8fafc',
    },
    Menu: {
      // W-83.7: light menu; the selected item is blue on sky blue, the rest slate
      itemBg: '#ffffff',
      subMenuItemBg: '#ffffff',
      itemColor: '#334155',
      itemHoverBg: '#f1f5f9',
      itemSelectedBg: '#e0f2fe',
      itemSelectedColor: '#2563eb',
      iconSize: 16,
      collapsedIconSize: 18,
      itemBorderRadius: 6,
      // W-83.5: 40 px rows
      itemHeight: 40,
      itemMarginBlock: 2,
      itemMarginInline: 8,
      fontSize: 14,
    },
    Card: {
      headerFontSize: 16,
      borderRadiusLG: 12,
      colorBorderSecondary: '#e2e8f0',
      boxShadowTertiary: '0 0 0 1px #e2e8f0', // W-83.7: a 1 px ring, no shadow
    },
    Table: {
      headerBg: '#f8fafc',
      headerColor: '#475569',
      rowHoverBg: '#f0f9ff',
      // W-83.5: 10 px above and below the line, so a row with a 24 px avatar is 44 px and a
      // text-only row about 42 px; small tables 6 px
      cellPaddingBlock: 10,
      cellPaddingBlockSM: 6,
      cellPaddingInline: 12,
      cellPaddingInlineSM: 8,
      cellFontSize: 14,
      borderColor: '#f1f5f9',
      borderRadiusLG: 12,
    },
    Button: {
      borderRadius: 8,
      controlHeight: 34,
      primaryShadow: 'none', // W-83.7
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
