import { describe, it, expect } from 'vitest';
import { ESLint } from 'eslint';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

describe('ESLint boundary rules (W-45 §5b)', () => {
  const configFile = resolve(__dirname, '../../.eslintrc.cjs');
  const eslint = new ESLint({
    overrideConfigFile: configFile,
    useEslintrc: false,
  });

  it('rejects screen importing axios directly with exactly 1 error', async () => {
    const code = "import axios from 'axios';\nexport const a = axios;\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../core/screens/BadAxiosScreen.jsx'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-imports');
    expect(results[0].messages[0].message).toContain('axios');
  });

  it('rejects screen reading localStorage directly with exactly 1 error', async () => {
    const code = "export const token = localStorage.getItem('token');\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../core/screens/BadStorageScreen.jsx'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-globals');
    expect(results[0].messages[0].message).toContain('localStorage');
  });

  it('rejects hrms module importing from @payroll with exactly 1 error', async () => {
    const code = "import { salary } from '@payroll/salary';\nexport const s = salary;\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../hrms/screens/BadHrmsImport.jsx'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-imports');
    expect(results[0].messages[0].message).toContain('payroll');
  });

  it('rejects shared module importing from @shell with exactly 1 error', async () => {
    const code = "import { Header } from '@shell/Header';\nexport const h = Header;\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../shared/utils/BadSharedImport.js'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-imports');
    expect(results[0].messages[0].message).toContain('shell');
  });

  it('rejects three-deep relative boundary import with exactly 1 error (D-19)', async () => {
    const code = "import { leave } from '../../../hrms/leave/index.js';\nexport const l = leave;\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../core/screens/sub/deep/BadDeepImport.jsx'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-imports');
    expect(results[0].messages[0].message).toContain('hrms');
  });

  it('rejects bare @hrms alias import with exactly 1 error (D-19)', async () => {
    const code = "import hrms from '@hrms';\nexport const h = hrms;\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../core/screens/BadBareImport.jsx'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-imports');
    expect(results[0].messages[0].message).toContain('hrms');
  });

  it('rejects axios subpath import (axios/lib/axios) with exactly 1 error (D-19)', async () => {
    const code = "import axios from 'axios/lib/axios';\nexport const a = axios;\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../core/screens/BadAxiosSubpath.jsx'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-imports');
    expect(results[0].messages[0].message).toContain('axios');
  });

  it('rejects globalThis.localStorage access with exactly 1 error (D-19)', async () => {
    const code = "export const token = globalThis.localStorage.getItem('token');\n";
    const results = await eslint.lintText(code, {
      filePath: resolve(__dirname, '../core/screens/BadGlobalThisStorage.jsx'),
    });

    expect(results[0].errorCount).toBe(1);
    expect(results[0].messages[0].ruleId).toBe('no-restricted-syntax');
    expect(results[0].messages[0].message).toContain('globalThis.localStorage');
  });

  it('rejects bracket access window[\'localStorage\'] and globalThis[\'localStorage\'] (D-19)', async () => {
    const code1 = "export const t1 = window['localStorage'].getItem('token');\n";
    const results1 = await eslint.lintText(code1, {
      filePath: resolve(__dirname, '../core/screens/BadWindowBracketStorage.jsx'),
    });
    expect(results1[0].errorCount).toBe(1);
    expect(results1[0].messages[0].ruleId).toBe('no-restricted-syntax');

    const code2 = "export const t2 = globalThis['localStorage'].getItem('token');\n";
    const results2 = await eslint.lintText(code2, {
      filePath: resolve(__dirname, '../core/screens/BadGlobalThisBracketStorage.jsx'),
    });
    expect(results2[0].errorCount).toBe(1);
    expect(results2[0].messages[0].ruleId).toBe('no-restricted-syntax');
  });
});

