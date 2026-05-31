# STORY-403 — Frontend Azure Deployment

**Branch:** `story-403-frontend-azure-deployment`

## What we're building

Deploying the Next.js frontend to the Azure Static Web App (`codesense-frontend`) that was already provisioned in STORY-401. The key challenge is ensuring `NEXT_PUBLIC_API_URL` points to the live backend (`https://codesense-backend.azurewebsites.net`) at build time — Next.js embeds this into the client-side bundle, so it must be set as a build environment variable in the Static Web App configuration, not just a runtime value.

## Task list

- [ ] Set `NEXT_PUBLIC_API_URL` in Azure Static Web App Application Settings
- [ ] Link GitHub repo to Static Web App and trigger deployment
- [ ] Verify full end-to-end flow on Azure (login, submit review, chat)

## First step

In the Azure Portal: go to Static Web Apps → `codesense-frontend` → Configuration → Application settings → add `NEXT_PUBLIC_API_URL = https://codesense-backend.azurewebsites.net`. This ensures every GitHub-triggered build picks up the correct API URL.
