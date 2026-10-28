import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig } from '@playwright/test'

const workspaceEnv = resolve(import.meta.dirname, '../../.env')
if (existsSync(workspaceEnv)) process.loadEnvFile(workspaceEnv)

export default defineConfig({
  testDir: './e2e',
  testIgnore: 'member2-acceptance.spec.ts',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  preserveOutput: 'never',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    browserName: 'chromium',
    headless: true,
    launchOptions: { channel: 'msedge' },
    trace: 'off',
  },
  webServer: {
    command: 'pnpm.cmd exec vite --host 127.0.0.1 --port 5173 --strictPort',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
})
