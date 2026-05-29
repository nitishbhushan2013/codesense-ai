# CodeSense Backend

Spring Boot 3.5 / Java 21 REST API for CodeSense AI.

## Prerequisites

- JDK 21
- PostgreSQL running locally with a `codesense_db` database (schema is
  auto-managed via JPA `ddl-auto: update`)
- Maven wrapper (bundled — use `./mvnw` / `mvnw.cmd`)

## Configuration & secrets

All secrets are read from environment variables; **no real secret values live
in `application.yml`**. The config uses Spring placeholders (`${VAR}` /
`${VAR:default}`):

| Variable               | Required | Default (in `application.yml`)                     | Purpose                                  |
| ---------------------- | -------- | -------------------------------------------------- | ---------------------------------------- |
| `DB_URL`               | no       | `jdbc:postgresql://localhost:5432/codesense_db`    | JDBC URL for PostgreSQL                  |
| `DB_USERNAME`          | no       | `postgres`                                         | DB user                                  |
| `DB_PASSWORD`          | **yes**  | —                                                  | DB password                             |
| `JWT_SECRET`           | **yes**  | —                                                  | HMAC signing key for auth JWTs (≥32 chars) |
| `GITHUB_CLIENT_ID`     | **yes**  | —                                                  | GitHub OAuth app client id               |
| `GITHUB_CLIENT_SECRET` | **yes**  | —                                                  | GitHub OAuth app client secret           |
| `GITHUB_API_TOKEN`     | no       | empty                                              | PAT for fetching PR diffs (rate limits)  |
| `CLAUDE_API_KEY`       | no\*     | empty                                              | Anthropic API key for AI reviews         |

\* The app boots without `CLAUDE_API_KEY`, but reviews need it to return real
results.

Variables marked **Required** have no default, so the app fails fast on startup
if they are unset.

### Local setup

1. Copy the example env file and fill in real values:

   ```bash
   cp .env.example .env
   ```

   `.env` and `application-local.yml` are gitignored — never commit secrets.

2. Export the variables into your shell before running. The Maven plugin does
   **not** auto-load `.env`, so source it yourself:

   - macOS / Linux (bash/zsh):

     ```bash
     set -a; source .env; set +a
     ./mvnw spring-boot:run
     ```

   - Windows PowerShell:

     ```powershell
     Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
       $name, $value = $_ -split '=', 2
       Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
     }
     .\mvnw.cmd spring-boot:run
     ```

   Alternatively, set the variables in your IDE run configuration, or put
   non-secret overrides in a gitignored `application-local.yml` and run with
   `--spring.profiles.active=local`.

## Commands

Run from this directory (`backend/codesense-backend/`):

- Dev server (port 8080): `./mvnw spring-boot:run`
- Build + run tests: `./mvnw clean install`
- Run tests: `./mvnw test`
- Single test class: `./mvnw test -Dtest=ClaudeServiceTest`

Tests run against H2 under the `test` profile and do not need the env vars above.
