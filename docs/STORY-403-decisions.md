# STORY-403 — Frontend Azure Deployment · Implementation Decisions

## What was built

Deployed the Next.js frontend to Azure Static Web Apps (`codesense-frontend`, eastus2) via a GitHub Actions workflow (`.github/workflows/deploy-frontend.yml`). Every push to `main` touching `frontend/**` now builds and deploys the app with `NEXT_PUBLIC_API_URL` pointing at the live backend. `FRONTEND_URL` on the App Service was updated for CORS.

---

## Key decisions

### Set `NEXT_PUBLIC_API_URL` in the workflow, not in Azure App Settings

**Context:** `NEXT_PUBLIC_*` variables in Next.js are embedded into the client-side bundle at build time, not injected at runtime. Azure Static Web Apps Application Settings are available to server-side code at runtime, but the SWA GitHub Actions build container does not automatically expose them as build-time environment variables.

**Decision:** `NEXT_PUBLIC_API_URL` is hardcoded directly in the `env:` block of `deploy-frontend.yml`.

**Why:** Simplest approach that guarantees the variable is present during `npm run build`. It is not a secret — it is a public API URL. Putting it in the workflow file makes the value explicit and reviewable.

**Implications:** If the backend URL ever changes, update `NEXT_PUBLIC_API_URL` in `deploy-frontend.yml` and push — this triggers a rebuild with the new URL embedded.

---

### Create SWA without GitHub integration (`--login-with-github` removed)

**Context:** The provision script (STORY-401) created the SWA using `az staticwebapp create --login-with-github`, which requires browser-based OAuth to link the GitHub repo. This interactive step was not completed, leaving the resource with `repo: null` and `branch: null`.

**Decision:** Delete and recreate `codesense-frontend` using `az staticwebapp create --sku Free` with no GitHub flags. Our own GitHub Actions workflow handles deployment via the deployment token — the Azure-side GitHub link is not needed.

**Why:** A partially-linked SWA causes the deployment token to be rejected by the Azure deployment service. Recreating without the GitHub integration produces a clean resource whose token works immediately.

**Implications:** The Azure portal will show no GitHub connection under the SWA resource (no "Deployment" section in the left nav). This is expected — deployments are tracked in GitHub Actions, not in the Azure portal.

---

### `workflow_dispatch` added for manual re-triggering

**Context:** `gh run rerun` re-uses secrets cached at the time of the original run. After rotating the deployment token (twice), re-runs kept using the old invalid token.

**Decision:** Added `workflow_dispatch:` trigger to `deploy-frontend.yml`.

**Why:** Allows triggering a fresh run from the GitHub UI or CLI (`gh workflow run`) that always picks up the current secret values. Avoids the re-run secret-caching problem entirely.

**Implications:** Anyone with repo write access can trigger a frontend deployment manually from `https://github.com/nitishbhushan2013/codesense-ai/actions/workflows/deploy-frontend.yml`.

---

## Technical challenges

### Deployment token rejected despite being freshly reset

**Problem:** `azure/static-web-apps-deploy@v1` returned `deployment_token provided was invalid` on every attempt, even after `az staticwebapp secrets reset-api-key` and updating the GitHub secret.

**How it was resolved:** Root cause was that the SWA resource was in a broken state from the incomplete `--login-with-github` OAuth flow during provisioning. Deleting and recreating the resource cleanly produced a token that was accepted on the first attempt.

**Why this matters:** If the token is ever rejected in future, check `az staticwebapp show --query "{repo:repositoryUrl,branch:branch}"` — if both are `null`, the resource may be in a partially-configured state and needs to be recreated.

---

### `gh run rerun` does not pick up updated secrets

**Problem:** After updating `AZURE_STATIC_WEB_APPS_API_TOKEN` in GitHub Secrets, re-running the failed job still used the old (invalid) token.

**How it was resolved:** Triggered a fresh run via `gh workflow run` (after adding `workflow_dispatch`) rather than re-running the failed job.

**Why this matters:** Never use `gh run rerun` after rotating a secret — always trigger a new run.

---

## What to watch out for

- The live frontend URL changed when the SWA was recreated: old `jolly-sky-072e6080f.7.azurestaticapps.net` → new `calm-coast-0eb31dc0f.7.azurestaticapps.net`. Update any bookmarks or shared links.
- `FRONTEND_URL` on the backend App Service must match the live SWA URL exactly — if the SWA is ever recreated again, update this env var via `az webapp config appsettings set` or the portal, otherwise CORS will block the frontend.
- Azure SWA free tier has a build time limit of ~10 min. The current Next.js build completes in ~3 min — well within limits.
- There is no `staticwebapp.config.json` in the repo. If routing issues appear (404 on page refresh for deep links), add one with `"navigationFallback": {"rewrite": "/index.html"}`.
