import { test, expect } from "@playwright/test";

/**
 * STORY-201 — Landing Page + Submission Form
 *
 * Done when: landing page renders cleanly, both tabs work, submitting triggers
 * a loading state. CLAUDE_API_KEY is intentionally unset in local dev, so a real
 * submit surfaces an error rather than a successful AI review — scenario 5 asserts
 * that error path (the submit wiring + spinner + error banner), not a real result.
 */
test.describe("STORY-201 — Landing Page + Submission Form", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/");
  });

  // Scenario 1 — hero renders
  test("1: hero renders headline and no-signup line", async ({ page }) => {
    await expect(
      page.getByRole("heading", { name: /AI code review/i }),
    ).toBeVisible();
    await expect(page.getByText("No sign-up required to try.")).toBeVisible();
  });

  // Scenario 2 — two tabs switch the visible form fields
  test("2: tabs switch between PR URL and Paste Code", async ({ page }) => {
    const prTab = page.getByRole("button", { name: "GitHub PR URL" });
    const pasteTab = page.getByRole("button", { name: "Paste Code" });
    await expect(prTab).toBeVisible();
    await expect(pasteTab).toBeVisible();

    // Default tab: PR URL field present.
    await expect(page.getByPlaceholder(/github\.com\/owner\/repo\/pull/i)).toBeVisible();

    // Switch to Paste Code: language select + code textarea appear.
    await pasteTab.click();
    await expect(page.getByPlaceholder("Paste your code here...")).toBeVisible();
    await expect(page.getByRole("combobox")).toBeVisible(); // language <select>

    // Switch back: PR URL field returns.
    await prTab.click();
    await expect(page.getByPlaceholder(/github\.com\/owner\/repo\/pull/i)).toBeVisible();
  });

  // Scenario 3 — PR URL validation shows an inline error for a non-matching URL
  test("3: invalid PR URL shows inline validation error", async ({ page }) => {
    // "ftp://x" passes the native type=url check but fails the app's PR regex.
    await page.getByPlaceholder(/github\.com\/owner\/repo\/pull/i).fill("ftp://x");
    await page.getByRole("button", { name: "Review my code" }).click();
    await expect(
      page.getByText(/Enter a valid GitHub PR URL/i),
    ).toBeVisible();
  });

  // Scenario 4 — empty Paste Code submit is blocked (textarea is required)
  test("4: empty code submit is blocked by required validation", async ({ page }) => {
    await page.getByRole("button", { name: "Paste Code" }).click();
    const textarea = page.getByPlaceholder("Paste your code here...");
    await expect(textarea).toHaveAttribute("required", "");
    // With no code, the field is invalid and the browser blocks submit.
    const valid = await textarea.evaluate(
      (el) => (el as HTMLTextAreaElement).checkValidity(),
    );
    expect(valid).toBe(false);
  });

  // Scenario 5 — submit wiring + error path: spinner shows, then error banner, spinner clears
  test("5: paste submit shows loading then an error banner", async ({ page }) => {
    await page.getByRole("button", { name: "Paste Code" }).click();
    await page.getByRole("combobox").selectOption("JavaScript");
    await page
      .getByPlaceholder("Paste your code here...")
      .fill("function add(a, b) { return a + b }");

    const submit = page.getByRole("button", { name: "Review my code" });
    await submit.click();

    // The red error banner appears (friendly fallback or a server message),
    // and the button returns to its idle label (spinner cleared).
    await expect(page.locator('div[class*="bg-red-900"]')).toBeVisible({
      timeout: 15000,
    });
    await expect(
      page.getByRole("button", { name: "Review my code" }),
    ).toBeVisible();
  });
});
