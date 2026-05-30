---
name: complete-story
description: When a story is done — E2E tests green, lint passing — commit all changes on a feature branch, push, and open a GitHub PR against main.
---

## What this skill does

1. Verifies the story is actually done (lint + E2E green)
2. Creates a feature branch named `story-<ID>-<slug>` if not already on one
3. Stages and commits all changes with a conventional commit message
4. Pushes the branch to origin
5. Opens a PR against `main` via `gh pr create`

## Pre-flight checklist (run these first, stop if any fail)

### Lint
```powershell
cd frontend\codesense-frontend
npm run lint
```
Must exit 0. Fix errors before continuing.

### E2E tests
```powershell
cd frontend\codesense-frontend
npx playwright test --reporter=list
```
Must be all green. Fix failures before continuing.

### Backend compile
```powershell
cd backend\codesense-backend
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\mvnw.cmd clean compile
```
Must exit BUILD SUCCESS.

## Step 1 — Create feature branch (skip if already on a story branch)

```powershell
git status
git branch --show-current
```

If on `main`, create a branch:
```powershell
git checkout -b story-<STORY_ID>-<short-slug>
```
Example: `story-301-review-history-dashboard`

## Step 2 — Stage and commit

Review what changed:
```powershell
git diff --stat
git status
```

Stage specific files (never `git add -A` — avoid committing `.env` or local secrets):
```powershell
git add <file1> <file2> ...
```

Commit using the project's conventional style:
```powershell
git commit -m "$(cat <<'EOF'
feat: STORY-<ID> <short description of what was built>

<optional body: key decisions or non-obvious choices>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

## Step 3 — Push

```powershell
git push -u origin HEAD
```

## Step 4 — Create PR

```powershell
gh pr create --title "feat: STORY-<ID> <title>" --body "$(cat <<'EOF'
## Summary
- <bullet 1>
- <bullet 2>
- <bullet 3>

## Story
STORY-<ID> — <Story Name>

## Test plan
- [ ] Backend: `./mvnw.cmd clean compile` passes
- [ ] Frontend: `npm run lint` passes
- [ ] E2E: `npx playwright test` all green
- [ ] Manually verified in browser at http://localhost:3000

🤖 Generated with [Claude Code](https://claude.ai/code)
EOF
)" --base main
```

## Step 5 — Report to user

Tell the user:
- The PR URL
- Which scenarios passed in E2E
- Mark the story as ✅ COMPLETE in `docs/STORY-BACKLOG.md`
- Suggest the next pending story

## Important rules

- Never force-push to `main`
- Never commit `.env`, `application-local.yml`, or any file with real secrets
- Never use `--no-verify` to skip hooks
- If the pre-flight checks fail, stop and fix — do not open a PR with failing tests
