module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime',
    'plugin:react-hooks/recommended',
  ],
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  settings: { react: { version: 'detect' } },
  rules: {
    // W-45 §5b: Forbid direct axios imports; all calls go through apiClient
    'no-restricted-imports': [
      'error',
      {
        paths: [
          {
            name: 'axios',
            message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js',
          },
        ],
        patterns: [
          {
            group: ['axios/*'],
            message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js',
          },
        ],
      },
    ],
    // W-45 §5b: Forbid direct localStorage/sessionStorage access
    'no-restricted-globals': [
      'error',
      {
        name: 'localStorage',
        message: 'Direct localStorage access is forbidden. Read state from props or context.',
      },
      {
        name: 'sessionStorage',
        message: 'Direct sessionStorage access is forbidden.',
      },
    ],
    // W-45 §5b: Forbid direct window.localStorage, window.__ENV, and import.meta.env
    'no-restricted-syntax': [
      'error',
      {
        selector: "MemberExpression[object.name='window'][property.name='localStorage']",
        message: 'Direct window.localStorage access is forbidden.',
      },
      {
        selector: "MemberExpression[object.name='window'][property.value='localStorage']",
        message: "Direct window['localStorage'] access is forbidden.",
      },
      {
        selector: "MemberExpression[object.name='globalThis'][property.name='localStorage']",
        message: 'Direct globalThis.localStorage access is forbidden.',
      },
      {
        selector: "MemberExpression[object.name='globalThis'][property.value='localStorage']",
        message: "Direct globalThis['localStorage'] access is forbidden.",
      },
      {
        selector: "MemberExpression[object.name='window'][property.name='sessionStorage']",
        message: 'Direct window.sessionStorage access is forbidden.',
      },
      {
        selector: "MemberExpression[object.name='window'][property.value='sessionStorage']",
        message: "Direct window['sessionStorage'] access is forbidden.",
      },
      {
        selector: "MemberExpression[object.name='globalThis'][property.name='sessionStorage']",
        message: 'Direct globalThis.sessionStorage access is forbidden.',
      },
      {
        selector: "MemberExpression[object.name='globalThis'][property.value='sessionStorage']",
        message: "Direct globalThis['sessionStorage'] access is forbidden.",
      },
      {
        selector: "MemberExpression[object.name='window'][property.name='__ENV']",
        message: 'Direct window.__ENV access is forbidden outside src/shared/config.js.',
      },
      {
        selector: "MemberExpression[object.name='window'][property.value='__ENV']",
        message: "Direct window['__ENV'] access is forbidden outside src/shared/config.js.",
      },
      {
        selector: "MemberExpression[object.name='globalThis'][property.name='__ENV']",
        message: 'Direct globalThis.__ENV access is forbidden outside src/shared/config.js.',
      },
      {
        selector: "MemberExpression[object.name='globalThis'][property.value='__ENV']",
        message: "Direct globalThis['__ENV'] access is forbidden outside src/shared/config.js.",
      },
      {
        selector: "MetaProperty[meta.name='import'][property.name='meta']",
        message: 'Direct import.meta access is forbidden outside src/shared/config.js.',
      },
    ],
  },
  overrides: [
    {
      // src/shared/config.js is the ONE allowed reader of environment
      files: ['src/shared/config.js'],
      rules: {
        'no-restricted-globals': 'off',
        'no-restricted-syntax': 'off',
      },
    },
    {
      // shared must not import from shell or feature modules
      files: ['src/shared/**'],
      rules: {
        'no-restricted-imports': [
          'error',
          {
            paths: [
              {
                name: 'axios',
                message: 'Direct axios import is restricted. Allowed only in src/shared/api/client.js',
              },
            ],
            patterns: [
              { group: ['axios/*'], message: 'Direct axios import is restricted. Allowed only in src/shared/api/client.js' },
              { group: ['@shell', '@shell/**', '**/shell/**'], message: 'shared must not import from shell' },
              { group: ['@core', '@core/**', '**/core/**'], message: 'shared must not import from core' },
              { group: ['@hrms', '@hrms/**', '**/hrms/**'], message: 'shared must not import from hrms' },
              { group: ['@payroll', '@payroll/**', '**/payroll/**'], message: 'shared must not import from payroll' },
            ],
          },
        ],
      },
    },
    {
      // src/shared/api/client.js is the ONE allowed importer of axios
      files: ['src/shared/api/client.js'],
      rules: {
        'no-restricted-imports': [
          'error',
          {
            paths: [],
            patterns: [
              { group: ['@shell', '@shell/**', '**/shell/**'], message: 'shared must not import from shell' },
              { group: ['@core', '@core/**', '**/core/**'], message: 'shared must not import from core' },
              { group: ['@hrms', '@hrms/**', '**/hrms/**'], message: 'shared must not import from hrms' },
              { group: ['@payroll', '@payroll/**', '**/payroll/**'], message: 'shared must not import from payroll' },
            ],
          },
        ],
      },
    },
    {
      // core must not import from hrms, payroll, or shell (except @shell/screens)
      files: ['src/core/**'],
      rules: {
        'no-restricted-imports': [
          'error',
          {
            paths: [
              {
                name: 'axios',
                message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js',
              },
              {
                name: '@shell',
                message: 'core must not import from shell (except @shell/screens)',
              },
            ],
            patterns: [
              { group: ['axios/*'], message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js' },
              { group: ['@hrms', '@hrms/**', '**/hrms/**'], message: 'core must not import from hrms' },
              { group: ['@payroll', '@payroll/**', '**/payroll/**'], message: 'core must not import from payroll' },
              {
                group: [
                  '@shell/*',
                  '@shell/**',
                  '**/shell/**',
                  '!@shell/screens',
                  '!@shell/screens/**',
                  '!**/shell/screens/**',
                ],
                message: 'core must not import from shell (except @shell/screens)',
              },
            ],
          },
        ],
      },
    },
    {
      // hrms must not import from payroll or shell (except @shell/screens)
      files: ['src/hrms/**'],
      rules: {
        'no-restricted-imports': [
          'error',
          {
            paths: [
              {
                name: 'axios',
                message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js',
              },
              {
                name: '@shell',
                message: 'hrms must not import from shell (except @shell/screens)',
              },
            ],
            patterns: [
              { group: ['axios/*'], message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js' },
              { group: ['@payroll', '@payroll/**', '**/payroll/**'], message: 'hrms must not import from payroll' },
              {
                group: [
                  '@shell/*',
                  '@shell/**',
                  '**/shell/**',
                  '!@shell/screens',
                  '!@shell/screens/**',
                  '!**/shell/screens/**',
                ],
                message: 'hrms must not import from shell (except @shell/screens)',
              },
            ],
          },
        ],
      },
    },
    {
      // payroll must not import from hrms or shell (except @shell/screens)
      files: ['src/payroll/**'],
      rules: {
        'no-restricted-imports': [
          'error',
          {
            paths: [
              {
                name: 'axios',
                message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js',
              },
              {
                name: '@shell',
                message: 'payroll must not import from shell (except @shell/screens)',
              },
            ],
            patterns: [
              { group: ['axios/*'], message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js' },
              { group: ['@hrms', '@hrms/**', '**/hrms/**'], message: 'payroll must not import from hrms' },
              {
                group: [
                  '@shell/*',
                  '@shell/**',
                  '**/shell/**',
                  '!@shell/screens',
                  '!@shell/screens/**',
                  '!**/shell/screens/**',
                ],
                message: 'payroll must not import from shell (except @shell/screens)',
              },
            ],
          },
        ],
      },
    },
    {
      // The shell composes modules, but still restricts axios and env
      files: ['src/shell/**', 'src/main.jsx'],
      rules: {
        'no-restricted-imports': [
          'error',
          {
            paths: [
              {
                name: 'axios',
                message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js',
              },
            ],
            patterns: [
              { group: ['axios/*'], message: 'Direct axios import is restricted. Use apiClient from @shared/api/client.js' },
            ],
          },
        ],
      },
    },
    {
      // Tests are exempt from env/storage restrictions to allow mock setup
      files: ['src/**/*.test.{js,jsx}', 'src/test/**'],
      rules: {
        'no-restricted-globals': 'off',
        'no-restricted-syntax': 'off',
        'no-restricted-imports': 'off',
      },
    },
  ],
};
