#!/usr/bin/env bash
# =============================================================================
# CodeSense AI — Azure Resource Provisioning Script
#
# Usage:
#   1. Fill in the CONFIGURATION section below.
#   2. Log in:  az login
#   3. Run:     bash scripts/provision-azure.sh
#
# This script is idempotent for most resources (re-running is safe).
# Resources that already exist will be skipped with a warning.
# =============================================================================
set -euo pipefail

# =============================================================================
# CONFIGURATION — edit these before running
# =============================================================================

SUBSCRIPTION_ID="${AZURE_SUBSCRIPTION_ID}"                      # az account show --query id -o tsv
LOCATION="australiaeast"                # az account list-locations -o table
RESOURCE_GROUP="codesense-rg"

# PostgreSQL
PG_SERVER="codesense-db"
PG_ADMIN_USER="codesenseadmin"
PG_ADMIN_PASSWORD=""                    # min 8 chars, upper+lower+number+special
PG_DATABASE="codesense_db"

# Storage
STORAGE_ACCOUNT="codesensetore"         # 3-24 chars, lowercase letters and numbers only
STORAGE_CONTAINER="code-diffs"

# Key Vault
KEY_VAULT="codesense-kv"

# Secrets — paste values here (never commit this file with real values filled in)
SECRET_CLAUDE_API_KEY=""
SECRET_GITHUB_CLIENT_SECRET=""
SECRET_JWT_SECRET=""                    # openssl rand -base64 32
SECRET_DB_PASSWORD="$PG_ADMIN_PASSWORD"
SECRET_GITHUB_API_TOKEN=""             # optional, leave empty if unused

# App Service
APP_SERVICE_PLAN="codesense-plan"
APP_SERVICE_NAME="codesense-backend"   # becomes https://codesense-backend.azurewebsites.net

# Static Web Apps
STATIC_WEB_APP="codesense-frontend"

# Application Insights
APP_INSIGHTS="codesense-insights"

# =============================================================================
# END CONFIGURATION
# =============================================================================

if [[ -z "$SUBSCRIPTION_ID" ]]; then
  echo "ERROR: Set SUBSCRIPTION_ID before running." >&2
  exit 1
fi
if [[ -z "$PG_ADMIN_PASSWORD" ]]; then
  echo "ERROR: Set PG_ADMIN_PASSWORD before running." >&2
  exit 1
fi

echo "==> Setting subscription: $SUBSCRIPTION_ID"
az account set --subscription "$SUBSCRIPTION_ID"

# ── 1. Resource Group ────────────────────────────────────────────────────────
echo "==> Creating resource group: $RESOURCE_GROUP"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --output none

# ── 2. PostgreSQL Flexible Server ────────────────────────────────────────────
echo "==> Creating PostgreSQL Flexible Server: $PG_SERVER"
az postgres flexible-server create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$PG_SERVER" \
  --location "$LOCATION" \
  --admin-user "$PG_ADMIN_USER" \
  --admin-password "$PG_ADMIN_PASSWORD" \
  --sku-name "Standard_B1ms" \
  --tier "Burstable" \
  --storage-size 32 \
  --version 16 \
  --public-access "None" \
  --output none

echo "==> Creating database: $PG_DATABASE"
az postgres flexible-server db create \
  --resource-group "$RESOURCE_GROUP" \
  --server-name "$PG_SERVER" \
  --database-name "$PG_DATABASE" \
  --output none

# ── 3. Blob Storage ──────────────────────────────────────────────────────────
echo "==> Creating storage account: $STORAGE_ACCOUNT"
az storage account create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$STORAGE_ACCOUNT" \
  --location "$LOCATION" \
  --sku "Standard_LRS" \
  --kind "StorageV2" \
  --output none

STORAGE_CONNECTION_STRING=$(az storage account show-connection-string \
  --resource-group "$RESOURCE_GROUP" \
  --name "$STORAGE_ACCOUNT" \
  --query connectionString \
  --output tsv)

echo "==> Creating blob container: $STORAGE_CONTAINER"
az storage container create \
  --name "$STORAGE_CONTAINER" \
  --connection-string "$STORAGE_CONNECTION_STRING" \
  --output none

# ── 4. Key Vault ─────────────────────────────────────────────────────────────
echo "==> Creating Key Vault: $KEY_VAULT"
az keyvault create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$KEY_VAULT" \
  --location "$LOCATION" \
  --enable-rbac-authorization false \
  --output none

echo "==> Setting Key Vault secrets"

# Only set secrets that have values
[[ -n "$SECRET_CLAUDE_API_KEY" ]] && \
  az keyvault secret set --vault-name "$KEY_VAULT" --name "CLAUDE-API-KEY" \
    --value "$SECRET_CLAUDE_API_KEY" --output none

[[ -n "$SECRET_GITHUB_CLIENT_SECRET" ]] && \
  az keyvault secret set --vault-name "$KEY_VAULT" --name "GITHUB-CLIENT-SECRET" \
    --value "$SECRET_GITHUB_CLIENT_SECRET" --output none

[[ -n "$SECRET_JWT_SECRET" ]] && \
  az keyvault secret set --vault-name "$KEY_VAULT" --name "JWT-SECRET" \
    --value "$SECRET_JWT_SECRET" --output none

az keyvault secret set --vault-name "$KEY_VAULT" --name "DB-PASSWORD" \
  --value "$SECRET_DB_PASSWORD" --output none

az keyvault secret set --vault-name "$KEY_VAULT" --name "AZURE-STORAGE-CONNECTION-STRING" \
  --value "$STORAGE_CONNECTION_STRING" --output none

