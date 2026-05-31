# CodeSense AI — Story Backlog

## Version 1.0

## Backlog Philosophy

Each story is small enough to build in one focused session.
Stories are ordered so each one builds on the previous.
No story is vague — every task is explicit and testable.

---

## Pre-Sprint — Dev Environment Setup

### STORY-000 — Dev Environment Setup

**Status: ✅ COMPLETE**

Tasks:

- [x] Install VS Code
- [x] Install Node.js 20 LTS
- [x] Install Java 21 JDK
- [x] Install Maven
- [x] Install Git
- [x] Install Azure CLI
- [x] Create GitHub account
- [x] Create Azure account
- [x] Install VS Code extensions

Done when: `node -v`, `java -version`, `mvn -v`, `git -v`, `az -v` all respond without errors.

---

## Sprint 1 — Foundation

### STORY-101 — Initialise Backend Project

**Status: ✅ COMPLETE**

Tasks:

- [x] Create Spring Boot 3 project via start.spring.io
- [x] Set up application.yml with local dev config
- [x] Create base package structure
- [x] Add JWT, Azure SDK dependencies to pom.xml
- [x] Verify app starts with mvn spring-boot:run

Done when: Spring Boot starts on port 8080 with no errors.

---

### STORY-102 — Initialise Frontend Project

**Status: ✅ COMPLETE**

Tasks:

- [x] Create Next.js 14 project with TypeScript and Tailwind
- [x] Set up folder structure
- [x] Create base layout with empty Navbar
- [x] Set up lib/api.ts with Axios base configuration
- [x] Verify app runs with npm run dev

Done when: Next.js runs on localhost:3000 with no errors.

---

### STORY-103 — Set up PostgreSQL locally

**Status: ✅ COMPLETE**

Tasks:

- [x] Install PostgreSQL locally
- [x] Create database codesense_db
- [x] Run all four CREATE TABLE scripts
- [x] Connect Spring Boot to local DB
- [x] Verify Spring Boot connects without errors

Done when: Tables exist in DB and Spring Boot connects cleanly.

---

### STORY-104 — Email/Password Registration API

**Status: ✅ COMPLETE**

Tasks:

- [x] Create User entity and UserRepository
- [x] Create AuthController with POST /api/auth/register
- [x] Create AuthService with BCrypt password hashing
- [x] Validate: name, email (unique), password (min 8 chars, 1 number)
- [x] Return meaningful error messages for validation failures
- [x] Create SecurityConfig to permit /api/auth/\*\* endpoints

Done when: POST /api/auth/register creates user. Duplicate email returns 400. Weak password returns 400.

---

### STORY-105 — Email/Password Login + JWT

**Status: ✅ COMPLETE**

Tasks:

- [x] Create LoginRequest DTO
- [x] Create JwtService for token generation and validation
- [x] Create JwtFilter to validate JWT on every request
- [x] Create POST /api/auth/login endpoint
- [x] Return JWT in HttpOnly cookie with 24hr expiry
- [x] Create GET /api/auth/me endpoint
- [x] Create POST /api/auth/logout endpoint
- [x] Update SecurityConfig to add JwtFilter

Done when: Login returns JWT cookie. /api/auth/me returns profile. Logout clears cookie.

---

### STORY-106 — GitHub OAuth Login

**Status: ✅ COMPLETE**

Tasks:

- [x] Register GitHub OAuth App in GitHub Developer Settings
- [x] Add GitHub OAuth2 config to application.yml
- [x] Configure Spring Security OAuth2 client
- [x] Create OAuth2SuccessHandler
- [x] Test full GitHub OAuth flow locally

Done when: GitHub login completes OAuth flow, creates/finds user in DB, issues JWT cookie.

---

### STORY-107 — Login and Registration UI

**Status: ✅ COMPLETE**

Tasks:

