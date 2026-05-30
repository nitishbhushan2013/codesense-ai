---
name: setup-dev
description: Start both dev servers (Spring Boot :8080 + Next.js :3000) and confirm they are healthy before resuming story work.
---

## What this skill does

1. Checks JAVA_HOME is set (uses the Eclipse Adoptium JDK at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`)
2. Starts the Spring Boot backend in the background from `backend/codesense-backend/`
3. Starts the Next.js frontend in the background from `frontend/codesense-frontend/`
4. Polls until both `:8080` and `:3000` are listening, then reports ready

## Instructions

Run in this exact order. Do NOT proceed to story work until both servers show LISTENING.

### 1 — Start backend
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
cd backend\codesense-backend
.\mvnw.cmd spring-boot:run
```
Run in background.

### 2 — Install frontend deps (only if node_modules is missing)
```powershell
cd frontend\codesense-frontend
npm install
```

### 3 — Start frontend
```powershell
cd frontend\codesense-frontend
npm run dev
```
Run in background.

### 4 — Verify both ports are up
```powershell
netstat -ano | Select-String ":3000|:8080"
```
Both lines must show LISTENING before continuing.

### 5 — Report to user
Tell the user:
- ✅ Backend running on http://localhost:8080
- ✅ Frontend running on http://localhost:3000
- Current branch and latest commit
- Next pending story from STORY-BACKLOG.md
