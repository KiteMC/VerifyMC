import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'path';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
}); 