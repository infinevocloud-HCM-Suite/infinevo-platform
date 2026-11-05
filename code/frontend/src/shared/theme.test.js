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
