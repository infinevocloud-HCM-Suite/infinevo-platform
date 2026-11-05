import { describe, it, expect } from 'vitest';
import { theme } from './theme.js';

// W-83.5: density is set once here. A later edit cannot quietly undo it.
describe('theme density (W-83.5)', () => {
  it('a table row is 44 px: 10 px padding each side of a 24 px line', () => {
    expect(theme.components.Table.cellPaddingBlock).toBe(10);
    expect(theme.components.Table.cellPaddingInline).toBe(12);
    expect(theme.components.Table.cellFontSize).toBe(14);
  });

  it('a side menu row is 40 px', () => {
    expect(theme.components.Menu.itemHeight).toBe(40);
  });

  it('headings use one scale', () => {
    expect(theme.token.fontSizeHeading4).toBe(16);
    expect(theme.token.fontSizeHeading3).toBe(20);
  });
});

// W-83.7: a white side menu, Inter, borders rather than shadows.
describe('theme chrome (W-83.7)', () => {
  it('the side menu is white and the menu is light', () => {
    expect(theme.components.Layout.siderBg).toBe('#ffffff');
    expect(theme.components.Menu.itemSelectedBg).toBe('#e0f2fe');
    expect(theme.components.Menu.darkItemBg).toBeUndefined();
  });

  it('Inter comes first in the font stack', () => {
    expect(theme.token.fontFamily.startsWith("'Inter Variable'")).toBe(true);
  });

  it('no blur shadow is left in the tokens', () => {
    const all = JSON.stringify(theme);
    expect(all).not.toMatch(/rgba\(15, 23, 42/);
    expect(theme.components.Button.primaryShadow).toBe('none');
    expect(theme.components.Card.boxShadowTertiary).toBe('0 0 0 1px #e2e8f0');
  });
});
