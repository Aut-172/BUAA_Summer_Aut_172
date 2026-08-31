import { defineConfig } from 'vite';

const gatewayUrl = process.env.VITE_GATEWAY_URL || 'http://localhost:8080';
const ossUrl = process.env.VITE_OSS_PROXY_TARGET || 'https://buaa-summer-life-assistant.oss-cn-heyuan.aliyuncs.com';

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
      },
      '/oss': {
        target: ossUrl,
        changeOrigin: true,
        secure: false,
        rewrite: path => path.replace(/^\/oss/, '')
      }
    }
  }
});
