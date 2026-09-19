import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests drive the real Angular app against the real Spring Boot API — no mocks
 * anywhere. Both servers must already be running:
 *
 *   backend:  cd backend && mvn spring-boot:run
 *   frontend: cd frontend && npm start
 *
 * Uses the machine's installed Edge (`channel: 'msedge'`) rather than downloading a
 * browser, since `npx playwright install` can't reach its CDN from this network.
 *
 * <p>The specs share one database, so they're written not to disturb each other: counts are
 * relative, published books are always EASY so the HARD filter stays predictable, and
 * manage-books deletes everything it creates. The create-book suite is the exception — its
 * books are left behind on purpose, since publishing is what it's testing — so for a run
 * against exactly the four samples, stop the backend, delete `backend/data/`, and start it
 * again.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'edge',
      use: { ...devices['Desktop Chrome'], channel: 'msedge' }
    }
  ]
});
