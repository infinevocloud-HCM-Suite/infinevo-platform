import { describe, it, expect } from 'vitest';
import { theme } from './theme.js';

describe('theme design tokens (W-83.5)', () => {
  it('enforces table density: cellPaddingBlock is 10 and cellPaddingBlockSM is 6', () => {
    expect(theme.components.Table.cellPaddingBlock).toBe(10);
    expect(theme.components.Table.cellPaddingInline).toBe(12);
    expect(theme.components.Table.cellPaddingBlockSM).toBe(6);
    expect(theme.components.Table.cellFontSize).toBe(14);
  });

  it('enforces menu density: itemHeight is 40 and itemMarginBlock is 2', () => {
    expect(theme.components.Menu.itemHeight).toBe(40);
    expect(theme.components.Menu.itemMarginBlock).toBe(2);
    expect(theme.components.Menu.itemMarginInline).toBe(8);
    expect(theme.components.Menu.fontSize).toBe(14);
  });

  it('enforces single heading scale for h3 and h4', () => {
    expect(theme.token.fontSizeHeading3).toBe(20);
    expect(theme.token.fontSizeHeading4).toBe(16);
  });
});

