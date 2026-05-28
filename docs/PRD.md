# CodeSense AI — Product Requirements Document (PRD)

## Version 1.0

## Elevator Pitch

Any developer visits the URL, pastes a GitHub PR link or raw code, and instantly receives a structured AI review — bugs, security issues, performance problems, and quality findings — each with a suggested fix. No login required to get value. Create an account to save history and revisit past reviews.

## User Types & Their Journeys

| User Type         | Journey                                                                |
| ----------------- | ---------------------------------------------------------------------- |
| Anonymous visitor | Land → paste code → get review → optionally sign up                    |
| Registered user   | Login → paste code → get review → saved to dashboard → revisit anytime |
| Returning user    | Login → open dashboard → view past reviews → continue chatting         |

## Epics Overview

- EPIC 1 — Landing & Public Access
- EPIC 2 — Authentication (GitHub OAuth + Email/Password)
- EPIC 3 — Code Submission (PR URL + Raw paste)
- EPIC 4 — AI Review Engine
- EPIC 5 — Review Results Display
- EPIC 6 — Follow-up Chat
- EPIC 7 — Review History Dashboard
- EPIC 8 — Infrastructure & Azure DevOps

## MoSCoW Prioritization

### Must Have (MVP)

| ID  | Feature                                                                |
| --- | ---------------------------------------------------------------------- |
| M1  | Public landing page with code submission (no login needed)             |
| M2  | Submit via GitHub PR URL                                               |
| M3  | Submit via raw code paste with language selector                       |
| M4  | AI review returned with findings: bugs, security, performance, quality |
| M5  | Each finding has: severity badge, description, suggested fix snippet   |
| M6  | GitHub OAuth login                                                     |
| M7  | Email/Password registration and login                                  |
| M8  | Logged-in users have reviews auto-saved                                |
| M9  | Personal review history dashboard                                      |
| M10 | Azure DevOps CI/CD pipeline                                            |

### Should Have

| ID  | Feature                                             |
| --- | --------------------------------------------------- |
| S1  | Follow-up AI chat on any review                     |
| S2  | Overall review score / summary card                 |
| S3  | Filter findings by category or severity             |
| S4  | Copy-to-clipboard for each fix snippet              |
| S5  | Anonymous review prompt to sign up at end of review |

### Could Have

| ID  | Feature                          |
| --- | -------------------------------- |
| C1  | Export review as Markdown or PDF |
| C2  | Dark mode toggle                 |
| C3  | Re-run review on same code       |
| C4  | Share review via public link     |
| C5  | Review stats on dashboard        |

### Won't Have (v1)

| Feature                   | Reason                   |
| ------------------------- | ------------------------ |
| Auto-push fixes to GitHub | Too risky for v1         |
| GitLab / Bitbucket        | GitHub-first strategy    |
| Team workspaces           | Multi-user complexity    |
| Billing / subscriptions   | Not needed for portfolio |

## Detailed User Stories

### EPIC 1 — Landing & Public Access

**US-101 — Public landing page**

> As an anonymous visitor, I want to land on a clean page where I can immediately submit code for review without signing up.

Acceptance Criteria:

- [ ] Hero section with value proposition
- [ ] Two submission tabs: GitHub PR URL and Paste Code
- [ ] Submit button triggers AI review without login
- [ ] Soft sign-up prompt shown after anonymous review
- [ ] Fully responsive on mobile and desktop

### EPIC 2 — Authentication

**US-201 — GitHub OAuth login**

> As a developer, I want to log in with my GitHub account so I don't need a new username and password.

Acceptance Criteria:

- [ ] "Continue with GitHub" button on login page
- [ ] OAuth 2.0 flow via GitHub handled by Spring Security
- [ ] JWT token issued and stored in httpOnly cookie
- [ ] User's GitHub avatar and name shown in navbar
- [ ] New users auto-created on first login

**US-202 — Email/Password registration**

> As a developer, I want to register with my email and password.

Acceptance Criteria:

- [ ] Registration form: name, email, password, confirm password
- [ ] Password minimum 8 characters with at least one number
- [ ] Email uniqueness validated
- [ ] Account created and user logged in automatically on success

**US-203 — Login with Email/Password**

> As a registered user, I want to log in with my email and password.

Acceptance Criteria:

- [ ] Login form: email, password
- [ ] JWT token issued on success
- [ ] Friendly error on wrong credentials
- [ ] Redirect to dashboard after login

