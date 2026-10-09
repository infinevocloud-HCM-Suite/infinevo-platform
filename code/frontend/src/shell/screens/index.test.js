/**
 * `@shell/screens` is exempt from the module-boundary lint rule (W-45 §5b), so whatever it
 * exports is importable from every module. This pins the list.
 */
import { describe, it, expect } from 'vitest';
import * as screens from './index.js';

describe('@shell/screens', () => {
  it('exports the error screens, the two feed hints and the module employee tabs, and nothing else', () => {
    expect(Object.keys(screens).sort()).toEqual(
      ['NoModules', 'NotEntitled', 'NotFound', 'Suspended', 'useCan', 'useHasModule', 'useModuleEmployeeTabs'].sort(),
    );
  });
});
