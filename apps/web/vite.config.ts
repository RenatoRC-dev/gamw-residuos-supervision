import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const integracion = process.env.GAMW_WEB_INTEGRACION === 'true';
  const env = integracion ? process.env : loadEnv(mode, process.cwd(), '');
  return {
    envDir: integracion ? false : undefined,
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: env.BACKEND_PROXY_TARGET || 'http://127.0.0.1:8080',
        },
      },
    },
    test: {
      include: ['src/**/*.test.{ts,tsx}'],
      environment: 'jsdom',
      setupFiles: ['./src/test-setup.ts'],
      clearMocks: true,
    },
  };
});
