---
name: start-story
description: Transition from a merged PR to starting the next story cleanly.
---

## What this command does

1. Switch to main and pull latest (`git checkout main && git pull`)
2. Read the story backlog to find the next pending story
3. Create a feature branch named after the story (e.g. `story-402-<slug>`)
4. Read the story's design notes if available (`docs/STORY-<ID>-*.md`)
5. Run `/setup-dev` to ensure Spring Boot :8080 + Next.js :3000 are healthy
6. Produce a short "here's what we're building" summary and suggest the first concrete step

## Instructions

### 1 — Sync main

```powershell
git checkout main
git pull origin main
git log --oneline -5
```

Confirm you are on main and the latest commit matches the recently merged PR.

### 2 — Read the story backlog

Read `docs/STORY-BACKLOG.md` in full. Identify the **first story whose Status is `⏳ PENDING`**. Note its ID, title, and task list.

### 3 — Create the feature branch

Derive a short kebab-case slug from the story title (3–5 words max). Create and switch to the branch:

```powershell
git checkout -b story-<ID>-<slug>
```

Example: `story-402-backend-azure-deployment`

### 4 — Read story design notes

Check whether a local design file exists for the story:

```
docs/STORY-<ID>*.md
```

If found, read it for scope, acceptance criteria, and any deferred tasks from prior stories. If not found, use the backlog task list as the source of truth.

### 5 — Run pre-flight checks

Before writing any code, run all three baseline checks as specified in `CLAUDE.md`:

**Frontend lint:**
```powershell
cd frontend/codesense-frontend
npm run lint
```

**Backend compile:**
```powershell
cd backend/codesense-backend
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\mvnw.cmd clean compile
```

**Full E2E suite:**
```powershell
cd frontend/codesense-frontend
npx playwright test --reporter=list
```

If any check fails, fix it and commit separately as `fix: pre-story lint/compile baseline` before proceeding.

### 6 — Start dev servers

Invoke `/setup-dev` to start both servers if they are not already running. Confirm:
- Spring Boot responding on `http://localhost:8080/api/auth/me`
- Next.js responding on `http://localhost:3000`

### 7 — Report and suggest first step

Write a short structured summary in this format:

---

**Story:** `STORY-<ID> — <Title>`

**Branch:** `story-<ID>-<slug>`

**What we're building:**
<2–3 sentences describing the feature, its purpose, and how it fits into the overall app.>

**Task list:**
<Paste the unchecked task items from the backlog verbatim.>

**First step:**
<One concrete coding action — name the file to create or modify and what to add.>

---

Keep the summary under 25 lines. No filler — every line should be actionable or directly informative.
