import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// Vite, not Create React App (D-30). Ant Design arrives from the Payroll side (D-29),
// but the build tool and the component library are independent choices - and CRA is
// deprecated and unmaintained.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@shell': fileURLToPath(new URL('./src/shell', import.meta.url)),
      '@core': fileURLToPath(new URL('./src/core', import.meta.url)),
      '@hrms': fileURLToPath(new URL('./src/hrms', import.meta.url)),
      '@payroll': fileURLToPath(new URL('./src/payroll', import.meta.url)),
      '@shared': fileURLToPath(new URL('./src/shared', import.meta.url)),
    },
  },
  server: { port: 5173 },
});
