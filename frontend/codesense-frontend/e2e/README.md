# E2E tests (Playwright)

Browser tests covering the **completed** CodeSense AI stories. These drive a real
Chromium browser against running servers — they do **not** start the app for you.

## What's covered

| Spec | Stories | Scenarios |
|------|---------|-----------|
| `landing.spec.ts` | STORY-201 (Landing + Submission Form) | hero, tab switching, PR-URL validation, empty-code validation, submit/loading/error path (1–5) |
| `auth.spec.ts` | STORY-107 (Auth UI) + STORY-104/105 (register + login + JWT) | login/register page render, invalid login, password mismatch, register→auto-login→dashboard, logout, re-login, navbar auth state (6–13) |

Not covered (pending stories): STORY-205 review results page, STORY-301 dashboard
contents, chat. The tests only assert that `/dashboard` is reached and auth state
is correct, not its contents.

## Prerequisites — start both servers first

```powershell
# Terminal 1 — backend (port 8080)
cd backend/codesense-backend
./mvnw spring-boot:run

# Terminal 2 — frontend (port 3000)
cd frontend/codesense-frontend
npm run dev
```

PostgreSQL must be running on 5432. `CLAUDE_API_KEY` can stay unset — scenario 5
deliberately asserts the *error* path of a review submission, not a real AI result.

## Run the tests

From `frontend/codesense-frontend/`:

```powershell
npm run test:e2e          # headless run (list + HTML report)
npm run test:e2e:headed   # watch it drive a visible browser
npm run test:e2e:ui       # interactive Playwright UI mode
npm run test:e2e:report   # open the last HTML report
```

Run a single file or scenario:

```powershell
npx playwright test e2e/landing.spec.ts
npx playwright test -g "invalid login"
```

Point at a different frontend URL with `E2E_BASE_URL` (e.g. `E2E_BASE_URL=http://localhost:3001`).

## Notes

- The register/login block (`test.describe.serial`) creates a **real** user in
  PostgreSQL using a unique `qa+<timestamp>@example.com` email each run, so it is
  safe to re-run. The exact email is recorded as a test annotation in the report.
- Tests run serially (`workers: 1`) because the auth flow shares session state.
- First install only: `npm install -D @playwright/test && npx playwright install chromium`
  (already done in this repo).
