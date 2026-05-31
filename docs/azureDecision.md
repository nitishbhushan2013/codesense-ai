# CodeSense AI — Azure Resource Decisions

> Local-only reference. Kept in sync with `docs/ARCHITECTURE.md`.

---

## Infrastructure map

```
Azure Subscription
└── Resource Group: codesense-rg
    ├── Azure Static Web Apps         ← Next.js frontend
    ├── Azure App Service (B2)        ← Spring Boot backend
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

---

## Resource-by-resource decisions

### Azure App Service (B2 plan) — Spring Boot backend

**What need drove this choice:**
The backend is a long-running JVM process that handles HTTP requests, manages PostgreSQL connection pools, and keeps SSE connections open while streaming Claude responses token by token. That rules out serverless (Functions): JVM cold-start latency is 5–15 seconds, and SSE requires a persistent HTTP connection that serverless platforms close after 30–60 seconds. App Service gives an always-on VM with auto-restart, staged deployment slots, and built-in load balancing.

**Why B2 (2 vCores, 3.5 GB RAM):**
The JVM baseline footprint is ~300–500 MB. Each active Claude streaming session (SSE) holds a reactive `Flux<String>` pipeline and a Spring MVC thread. B1 (1 vCore, 1.75 GB) runs out of headroom under concurrent reviews. B2 gives comfortable room for 5–10 concurrent users without throttling.

**What would break without it:**
The entire backend — auth, review submission, chat streaming, dashboard API — lives here. No App Service = no application.

---

### Azure Static Web Apps — Next.js frontend

**What need drove this choice:**
Next.js 16 uses the App Router with Server Components and SSR. It can't be deployed as a plain S3/CDN static bundle — it needs a Node.js runtime for server-side rendering. Azure Static Web Apps has first-class Next.js support (it was explicitly listed as a reason Next.js was chosen in `ARCHITECTURE.md`). It handles:
- Global CDN distribution (low latency worldwide)
- Free SSL/TLS certificate
- Automatic GitHub deployment on push to main
- No server to manage — serverless Node.js for SSR

**Cost:** Free tier covers the portfolio use case entirely.

**Why not App Service for the frontend too:**
App Service costs money even at the B1 tier (~$13/month). Static Web Apps free tier is zero. For a Next.js app serving mostly cached HTML, there's no reason to pay for a full VM.

**What would break without it:**
The entire user interface — landing page, review results, dashboard, auth pages.

---

### Azure Database for PostgreSQL (Flexible Server) — primary data store

**What need drove this choice:**
The data model is relational with hard foreign-key constraints:
- `users` ← `reviews` (ON DELETE CASCADE)
- `reviews` ← `findings` (ON DELETE CASCADE)
- `reviews` ← `chat_messages` (ON DELETE CASCADE)

These relationships are enforced at the DB level, not just the application level. JPA/Hibernate relies on PostgreSQL's UUID generation, referential integrity, and transaction isolation. A document store (Cosmos DB, MongoDB) would require the application to manually manage cascade deletes and relationship consistency — unnecessary complexity for a well-defined relational schema.

**Why Flexible Server over Single Server:**
Azure Single Server is retired (end-of-life 2025). Flexible Server is the current offering — supports private networking, connection pooling via PgBouncer, and point-in-time restore.

**What would break without it:**
All user data — accounts, review history, findings, chat messages — lives here. No database = anonymous-only mode at best, total data loss at worst.

---

### Azure Blob Storage (container: `code-diffs`) — raw diff storage

**What need drove this choice:**
When a user submits a GitHub PR with 200 changed files, the raw diff can be 50–200 KB of text. Storing that in a `TEXT` column on every row of the `reviews` table would:
- Bloat the DB size rapidly (1,000 reviews × 100 KB = 100 MB of review text in the DB)
- Slow down `SELECT *` queries on the dashboard (fetching all reviews pulls all blobs)
- Make DB backups expensive

The architecture separates concerns: the `reviews` table stores metadata and structured findings (small, query-friendly), and Blob Storage holds the raw diff keyed by `blob_key` (a UUID path). The diff is only retrieved when needed (e.g., to re-run a review or display the original code).

**The `StorageService` interface:**
`LocalStorageService` (local filesystem, `./local-storage/code-diffs`) is the dev implementation. `AzureBlobStorageService` is the prod implementation — same interface, swapped by `@ConditionalOnProperty(storage.type=azure)`. No application code changes when switching from local to Azure.

**Cost:** Blob Storage costs ~$0.002 per GB per month. Even 10,000 reviews with 100 KB diffs = 1 GB = $0.002/month. Effectively free.

**What would break without it:**
PR URL submissions would fail at the storage step (the `StorageService.store()` call). Paste submissions would also fail. In theory the app could skip storage and pass the code directly through to Claude without persisting it — but that breaks the ability to re-run or audit reviews later.

---

### Azure Key Vault — secrets management

**What need drove this choice:**
By STORY-108, the application has five production secrets:
- `CLAUDE_API_KEY` — Anthropic billing key
- `GITHUB_CLIENT_SECRET` — OAuth client secret
- `JWT_SECRET` — signs all user sessions
- `DB_PASSWORD` — database access
- `GITHUB_API_TOKEN` — GitHub API rate limit bypass

Storing these as App Service "Application Settings" (environment variables in the Azure Portal) is better than a config file, but still visible to anyone with Contributor access to the subscription. Key Vault + Managed Identity is the Azure-native answer:

1. App Service is assigned a **system-assigned managed identity** (no credentials to manage)
2. That identity is granted **Key Vault Secrets User** role on the vault
3. The backend reads secrets via the Azure SDK at startup — the secret value never appears in any config file, pipeline variable, or deployment artifact

**Why not just App Service Application Settings:**
Any developer with Contributor role on the resource group can see Application Settings in plain text. Key Vault access is controlled by a separate RBAC policy — you can grant an identity read-only access to secrets without granting it any other Azure permissions.

**What would break without it:**
The backend would need secrets injected another way (pipeline variables, hardcoded — both worse). Key Vault is the last line of defence: even if the repo or pipeline is compromised, the secrets are not exposed.

---

### Azure Application Insights — monitoring and observability

**What need drove this choice:**
The application has several failure modes that are invisible without instrumentation:
- Claude API rate limits or timeouts (the retry logic in `ClaudeService` may silently exhaust retries)
- GitHub API 403s on private repos (correct error handling exists, but how often does it happen in production?)
- SSE stream disconnects mid-response (the client drops, the emitter errors — is this common?)
- Slow DB queries as the `reviews` table grows

Application Insights gives:
- **Request tracing:** every HTTP request with status, duration, and dependencies
- **Exception tracking:** unhandled errors with full stack trace
- **Custom metrics:** can log Claude response times, token counts
- **Live Metrics Stream:** real-time request/error/CPU view during deployments
- **Alerts:** email/SMS when error rate spikes

**Integration cost:** Add the `applicationinsights-spring-boot-starter` dependency and one environment variable (`APPLICATIONINSIGHTS_CONNECTION_STRING`). Zero code changes — the agent instruments Spring automatically.

**Why not self-hosted (Prometheus + Grafana):**
A portfolio project doesn't need operational overhead of running a monitoring stack. Application Insights is native to Azure and free up to 5 GB/month ingestion.

**What would break without it:**
Nothing breaks — but production issues become invisible. You'd have no way to know if 20% of reviews are failing silently.

---

## Why Azure over AWS or GCP

The `PROJECT-BRIEF.md` sets it as a goal: *"Deploy publicly on Azure, accessible via a shareable URL."* Azure was selected upfront — the tech stack table in the brief lists Azure, Static Web Apps, and App Service by name. The practical reasons:

1. **Spring Boot + PostgreSQL is a first-class combo on Azure** — App Service has native Java 21 support, Azure PostgreSQL Flexible Server is a managed service, no containerisation required
2. **Static Web Apps has first-class Next.js SSR support** — no custom Node.js server needed
3. **Azure DevOps Pipelines** integrates with the Azure deployment targets without extra connectors
4. The project is a portfolio piece — Azure certifications and Azure-specific deployment experience have direct resume value

---

## Summary table

| Resource | Tier | Why this tier |
|---|---|---|
| App Service | B2 | JVM headroom + concurrent SSE sessions |
| Static Web Apps | Free | Next.js SSR, CDN, free tier sufficient |
| PostgreSQL Flexible Server | Burstable B1ms | Dev/portfolio workload; scale up for prod |
| Blob Storage | LRS (Locally Redundant) | Low cost, portfolio-grade redundancy |
| Key Vault | Standard | Secrets; no HSM needed at this scale |
| Application Insights | Pay-as-you-go | Free up to 5 GB/month ingestion |
