# CodeSense AI — Architecture Document

## Version 1.0

## System Overview

CodeSense AI is a web application that accepts GitHub PR URLs or raw code,
analyses it using Claude AI, and returns structured review findings with
suggested fixes. The system is built on Microsoft Azure with a React/Next.js
frontend and Java/Spring Boot backend.

## Component Decisions

| Component          | Choice             | Why                                                    |
| ------------------ | ------------------ | ------------------------------------------------------ |
| Next.js            | React framework    | SSR, file-based routing, Azure Static Web Apps support |
| Spring Boot 3      | Java backend       | Production-grade, excellent Spring Security for auth   |
| PostgreSQL         | Primary database   | Relational, reliable, Azure managed service            |
| Azure Blob Storage | Storing code diffs | Keeps DB lean, large text blobs stored separately      |
| Claude API         | AI engine          | Best code understanding, structured JSON output        |
| Azure Key Vault    | Secrets management | API keys never in code or env files                    |
| App Insights       | Monitoring         | Native Azure integration                               |

## Frontend Architecture

### Folder Structure

```
codesense-frontend/
├── app/
│   ├── page.tsx                   ← Landing page (public)
│   ├── review/
│   │   └── [id]/page.tsx          ← Review results page
│   ├── dashboard/
│   │   └── page.tsx               ← History dashboard (protected)
│   ├── auth/
│   │   ├── login/page.tsx         ← Login page
│   │   └── register/page.tsx      ← Registration page
│   └── layout.tsx                 ← Root layout with navbar
├── components/
│   ├── SubmitForm.tsx             ← PR URL + code paste tabs
│   ├── ReviewCard.tsx             ← Single finding card
│   ├── ReviewSummary.tsx          ← Score + category breakdown
│   ├── ChatPanel.tsx              ← Follow-up chat interface
│   ├── DashboardList.tsx          ← Past reviews list
│   └── Navbar.tsx                 ← Auth-aware navigation
├── lib/
│   ├── api.ts                     ← All backend API calls
│   └── auth.ts                    ← Auth helper functions
└── types/
    └── index.ts                   ← TypeScript interfaces
```

## Backend Architecture

### Folder Structure

```
codesense-backend/
├── src/main/java/com/codesense/
│   ├── controller/
│   │   ├── AuthController.java        ← Login, register, OAuth
│   │   ├── ReviewController.java      ← Submit + fetch reviews
│   │   └── ChatController.java        ← Follow-up chat SSE
│   ├── service/
│   │   ├── AuthService.java           ← JWT + user management
│   │   ├── GitHubService.java         ← Fetch PR diff
│   │   ├── ClaudeService.java         ← Send code to Claude API
│   │   ├── ReviewService.java         ← Orchestrate review flow
│   │   └── StorageService.java        ← Azure Blob operations
│   ├── model/
│   │   ├── User.java                  ← User entity
│   │   ├── Review.java                ← Review entity
│   │   ├── Finding.java               ← Finding entity
│   │   └── ChatMessage.java           ← Chat history entity
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── ReviewRepository.java
│   │   └── ChatMessageRepository.java
│   ├── security/
│   │   ├── JwtFilter.java             ← JWT validation
│   │   ├── JwtService.java            ← JWT generation
│   │   ├── SecurityConfig.java        ← Spring Security config
│   │   └── OAuth2SuccessHandler.java  ← Post-GitHub-login handler
│   └── dto/
│       ├── RegisterRequest.java       ← Registration request
│       ├── LoginRequest.java          ← Login request
│       ├── AuthResponse.java          ← Auth response
│       ├── ReviewRequest.java         ← Review submission
│       ├── ReviewResponse.java        ← Review result
│       └── FindingDto.java            ← Individual finding
```

## Database Schema

### Users Table

```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) UNIQUE,
    name        VARCHAR(255) NOT NULL,
    password    VARCHAR(255),
    github_id   VARCHAR(100) UNIQUE,
    avatar_url  VARCHAR(500),
    created_at  TIMESTAMP DEFAULT NOW()
);
```

### Reviews Table