- [x] Build /auth/login page
- [x] Build /auth/register page
- [x] Connect forms to backend API
- [x] Store auth state in React Context
- [x] Show user avatar in Navbar when logged in
- [x] Redirect to dashboard after login

Done when: User can register, login, see name in navbar, and logout from browser.

---

### STORY-108 — Secrets Hygiene

**Status: ✅ COMPLETE**

Background: STORY-106 committed a real GitHub OAuth client_secret into
`application.yml`. This story rotates the exposed secret and moves all
secrets out of source control before any further secret-bearing config is
added (Claude API key, GitHub PAT, blob storage connection string, etc.).

Tasks:

- [ ] Rotate the exposed GitHub OAuth client secret in GitHub Developer Settings *(manual — owner action required)*
- [x] Replace hardcoded secrets in application.yml with ${ENV_VAR} placeholders
      (GITHUB_CLIENT_SECRET, JWT_SECRET, DB_PASSWORD, CLAUDE_API_KEY)
- [x] Add backend/codesense-backend/.env.example documenting required vars
- [x] Update .gitignore to exclude .env and application-local.yml
- [x] Document local dev setup in README (how to populate secrets)
- [x] Verify app still boots locally with env vars set

Done when: No real secrets in committed files. App boots cleanly with secrets
sourced from environment. .env.example lists every secret the app needs.

---

## Sprint 2 — Core Value

### STORY-201 — Landing Page + Submission Form

**Status: ✅ COMPLETE**

Tasks:

- [x] Build landing page with hero section
- [x] Build SubmitForm with two tabs: GitHub PR URL and Paste Code
- [x] PR URL tab with validation
- [x] Paste Code tab with language selector
- [x] Loading state during submission

Done when: Landing page renders cleanly. Both tabs work. Submitting triggers loading state.

---

### STORY-202 — GitHub PR Diff Fetching

**Status: ✅ COMPLETE**

Tasks:

- [x] Create GitHubService to fetch PR diff via GitHub REST API
- [x] Parse PR URL to extract owner, repo, PR number
- [x] Store raw diff in Azure Blob Storage *(local filesystem impl shipped;
      Azure impl deferred to STORY-401 behind same StorageService interface)*
- [x] Handle errors: PR not found, private repo, rate limit
- [x] Write unit test with mocked GitHub API response

Done when: Given valid public GitHub PR URL, service returns code diff and blob key.

---

### STORY-203 — Claude AI Review Service

**Status: ✅ COMPLETE**

Tasks:

- [x] Add Claude API key to local config *(env var ${CLAUDE_API_KEY:}, blank default)*
- [x] Create ClaudeService with code analysis method
- [x] Build system prompt and user prompt
- [x] Call Claude API and parse JSON response
- [x] Map response to ReviewResponse DTO
- [x] Handle retry logic — max 2 retries
- [x] Write unit test with mocked Claude response

Done when: Given code string, ClaudeService returns parsed ReviewResponse with findings.

---

### STORY-204 — Review Submission Endpoint

**Status: ✅ COMPLETE**

Tasks:

- [x] Create POST /api/reviews endpoint
- [x] Orchestrate: fetch diff → call Claude → parse response
- [x] Persist Review + Finding records for logged-in users
- [x] Return result only for anonymous users
- [x] Write integration test

Done when: POST /api/reviews returns full structured review. Logged-in users have it saved.

---

### STORY-205 — Review Results Page

**Status: ✅ COMPLETE**

Tasks:

- [x] Build /review/[id] page
- [x] Build ReviewSummary component
- [x] Build ReviewCard component
- [x] Add category tabs: Bugs, Security, Performance, Quality
- [x] Add severity filter
- [x] Add copy-to-clipboard on fix snippets
- [x] Show sign-up nudge for anonymous users

Done when: After submitting code, user sees full review with categorised findings and copyable fixes.

---

## Sprint 3 — Retention

### STORY-301 — Review History Dashboard

**Status: ✅ COMPLETE**

Tasks:

