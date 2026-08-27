import { defineConfig } from 'vite';

const backendUrl = process.env.VITE_BACKEND_URL || 'http://localhost:8081';

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    include: ['src/**/*.test.{js,jsx}']
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
        secure: false
      },
      '/uploads': {
        target: backendUrl,
        changeOrigin: true,
        secure: false
      }
    }
  }
});
