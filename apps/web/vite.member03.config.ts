import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'
export default defineConfig({
  plugins: [vue()],
  server: { host: '127.0.0.1', proxy: { '/api': 'http://127.0.0.1:18080' } },
  build: { outDir: '.local/member03-dist', emptyOutDir: false, rollupOptions: { input: resolve(import.meta.dirname, 'tests/member03/index.html') } },
})
