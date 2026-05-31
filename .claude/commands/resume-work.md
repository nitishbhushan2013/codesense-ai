---
name: resume-work
description: Review the current git status and recent commits, then summarize what was last worked on and suggest what to tackle next.
---

## What this command does

1. Reads the current branch and recent commit history
2. Reads any uncommitted changes (staged + unstaged)
3. Reads the story backlog to find in-progress and pending stories
4. Produces a concise "where we left off" summary and a concrete next-step recommendation

## Instructions

### 1 — Gather git context

```powershell
git branch --show-current
```

```powershell
git log --oneline -10
```

```powershell
git status
```

```powershell
git diff --stat HEAD
```

### 2 — Read the story backlog

Read `docs/STORY-BACKLOG.md` in full to understand which stories are complete, in-progress, and pending.

### 3 — Synthesize and report

Write a short, structured report in this format:

---

**Current branch:** `<branch-name>`

**Last commit:** `<hash> <message>`

**What was last worked on:**
<2–4 sentences. Name the story, describe what was built or changed, and call out any files with uncommitted changes.>

**Uncommitted changes:**
<List modified/untracked files and a one-line note on what each contains. If none, say "Working tree is clean.">

**Suggested next step:**
<One concrete action. Pick from: (a) finish and close a partially-done story, (b) start the next pending story in the backlog, or (c) fix a pre-flight issue blocking the next story. Name the story ID and the first task to do.>

---

Keep the report under 20 lines. No filler text — every sentence should be actionable or directly informative.
