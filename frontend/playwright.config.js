import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
    testDir: './e2e',
    timeout: 30000,
    expect: {
        timeout: 5000
    },
    fullyParallel: false,
    workers: 1,
    retries: process.env.CI ? 1 : 0,
    reporter: [
        ['list'],
        ['html', { outputFolder: 'playwright-report', open: 'never' }],
        ['json', { outputFile: 'test-results/e2e-results.json' }]
    ],
    use: {
        baseURL: 'http://127.0.0.1:5173',
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        video: 'off'
    },
    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] }
        }
    ]
})
