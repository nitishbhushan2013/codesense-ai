# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This is a two-app monorepo (no root-level build tooling):

- `backend/codesense-backend/` — Spring Boot 3.5 / Java 21 REST API (Maven)
- `frontend/codesense-frontend/` — Next.js 16 / React 19 / Tailwind 4 app (App Router)
- `docs/` — `ARCHITECTURE.md`, `PRD.md`, `PROJECT-BRIEF.md`, `STORY-BACKLOG.md`. Per-story design notes (`docs/STORY-*.md`) are gitignored and kept locally.

Run all commands from the relevant app subdirectory, not the repo root.

## Commands

### Backend (`backend/codesense-backend/`)

Use the Maven wrapper. On Windows use `mvnw.cmd`; the examples below use the POSIX form.

- Run dev server (port 8080): `./mvnw spring-boot:run`
- Build + run tests: `./mvnw clean install`
- Run all tests: `./mvnw test`
- Run one test class: `./mvnw test -Dtest=ClaudeServiceTest`
- Run one test method: `./mvnw test -Dtest=ClaudeServiceTest#methodName`

Code formatting is enforced by `fmt-maven-plugin` (Google Java Format), bound to the `compile` phase — every build reformats sources in place. Match that style; do not hand-fight the formatter.

### Frontend (`frontend/codesense-frontend/`)

- Dev server (port 3000): `npm run dev`
- Production build: `npm run build`
- Lint: `npm run lint`

#### End-to-end tests (Playwright)

Specs live in `frontend/codesense-frontend/e2e/` (`*.spec.ts`), config in `playwright.config.ts`. See [e2e/README.md](frontend/codesense-frontend/e2e/README.md).

- Run all: `npm run test:e2e` — headed: `npm run test:e2e:headed` — UI mode: `npm run test:e2e:ui` — last report: `npm run test:e2e:report`
- One file/scenario: `npx playwright test e2e/auth.spec.ts` / `npx playwright test -g "invalid login"`

Tests run against **already-running** servers (they do not start them): frontend on 3000, backend on 8080, PostgreSQL on 5432. `CLAUDE_API_KEY` may stay unset — the review-submit test asserts the error path, not a live AI result.

**Standing instruction: whenever a story is completed, add Playwright E2E scenarios for it to `e2e/`** (one `describe` block per story, scenarios labelled by number), then run `npm run test:e2e` and confirm green before considering the story done. Group by story (`landing.spec.ts`, `auth.spec.ts`, …); reuse the existing selector conventions (`input[name="…"]`, role/text locators, `nav`-scoped navbar checks). Do not test pending/unbuilt features.

## Architecture

### Review flow (the core feature)

A single endpoint drives everything: `POST /api/reviews` ([ReviewController.java](backend/codesense-backend/src/main/java/com/codesense/controller/ReviewController.java)) → [ReviewService.submit](backend/codesense-backend/src/main/java/com/codesense/service/ReviewService.java). `ReviewService` orchestrates the pipeline:

1. Resolve source content by `submissionType`: `"pr_url"` → [GitHubService](backend/codesense-backend/src/main/java/com/codesense/service/GitHubService.java) fetches the PR diff; `"paste"` → uses the raw `code` field directly.
2. Persist the raw content blob via [StorageService](backend/codesense-backend/src/main/java/com/codesense/service/StorageService.java) and keep the returned `blobKey`.
3. Send content to Claude via [ClaudeService.analyzeCode](backend/codesense-backend/src/main/java/com/codesense/service/ClaudeService.java), which returns a structured `ClaudeReviewResult`.
4. **Branch on auth**: if a `userId` is present, persist a `Review` + `Finding` rows and return the saved entity; if anonymous, return an ephemeral `ReviewResponse` (id `null`) that is never written to the DB.

When adding fields to a review, both branches (`persistReview` and `ephemeralResponse`) must be updated to stay in sync.

### External-service services share a pattern