[[ -n "$SECRET_GITHUB_API_TOKEN" ]] && \
  az keyvault secret set --vault-name "$KEY_VAULT" --name "GITHUB-API-TOKEN" \
    --value "$SECRET_GITHUB_API_TOKEN" --output none

# ── 5. App Service ────────────────────────────────────────────────────────────
echo "==> Creating App Service Plan: $APP_SERVICE_PLAN"
az appservice plan create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_PLAN" \
  --location "$LOCATION" \
  --sku "B2" \
  --is-linux \
  --output none

echo "==> Creating Web App: $APP_SERVICE_NAME"
az webapp create \
  --resource-group "$RESOURCE_GROUP" \
  --plan "$APP_SERVICE_PLAN" \
  --name "$APP_SERVICE_NAME" \
  --runtime "JAVA:21-java21" \
  --output none

# ── 6. Managed Identity + Key Vault access ───────────────────────────────────
echo "==> Enabling system-assigned managed identity on App Service"
az webapp identity assign \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --output none

APP_IDENTITY=$(az webapp identity show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --query principalId \
  --output tsv)

echo "==> Granting Key Vault read access to App Service identity"
az keyvault set-policy \
  --name "$KEY_VAULT" \
  --object-id "$APP_IDENTITY" \
  --secret-permissions get list \
  --output none

# ── 7. App Service environment variables ─────────────────────────────────────
PG_HOST="${PG_SERVER}.postgres.database.azure.com"
DATASOURCE_URL="jdbc:postgresql://${PG_HOST}:5432/${PG_DATABASE}?sslmode=require"

echo "==> Configuring App Service environment variables"
az webapp config appsettings set \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --settings \
    "SPRING_PROFILES_ACTIVE=prod" \
    "DATASOURCE_URL=$DATASOURCE_URL" \
    "DB_PASSWORD=@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=DB-PASSWORD)" \
    "GITHUB_CLIENT_ID=Ov23lio4qDBYKi2LYvZC" \
    "GITHUB_CLIENT_SECRET=@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=GITHUB-CLIENT-SECRET)" \
    "JWT_SECRET=@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=JWT-SECRET)" \
    "CLAUDE_API_KEY=@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=CLAUDE-API-KEY)" \
    "GITHUB_API_TOKEN=@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=GITHUB-API-TOKEN)" \
    "AZURE_STORAGE_CONNECTION_STRING=@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=AZURE-STORAGE-CONNECTION-STRING)" \
    "AZURE_STORAGE_CONTAINER=$STORAGE_CONTAINER" \
  --output none

# ── 8. Static Web Apps ────────────────────────────────────────────────────────
echo "==> Creating Static Web App: $STATIC_WEB_APP"
az staticwebapp create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$STATIC_WEB_APP" \
  --location "eastus2" \
  --source "https://github.com/nitishbhushan2013/codesense-ai" \
  --branch "main" \
  --app-location "frontend/codesense-frontend" \
  --output-location ".next/standalone" \
  --login-with-github \
  --output none

STATIC_WEB_APP_URL=$(az staticwebapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$STATIC_WEB_APP" \
  --query "defaultHostname" \
  --output tsv)

echo "==> Static Web App URL: https://$STATIC_WEB_APP_URL"

# Update App Service with the frontend URL for CORS
az webapp config appsettings set \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --settings "FRONTEND_URL=https://$STATIC_WEB_APP_URL" \
  --output none

# ── 9. Application Insights ───────────────────────────────────────────────────
echo "==> Creating Application Insights: $APP_INSIGHTS"
az monitor app-insights component create \
  --resource-group "$RESOURCE_GROUP" \
  --app "$APP_INSIGHTS" \
  --location "$LOCATION" \
  --kind "web" \
  --output none

APP_INSIGHTS_CONNECTION_STRING=$(az monitor app-insights component show \
  --resource-group "$RESOURCE_GROUP" \
  --app "$APP_INSIGHTS" \
  --query "connectionString" \
  --output tsv)

az webapp config appsettings set \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --settings "APPLICATIONINSIGHTS_CONNECTION_STRING=$APP_INSIGHTS_CONNECTION_STRING" \
  --output none

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "  Provisioning complete!"
echo "============================================================"
echo "  Resource Group:    $RESOURCE_GROUP"
echo "  Backend URL:       https://${APP_SERVICE_NAME}.azurewebsites.net"
echo "  Frontend URL:      https://${STATIC_WEB_APP_URL}"
echo "  PostgreSQL host:   $PG_HOST"
echo "  Key Vault:         $KEY_VAULT"
echo "  App Insights:      $APP_INSIGHTS"
echo ""
echo "  Next steps:"
echo "  1. Run schema:   psql \"$DATASOURCE_URL\" < scripts/run-schema.sql"
echo "  2. Set GitHub Secret AZURE_WEBAPP_PUBLISH_PROFILE:"
echo "     az webapp deployment list-publishing-profiles \\"
echo "       --resource-group $RESOURCE_GROUP --name $APP_SERVICE_NAME --xml"
echo "  3. Set GitHub Secret AZURE_STATIC_WEB_APPS_API_TOKEN:"
echo "     az staticwebapp secrets list --name $STATIC_WEB_APP --query properties.apiKey -o tsv"
echo "  4. Set GitHub Secret NEXT_PUBLIC_API_URL=https://${APP_SERVICE_NAME}.azurewebsites.net"
echo "  5. Push to main to trigger GitHub Actions pipelines."
echo "============================================================"