```sql
CREATE TABLE reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    submission_type VARCHAR(20) NOT NULL,
    language        VARCHAR(50),
    pr_url          VARCHAR(500),
    blob_key        VARCHAR(500),
    score           INTEGER,
    status          VARCHAR(20) DEFAULT 'pending',
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### Findings Table

```sql
CREATE TABLE findings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id       UUID REFERENCES reviews(id) ON DELETE CASCADE,
    category        VARCHAR(20) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    line_reference  VARCHAR(100),
    description     TEXT NOT NULL,
    suggested_fix   TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### Chat Messages Table

```sql
CREATE TABLE chat_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id   UUID REFERENCES reviews(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);
```

## API Contract

### Authentication Endpoints

| Method | Endpoint                    | Auth | Description                  |
| ------ | --------------------------- | ---- | ---------------------------- |
| POST   | `/api/auth/register`        | None | Register with email/password |
| POST   | `/api/auth/login`           | None | Login, returns JWT cookie    |
| GET    | `/api/auth/github`          | None | Initiate GitHub OAuth        |
| GET    | `/api/auth/github/callback` | None | GitHub OAuth callback        |
| POST   | `/api/auth/logout`          | JWT  | Clear JWT cookie             |
| GET    | `/api/auth/me`              | JWT  | Get current user profile     |

### Review Endpoints

| Method | Endpoint            | Auth     | Description              |
| ------ | ------------------- | -------- | ------------------------ |
| POST   | `/api/reviews`      | Optional | Submit code for review   |
| GET    | `/api/reviews`      | Required | Get all reviews for user |
| GET    | `/api/reviews/{id}` | Optional | Get single review        |
| DELETE | `/api/reviews/{id}` | Required | Delete a review          |

### Chat Endpoints

| Method | Endpoint                 | Auth     | Description             |
| ------ | ------------------------ | -------- | ----------------------- |
| POST   | `/api/reviews/{id}/chat` | Required | Send chat message (SSE) |
| GET    | `/api/reviews/{id}/chat` | Required | Get chat history        |

## Claude API Prompt Design

### System Prompt

```
You are an expert code reviewer with deep knowledge of software
engineering best practices, security vulnerabilities, and performance
optimisation.

Analyse the provided code and return ONLY a valid JSON object with
this exact structure — no preamble, no markdown, just the JSON:

{
  "score": <integer 0-100>,
  "summary": "<2-3 sentence overall assessment>",
  "findings": [
    {
      "category": "<bug|security|performance|quality>",
      "severity": "<critical|warning|info>",
      "lineReference": "<e.g. Line 23 or Lines 45-52>",
      "description": "<clear explanation of the issue>",
      "suggestedFix": "<corrected code snippet>"
    }
  ]
}
```

## Azure Infrastructure Layout

```
Azure Subscription
└── Resource Group: codesense-rg
    ├── Azure Static Web Apps         ← Next.js frontend
    ├── Azure App Service             ← Spring Boot backend (B2 plan)
    ├── Azure Database for PostgreSQL (Flexible Server)
    ├── Azure Blob Storage Account
    │   └── Container: code-diffs
    ├── Azure Key Vault
    │   ├── CLAUDE_API_KEY
    │   ├── GITHUB_CLIENT_SECRET
    │   ├── JWT_SECRET
    │   └── DB_PASSWORD
    └── Azure Application Insights
```

## CI/CD Pipeline Design

### Frontend Pipeline

```
Trigger: push to main
Steps:
1. npm install
2. npm run build
3. npm run test
4. Deploy to Azure Static Web Apps
```

### Backend Pipeline

```
Trigger: push to main
Steps:
1. mvn clean install
2. mvn test
3. mvn package
4. Deploy JAR to Azure App Service
```

## Security Architecture

| Concern             | Solution                             |
| ------------------- | ------------------------------------ |
| Passwords           | BCrypt hashing, never stored plain   |
| JWT tokens          | HttpOnly cookie, 24hr expiry         |
| GitHub OAuth tokens | Never stored, used once only         |
| API keys            | Azure Key Vault via managed identity |
| CORS                | Backend allows frontend domain only  |
| SQL injection       | JPA parameterised queries            |
| Anonymous reviews   | Memory only, never written to DB     |

## Future Considerations (v2)

- AWS multi-cloud support (ECS + RDS)
- GitLab and Bitbucket integration
- Team workspaces and collaboration
- IDE plugin for VS Code
- CI/CD integration as a GitHub Action