**US-204 — Logout**

> As a logged-in user, I want to log out securely.

Acceptance Criteria:

- [ ] Logout button in navbar
- [ ] JWT cookie cleared on logout
- [ ] Redirected to landing page

### EPIC 3 — Code Submission

**US-301 — Submit via GitHub PR URL**

> As a developer, I want to paste a GitHub PR URL and have the system fetch and analyse the diff automatically.

Acceptance Criteria:

- [ ] Input accepts valid GitHub PR URL
- [ ] Backend fetches PR diff via GitHub REST API
- [ ] Supports PRs up to 2,000 lines
- [ ] Loading spinner during fetch and analysis
- [ ] Review result displayed within 30 seconds

**US-302 — Submit via raw code paste**

> As a developer, I want to paste my code directly with a language selector.

Acceptance Criteria:

- [ ] Code editor with syntax highlighting
- [ ] Language dropdown: JavaScript, TypeScript, Java, Python, Go, C#
- [ ] Minimum 10 lines, maximum 500 lines
- [ ] Same review result format as PR URL submission

### EPIC 4 — AI Review Engine

**US-401 — AI analysis and structured response**

> As the system, I want to send submitted code to Claude API and receive structured review feedback.

Acceptance Criteria:

- [ ] Spring Boot sends code to Claude API
- [ ] Claude returns strict JSON with summary, score, findings
- [ ] Each finding has category, severity, lineReference, description, suggestedFix
- [ ] Response persisted for logged-in users
- [ ] Anonymous reviews held in memory only
- [ ] Retry logic on timeout — max 2 retries

### EPIC 5 — Review Results Display

**US-501 — Display structured review results**

> As a developer, I want to see my AI review in a clean organised UI.

Acceptance Criteria:

- [ ] Summary card: score, finding counts by category
- [ ] Findings grouped by category tabs
- [ ] Severity color coding: Critical, Warning, Info
- [ ] Copy button on each fix snippet
- [ ] Filter by severity
- [ ] Fully responsive

### EPIC 6 — Follow-up Chat

**US-601 — Chat with AI about a review**

> As a logged-in developer, I want to ask follow-up questions about any finding.

Acceptance Criteria:

- [ ] Chat input on review results page for logged-in users
- [ ] Context includes original code and prior messages
- [ ] Responses streamed via Server-Sent Events
- [ ] Chat history saved per review session

### EPIC 7 — Review History Dashboard

**US-701 — Personal dashboard**

> As a logged-in user, I want to see all my past reviews in one place.

Acceptance Criteria:

- [ ] Lists all past reviews with date, score, finding count
- [ ] Click any review to reopen
- [ ] Filter by date range
- [ ] Delete a review
- [ ] Empty state with CTA for new users

### EPIC 8 — Infrastructure & DevOps

**US-801 — Azure DevOps CI/CD pipeline**

> As a developer, I want automated pipelines so every merge to main deploys automatically.

Acceptance Criteria:

- [ ] Frontend pipeline: build → test → deploy to Azure Static Web Apps
- [ ] Backend pipeline: build → test → deploy to Azure App Service
- [ ] PR to main triggers build and test only
- [ ] Merge to main triggers full deploy
- [ ] Secrets managed via Azure Key Vault

## MVP Definition

MVP = EPIC 1 + EPIC 2 + EPIC 3 + EPIC 4 + EPIC 5 + EPIC 7 + EPIC 8
Follow-up chat (EPIC 6) is Sprint 2.

## Non-Functional Requirements

| Area          | Requirement                              |
| ------------- | ---------------------------------------- |
| Performance   | AI review returned within 30 seconds     |
| Security      | Passwords BCrypt hashed, JWT 24hr expiry |
| Scalability   | Backend stateless, horizontally scalable |
| Availability  | 99.5% uptime target                      |
| Observability | Azure Application Insights               |
| Accessibility | WCAG 2.1 AA compliance                   |

## Sprint Plan

| Sprint   | Focus      | Epics                                           |
| -------- | ---------- | ----------------------------------------------- |
| Sprint 1 | Foundation | Auth + Public submission + Basic review display |
| Sprint 2 | Core value | AI engine + Full review UI + Fix snippets       |
| Sprint 3 | Retention  | Dashboard + History + Follow-up chat            |
| Sprint 4 | Ship       | DevOps pipelines + Azure deploy + Polish        |