- [x] Create GET /api/reviews endpoint
- [x] Create DELETE /api/reviews/{id} endpoint
- [x] Build /dashboard page (protected)
- [x] Build DashboardList component
- [x] Delete button with confirmation
- [x] Empty state with CTA
- [ ] Date range filter *(deferred — core done criteria met)*

Done when: Logged-in user sees all past reviews, can reopen and delete them.

---

### STORY-302 — Follow-up Chat Backend

**Status: ✅ COMPLETE**

Tasks:

- [x] Create POST /api/reviews/{id}/chat with SSE streaming
- [x] Build conversation context with original code
- [x] Call Claude API and stream tokens back
- [x] Persist chat messages to DB
- [x] Create GET /api/reviews/{id}/chat for history

Done when: Chat message returns streaming SSE response. History persists correctly.

---

### STORY-303 — Follow-up Chat UI

**Status: ✅ COMPLETE**

Tasks:

- [x] Build ChatPanel component on review results page
- [x] Show chat only to logged-in users
- [x] Connect to SSE endpoint — stream response token by token
- [x] Auto-scroll to latest message
- [x] Load existing chat history on revisit

Done when: Logged-in user can chat with AI about review in real time.

---

## Sprint 4 — Ship

### STORY-401 — Azure Resource Provisioning

**Status: ✅ COMPLETE**

Tasks:

- [x] Create Resource Group codesense-rg
- [x] Provision Azure Database for PostgreSQL
- [x] Run database schema SQL scripts
- [x] Provision Azure Blob Storage
- [x] Provision Azure Key Vault with all secrets
- [x] Provision Azure App Service
- [x] Provision Azure Static Web Apps
- [x] Provision Azure Application Insights

Done when: All Azure resources exist and are configured.

---

### STORY-402 — Backend Azure Deployment

**Status: ✅ COMPLETE**

Tasks:

- [x] Configure application-prod.yml
- [x] Connect App Service to Key Vault via Managed Identity
- [x] Create CI/CD pipeline (GitHub Actions — azure-pipelines replaced by .github/workflows/deploy-backend.yml)
- [x] Push to main and verify pipeline deploys
- [ ] Test all APIs against live Azure URL

Done when: Backend is live on Azure App Service.

---

### STORY-403 — Frontend Azure Deployment

**Status: ✅ COMPLETE**

Tasks:

- [x] Set NEXT_PUBLIC_API_URL to live backend URL (in workflow env)
- [x] Create .github/workflows/deploy-frontend.yml (GitHub Actions, replaced azure-pipelines approach)
- [x] Configure Azure Static Web Apps deployment token (GitHub secret AZURE_STATIC_WEB_APPS_API_TOKEN)
- [x] Push to main and verify pipeline deploys
- [x] Update FRONTEND_URL on App Service for CORS

Done when: Frontend is live and publicly accessible on Azure.

---

### STORY-404 — Polish + LinkedIn Ready

**Status: ⏳ PENDING**

Tasks:

- [ ] Write complete README.md
- [ ] Add favicon and app title
- [ ] Review all pages for mobile responsiveness
- [ ] Fix UI inconsistencies
- [ ] Add error boundary pages (404, 500)
- [ ] Full end-to-end test as anonymous user
- [ ] Full end-to-end test as logged-in user
- [ ] Take screenshots for LinkedIn post

Done when: App is polished, README complete, proud to share publicly.

---

## Backlog Summary

| Sprint     | Stories          | Status         |
| ---------- | ---------------- | -------------- |
| Pre-Sprint | STORY-000        | ✅ Complete    |
| Sprint 1   | STORY-101 to 108 | 🔄 In Progress |
| Sprint 2   | STORY-201 to 205 | 🔄 In Progress |
| Sprint 3   | STORY-301 to 303 | ⏳ Pending     |
| Sprint 4   | STORY-401 to 404 | ⏳ Pending     |
| **Total**  | **19 stories**   | **9 complete** |
