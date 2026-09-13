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
    // A module folder must not import from a sibling module folder. Same rule the
    // backend enforces with maven-enforcer; W-03 turns this into a pipeline gate.
    'no-restricted-imports': ['error', {
      patterns: [
        { group: ['@hrms/*'], message: 'payroll and core must not import from hrms' },
        { group: ['@payroll/*'], message: 'hrms and core must not import from payroll' },
      ],
    }],
  },
  overrides: [
    {
      // The shell composes every module, so it is the one place allowed to import both.
      files: ['src/shell/**', 'src/main.jsx'],
      rules: { 'no-restricted-imports': 'off' },
    },
    {
      files: ['src/hrms/**'],
      rules: {
        'no-restricted-imports': ['error', {
          patterns: [{ group: ['@payroll/*'], message: 'hrms must not import from payroll' }],
        }],
      },
    },
    {
      files: ['src/payroll/**'],
      rules: {
        'no-restricted-imports': ['error', {
          patterns: [{ group: ['@hrms/*'], message: 'payroll must not import from hrms' }],
        }],
      },
    },
  ],
};