`GitHubService` and `ClaudeService` both: use a Spring `WebClient` (from `webflux`) configured via `@Value` constructor injection, map HTTP failures to a typed domain exception with a `Reason` enum, and let `ReviewController` translate those `Reason`s into HTTP status codes (`mapGitHubStatus` / `mapClaudeStatus`). When adding a new failure mode, add a `Reason` and handle it in the controller's `switch` — the switches are exhaustive.

`ClaudeService` specifics: the system prompt forces a strict JSON schema; responses are run through `stripCodeFences` before parsing; retryable `Reason`s (`isRetryable()`) are retried up to `MAX_RETRIES` with exponential backoff. The model and API key come from `claude.api.*` in `application.yml` (`CLAUDE_API_KEY` env var).

### Storage is pluggable

`StorageService` is an interface. [LocalStorageService](backend/codesense-backend/src/main/java/com/codesense/service/LocalStorageService.java) is the only implementation, gated by `@ConditionalOnProperty(storage.type=local)` (the default). It writes diffs to `./local-storage/code-diffs`. Azure Blob Storage is the intended production backend (deps are present, impl is not) — add it as a second `StorageService` bean conditioned on a different `storage.type`.

### Auth (JWT-in-cookie, stateless)

[SecurityConfig](backend/codesense-backend/src/main/java/com/codesense/security/SecurityConfig.java) is stateless with CSRF disabled. Public routes: `/api/auth/**`, OAuth2 paths, and **`POST /api/reviews`** (so anonymous reviews work). Everything else requires auth.

[JwtFilter](backend/codesense-backend/src/main/java/com/codesense/security/JwtFilter.java) reads the `jwt` HttpOnly cookie, validates it, and sets a Spring `UserDetails` whose **`username` is the user's UUID string**. So in controllers, `@AuthenticationPrincipal UserDetails` → `userDetails.getUsername()` *is the userId* — that is what flows through `ReviewService` as `userId`. GitHub OAuth login goes through [OAuth2SuccessHandler](backend/codesense-backend/src/main/java/com/codesense/security/OAuth2SuccessHandler.java), which upserts the user, sets the `jwt` cookie, and redirects to the frontend dashboard.

[AuthController](backend/codesense-backend/src/main/java/com/codesense/controller/AuthController.java) (`/api/auth`) exposes `POST /register`, `POST /login` (sets the `jwt` cookie, 1-day max-age), `POST /logout` (clears it), and `GET /me` (the only authenticated route here — resolves the current user from the principal). Errors are returned as `200`/`400` with a message in `AuthResponse`, not via the `Reason`→status mapping used by the review pipeline.

### DTO / entity conventions

- DTOs (`dto/`) are Java `record`s (e.g. `ReviewRequest`, `ReviewResponse`, `FindingDto`).
- Entities (`model/`) and some DTOs use Lombok `@Builder` / `@Slf4j` / `@RequiredArgsConstructor`. Lombok is an annotation processor configured in the POM.
- JPA `ddl-auto: update` against PostgreSQL (`codesense_db`). Tests run against H2 with the `test` profile.

### Frontend

`lib/api.ts` exports a shared axios instance: `baseURL` from `NEXT_PUBLIC_API_URL` (default `http://localhost:8080`), `withCredentials: true` (cookies carry the JWT), and a response interceptor that redirects to `/auth/login` on 401. Route auth state lives in `app/auth-context.tsx`. Pages are in `app/` (App Router), shared UI in `components/`, types in `lib/types.ts`.

**Next.js 16 caveat** (see [frontend/codesense-frontend/AGENTS.md](frontend/codesense-frontend/AGENTS.md), which the frontend's own CLAUDE.md defers to): this version has breaking changes vs. older Next.js. Consult `node_modules/next/dist/docs/` before writing Next-specific code rather than relying on prior conventions.

## Implementation status vs. docs

`docs/ARCHITECTURE.md` describes the full intended system; much of it is aspirational. Currently implemented: auth (email/password + GitHub OAuth) and `POST /api/reviews` (anonymous + persisted). **Not yet built**: `GET`/`DELETE /api/reviews`, the chat feature (`ChatController`, `ChatMessage`), and Azure infrastructure. Treat the architecture doc as design intent, not current state.
