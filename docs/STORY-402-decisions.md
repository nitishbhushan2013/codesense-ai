# STORY-402 — Backend Azure Deployment · Implementation Decisions

## What was built

A production Spring profile (`application-prod.yml`), an Azure Blob Storage service implementation (`AzureBlobStorageService`), and an Azure Pipelines CI/CD pipeline (`azure-pipelines-backend.yml`) that builds, tests, and deploys the Spring Boot backend to Azure App Service on every push to `main` touching `backend/**`.

---

## Key decisions

### Use env vars injected by App Service rather than Key Vault SDK

**Context:** The backend needs secrets (DB password, Claude API key, JWT secret, storage connection string) at runtime in production.

**Decision:** `application-prod.yml` references secrets as plain `${ENV_VAR}` placeholders. The App Service app settings use Key Vault reference syntax (`@Microsoft.KeyVault(VaultName=...;SecretName=...)`) — Azure resolves these at runtime and injects them as normal environment variables. No Key Vault SDK or `azure-identity` dependency needed in the app.

**Why:** The provision script (`scripts/provision-azure.sh`) already wires up Managed Identity and sets all Key Vault references as App Service settings. Adding a Key Vault SDK would duplicate that concern and require extra dependencies. The env var approach keeps the app decoupled from the secret store.

**Implications:** The app has no awareness of Key Vault — any secret store that can inject env vars (Docker secrets, AWS SSM, local `.env`) works without code changes.

---

### Use `${DATASOURCE_URL}` rather than hardcoding the Azure PostgreSQL hostname

**Context:** The provision script constructs and sets `DATASOURCE_URL` as a full JDBC URL in the App Service settings.

**Decision:** `application-prod.yml` uses `url: ${DATASOURCE_URL}` directly rather than assembling the URL from parts.

**Why:** Avoids duplicating the hostname (`codesense-db.postgres.database.azure.com`) in two places. If the DB server is ever renamed or migrated, only the App Service setting needs updating — not the YAML.

**Implications:** The `DATASOURCE_URL` env var must always include `?sslmode=require` (which the provision script already does). Do not strip the query string when setting it manually.

---

### Connection string approach for Azure Blob Storage (not Managed Identity)

**Context:** `azure-storage-blob` SDK supports both connection strings and `DefaultAzureCredential` (Managed Identity). The Managed Identity approach requires adding `azure-identity` as a separate dependency.

**Decision:** `AzureBlobStorageService` uses a connection string from `${AZURE_STORAGE_CONNECTION_STRING}`, which App Service injects from Key Vault.

**Why:** The connection string is already stored in Key Vault by the provision script and injected alongside other secrets. Adding `azure-identity` for Managed Identity access to Blob Storage would add a dependency and require granting the App Service identity `Storage Blob Data Contributor` on the storage account — extra provisioning steps not worth the gain at this stage.

**Implications:** If Managed Identity is adopted in future (e.g. for compliance), replace the `connectionString()` builder call with `.credential(new DefaultAzureCredentialBuilder().build()).endpoint(...)` and remove the connection string from Key Vault. Also need `azure-identity` in `pom.xml`.

---

### `@ConditionalOnProperty` to switch storage implementations

**Context:** `LocalStorageService` (dev) and `AzureBlobStorageService` (prod) both implement `StorageService`. Only one should be active at a time.

**Decision:** Both beans are gated by `@ConditionalOnProperty(name = "storage.type", havingValue = "local"/"azure")`. `LocalStorageService` has `matchIfMissing = true` so local dev requires no explicit config.

**Why:** Clean separation — no `if/else` in the application code, no profiles needed just for storage switching. `storage.type: azure` in `application-prod.yml` is the only prod-side change required.

**Implications:** If a third storage backend is added (S3, GCS), follow the same pattern: add a new `havingValue`, set `matchIfMissing = false` on all three, add the `storage.type` value to the relevant profile.

---

### Azure Pipelines over GitHub Actions

**Context:** The repo is on GitHub; CI/CD could use either GitHub Actions or Azure Pipelines.

**Decision:** Azure Pipelines with `azure-pipelines-backend.yml`, service connection `codesense-azure-connection` to Azure subscription.

**Why:** Azure DevOps org (`codesense-ai`) was created as part of this story. Azure Pipelines integrates directly with Azure App Service deployment via the `AzureWebApp@1` task. GitHub Actions is a valid alternative but would require an additional Azure credentials secret in GitHub.

**Implications:** Requires maintaining the Azure DevOps org (`dev.azure.com/codesense-ai`). The service connection (`codesense-azure-connection`) must exist in that project — if the project is recreated, the connection must be recreated and the pipeline re-linked to the GitHub repo.

---

### Service connection name hardcoded in YAML

**Context:** The deploy stage references the Azure service connection. It could be a pipeline variable (`$(AZURE_SERVICE_CONNECTION)`) or a literal string.

**Decision:** Hardcoded as `codesense-azure-connection` directly in the YAML.

**Why:** There is only one environment (production). A variable adds indirection with no benefit. The name is not a secret.

**Implications:** If the service connection is ever renamed in Azure DevOps, update line 71 of `azure-pipelines-backend.yml`.

---

## Technical challenges

### Azure DevOps pipeline creation requires a GitHub PAT in CLI

**Problem:** `az pipelines create` for a GitHub-hosted repo prompts interactively for a GitHub username/PAT. Non-interactive terminals (like Claude Code's Bash tool) hit `EOFError` and the command fails.

**How it was resolved:** Pipeline was created manually through the Azure DevOps UI (Pipelines → New pipeline → GitHub → Existing YAML file).

**Why this matters:** Any automated pipeline bootstrapping script will need `AZURE_DEVOPS_EXT_GITHUB_PAT` set in the environment before calling `az pipelines create`.

---

### "Loading resource groups" hang in Azure DevOps service connection UI

**Problem:** When creating the Azure Resource Manager service connection, the resource group dropdown spun indefinitely on a newly created Azure DevOps org.

**How it was resolved:** Left the resource group blank (subscription-level scope) and proceeded. The connection was created successfully.

**Why this matters:** Subscription-level scope means the service principal has `Contributor` access to the whole subscription, not just `codesense-rg`. Scoping it down to `codesense-rg` later is a security improvement worth doing before sharing the subscription with other projects.

---

## What to watch out for

- `SPRING_PROFILES_ACTIVE=prod` must be set in App Service settings — the provision script sets this, but if app settings are reset the profile reverts to default (local storage, local DB).
- `AzureBlobStorageService.init()` creates the container if it doesn't exist — this is safe on startup but requires the connection string to be valid before the app starts. A missing or malformed `AZURE_STORAGE_CONNECTION_STRING` causes a startup failure, not a runtime error.
- The pipeline triggers on `backend/**` path changes. Changes to `azure-pipelines-backend.yml` itself (at repo root) do **not** trigger the pipeline — push a dummy backend change or trigger manually if you update the pipeline YAML.
- Azure App Service on Linux with Java 21 uses the startup command `java -jar /home/site/wwwroot/*.jar`. If multiple JARs exist in the deployment package, the glob may be ambiguous — Maven's `spring-boot:repackage` produces one fat JAR and one plain JAR; ensure only the fat JAR is in the artifact.
