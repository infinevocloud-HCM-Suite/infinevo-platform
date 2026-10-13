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
  // The dev server proxies /api to the backend, so the browser calls its own origin as it does
  // behind nginx in production (infra/docker/nginx/default.conf): the backend has no CORS
  // configuration and needs none. VITE_API_PROXY_TARGET is the backend as the dev server sees
  // it - http://app:8080 inside compose, http://localhost:8080 for `npm run dev` on the host.
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalized = id.replace(/\\/g, '/');
          if (!normalized.includes('/node_modules/')) {
            return;
          }
          if (normalized.includes('/node_modules/@ant-design/icons')) {
            return 'vendor-icons';
          }
          if (
            normalized.includes('/node_modules/@ant-design/cssinjs') ||
            normalized.includes('/node_modules/@ant-design/colors')
          ) {
            return 'vendor-antd-cssinjs';
          }
          if (normalized.includes('/node_modules/@rc-component/')) {
            return 'vendor-rc-component';
          }
          if (normalized.includes('/node_modules/rc-table/')) {
            return 'vendor-rc-table';
          }
          if (normalized.includes('/node_modules/rc-picker/')) {
            return 'vendor-rc-picker';
          }
          if (normalized.includes('/node_modules/rc-')) {
            return 'vendor-rc';
          }
          if (normalized.includes('/node_modules/dayjs/')) {
            return 'vendor-dayjs';
          }
          if (
            normalized.includes('/node_modules/@reduxjs') ||
            normalized.includes('/node_modules/redux')
          ) {
            return 'vendor-redux';
          }
          if (normalized.includes('/node_modules/sweetalert2')) {
            return 'vendor-swal';
          }
          if (normalized.includes('/node_modules/keycloak-js')) {
            return 'vendor-keycloak';
          }
          if (
            normalized.includes('/node_modules/react/') ||
            normalized.includes('/node_modules/react-dom/') ||
            normalized.includes('/node_modules/react-router-dom/') ||
            normalized.includes('/node_modules/scheduler/')
          ) {
            return 'vendor-react';
          }
        },
      },
    },
  },
  // `npm test` - vitest reads this file. jsdom because the shell tests render AppShell.
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.test.{js,jsx}'],
    testTimeout: 15000,
  },
});
