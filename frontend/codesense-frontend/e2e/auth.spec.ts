import { test, expect, type Page } from "@playwright/test";

/**
 * STORY-107 — Login & Registration UI
 * STORY-104 / STORY-105 — Register + Login + JWT (exercised end-to-end through the UI)
 *
 * The register flow writes a real user to PostgreSQL, so the end-to-end block
 * runs serially with a shared unique email.
 */

const GITHUB_OAUTH = "oauth2/authorization/github";

async function expectLoggedOutNavbar(page: Page) {
  await expect(page.getByRole("link", { name: "Login" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign Up" })).toBeVisible();
}

async function expectLoggedInNavbar(page: Page, name: string) {
  const nav = page.locator("nav");
  await expect(nav.getByRole("button", { name: "Logout" })).toBeVisible();
  await expect(nav.getByText(name, { exact: false })).toBeVisible();
}

test.describe("STORY-107 — Auth UI (render + client validation)", () => {
  // Scenario 6 — login page renders
  test("6: login page renders all expected elements", async ({ page }) => {
    await page.goto("/auth/login");
    await expect(page.getByRole("heading", { name: /Welcome back/i })).toBeVisible();

    const github = page.getByRole("link", { name: /Continue with GitHub/i });
    await expect(github).toBeVisible();
    await expect(github).toHaveAttribute("href", new RegExp(GITHUB_OAUTH));

    await expect(page.locator('input[name="email"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
    await expect(page.getByRole("link", { name: /Sign up free/i })).toHaveAttribute(
      "href",
      "/auth/register",
    );
  });

  // Scenario 7 — invalid login shows an error and the button recovers
  test("7: invalid login shows an error", async ({ page }) => {
    await page.goto("/auth/login");
    await page.locator('input[name="email"]').fill(`nobody-${Date.now()}@example.com`);
    await page.locator('input[name="password"]').fill("wrongpass1");
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.locator('div[class*="bg-red-900"]')).toBeVisible({
      timeout: 15000,
    });
    // Button returns from "Signing in..." to its idle label.
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });

  // Scenario 8 — register page renders
  test("8: register page renders all expected elements", async ({ page }) => {
    await page.goto("/auth/register");
    await expect(page.getByRole("heading", { name: /Create account/i })).toBeVisible();

    const github = page.getByRole("link", { name: /Continue with GitHub/i });
    await expect(github).toHaveAttribute("href", new RegExp(GITHUB_OAUTH));

    await expect(page.locator('input[name="name"]')).toBeVisible();
    await expect(page.locator('input[name="email"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();
    await expect(page.locator('input[name="confirmPassword"]')).toBeVisible();
    await expect(page.getByRole("button", { name: "Create account" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Sign in" })).toHaveAttribute(
      "href",
      "/auth/login",
    );
  });

  // Scenario 9 — password mismatch is caught client-side (no API call)
  test("9: password mismatch shows client-side error", async ({ page }) => {
    await page.goto("/auth/register");
    await page.locator('input[name="name"]').fill("QA Bot");
    await page.locator('input[name="email"]').fill(`qa+${Date.now()}@example.com`);
    await page.locator('input[name="password"]').fill("Passw0rd1");
    await page.locator('input[name="confirmPassword"]').fill("different");
    await page.getByRole("button", { name: "Create account" }).click();

    await expect(page.getByText(/Passwords do not match/i)).toBeVisible();
  });
});

test.describe.serial("STORY-104/105 — Register + Login + JWT (end-to-end)", () => {
  // Shared across the serial block. Date.now() is fine in a Node test file.
  const email = `qa+${Date.now()}@example.com`;
  const password = "Passw0rd1";
  const name = "QA Bot";

  // Scenario 10 — successful registration auto-logs-in and routes to /dashboard
  test("10: register -> auto-login -> dashboard + logged-in navbar", async ({ page }) => {
    test.info().annotations.push({ type: "credentials", description: `${email} / ${password}` });

    await page.goto("/auth/register");
    await page.locator('input[name="name"]').fill(name);
    await page.locator('input[name="email"]').fill(email);
    await page.locator('input[name="password"]').fill(password);
    await page.locator('input[name="confirmPassword"]').fill(password);
    await page.getByRole("button", { name: "Create account" }).click();

    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 20000 });
    await expectLoggedInNavbar(page, name);
  });

  // Scenario 11 — logout returns to a logged-out state
  test("11: logout returns to logged-out state", async ({ page }) => {
    // Establish session first by logging in.
    await page.goto("/auth/login");
    await page.locator('input[name="email"]').fill(email);
    await page.locator('input[name="password"]').fill(password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 20000 });

    await page.getByRole("button", { name: "Logout" }).click();
    // logout() redirects to "/".
    await expect(page).toHaveURL(/\/$/, { timeout: 20000 });
    await expectLoggedOutNavbar(page);
  });

  // Scenario 12 — re-login with the same credentials confirms JWT round-trip
  test("12: re-login with same credentials reaches dashboard", async ({ page }) => {
    await page.goto("/auth/login");
    await page.locator('input[name="email"]').fill(email);
    await page.locator('input[name="password"]').fill(password);
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 20000 });
    await expectLoggedInNavbar(page, name);
  });
});

// Scenario 13 — navbar reflects logged-out auth state on the landing page
test("13: navbar shows logged-out affordances when anonymous", async ({ page }) => {
  await page.goto("/");
  await expectLoggedOutNavbar(page);
});
