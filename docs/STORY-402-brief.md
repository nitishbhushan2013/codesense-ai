# STORY-402 — Backend Azure Deployment

**Branch:** `story-402-backend-azure-deployment`

## What we're building

Deploying the Spring Boot backend to Azure App Service so it runs in a real production environment. The backend needs a production Spring profile (`application-prod.yml`) that reads every secret from Azure Key Vault via Managed Identity — no credentials in config files. A CI/CD pipeline (`azure-pipelines-backend.yml`) will build and deploy the JAR on every push to `main`, so future changes ship automatically.

## Task list

- [ ] Configure `application-prod.yml`
- [ ] Connect App Service to Key Vault via Managed Identity
- [ ] Create `azure-pipelines-backend.yml`
- [ ] Push to main and verify pipeline deploys
- [ ] Test all APIs against live Azure URL

## First step

Create `backend/codesense-backend/src/main/resources/application-prod.yml` — activate on `SPRING_PROFILES_ACTIVE=prod`, source datasource URL and credentials from Azure Key Vault references injected as App Service env vars, and set storage type to `azure`.
