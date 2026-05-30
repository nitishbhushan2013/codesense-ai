# CodeSense AI

AI-powered code review — paste code or point at a GitHub PR and get instant structured feedback with follow-up chat.

## Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5, Java 21, PostgreSQL |
| Frontend | Next.js 16, React 19, Tailwind CSS 4 |
| AI | Claude (Anthropic) |
| Auth | Email/password + GitHub OAuth, JWT in HttpOnly cookie |

---

## Local development setup

### Prerequisites

- Java 21 JDK (e.g. [Eclipse Adoptium](https://adoptium.net/))
- Node.js 20 LTS
- PostgreSQL 15+ running locally (`codesense_db` database)
- A Claude API key from [console.anthropic.com](https://console.anthropic.com/)

### 1. Clone and configure secrets

```bash
git clone https://github.com/nitishbhushan2013/codesense-ai.git
cd codesense-ai/backend/codesense-backend
cp .env.example .env
```

Open `.env` and fill in the required values (see comments inside the file). At minimum you need:

| Variable | Where to get it |
|---|---|
| `GITHUB_CLIENT_SECRET` | [github.com/settings/developers](https://github.com/settings/developers) → your OAuth App → Generate new client secret |
| `JWT_SECRET` | Run `openssl rand -base64 32` |
| `CLAUDE_API_KEY` | [console.anthropic.com](https://console.anthropic.com/) |

`DB_PASSWORD` defaults to `postgres` (matches the standard local PostgreSQL install).

### 2. Load env vars and start the backend

**macOS / Linux (bash/zsh):**
```bash
cd backend/codesense-backend
set -a && source .env && set +a
./mvnw spring-boot:run
```

**Windows (PowerShell):**
```powershell
cd backend\codesense-backend
Get-Content .env | ForEach-Object {
  if ($_ -match '^([^#=\s][^=]*)=(.*)$') {
    [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim())
  }
}
.\mvnw.cmd spring-boot:run
```

Backend runs on **http://localhost:8080**.

### 3. Start the frontend

```bash
cd frontend/codesense-frontend
npm install
npm run dev
```

Frontend runs on **http://localhost:3000**.

### 4. GitHub OAuth callback URL

Your GitHub OAuth App's callback URL must be set to:
```
http://localhost:8080/login/oauth2/code/github
```

Configure this at [github.com/settings/developers](https://github.com/settings/developers) → your OAuth App → Authorization callback URL.

---

## Running tests

### Backend

```powershell
cd backend/codesense-backend
.\mvnw.cmd test
```

### Frontend (lint + E2E)

```powershell
cd frontend/codesense-frontend
npm run lint
npx playwright test   # requires both servers running on :3000 and :8080
```

---

## Project structure

```
codesense-ai/
├── backend/codesense-backend/   Spring Boot REST API
│   ├── .env.example             Required environment variables (copy → .env)
│   └── src/
├── frontend/codesense-frontend/ Next.js app
│   └── e2e/                     Playwright end-to-end tests
└── docs/
    ├── ARCHITECTURE.md          System design (aspirational)
    ├── PRD.md                   Product requirements
    └── STORY-BACKLOG.md         Sprint stories and status
```

---

## Feature status

| Sprint | Stories | Status |
|---|---|---|
| Pre-Sprint | STORY-000 | ✅ Complete |
| Sprint 1 | STORY-101 to 108 | 🔄 In Progress |
| Sprint 2 | STORY-201 to 205 | ✅ Complete |
| Sprint 3 | STORY-301 to 303 | ✅ Complete |
| Sprint 4 | STORY-401 to 404 | ⏳ Pending |
