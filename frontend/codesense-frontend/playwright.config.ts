import { defineConfig, devices } from "@playwright/test";

/**
 * E2E config for CodeSense AI completed-story tests.
 *
 * Prerequisites (these tests run against ALREADY-RUNNING servers — they do not
 * start them for you):
 *   - Frontend dev server on http://localhost:3000   (cd frontend/codesense-frontend && npm run dev)
 *   - Backend  on http://localhost:8080              (cd backend/codesense-backend && ./mvnw spring-boot:run)
 *   - PostgreSQL on 5432
 *
 * Override the base URL with E2E_BASE_URL if you run the frontend elsewhere.
 */
export default defineConfig({
  testDir: "./e2e",
  // Auth flow tests register + log in a real user, so run serially for deterministic state.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL || "http://localhost:3000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
