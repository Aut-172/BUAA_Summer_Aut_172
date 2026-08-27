import { defineConfig } from 'vite';

const gatewayUrl = process.env.VITE_GATEWAY_URL || 'http://localhost:8080';

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
        target: gatewayUrl,
        changeOrigin: true,
        secure: false
      },
      '/uploads': {
        target: gatewayUrl,
        changeOrigin: true,
        secure: false
      }
    }
  }
});
