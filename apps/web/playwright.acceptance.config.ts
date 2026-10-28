import { defineConfig } from '@playwright/test'
import base from './playwright.config'

export default defineConfig({
  ...base,
  testMatch: 'member2-acceptance.spec.ts',
  testIgnore: [],
  timeout: 120_000,
  use: { ...base.use, baseURL: 'http://127.0.0.1:5174', screenshot: 'off', video: 'off', actionTimeout: 10_000 },
  webServer: {
    command: 'pnpm.cmd exec vite --config vite.acceptance.config.ts --host 127.0.0.1 --port 5174 --strictPort',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
