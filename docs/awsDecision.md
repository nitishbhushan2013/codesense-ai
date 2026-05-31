# CodeSense AI — AWS Resource Decisions

> Local-only reference. AWS equivalent of `docs/azureDecision.md`.
> Use this if migrating or redeploying CodeSense AI to AWS.

---

## Infrastructure map

```
AWS Account
└── VPC: codesense-vpc
    ├── AWS Elastic Beanstalk (or ECS Fargate)   ← Spring Boot backend
    ├── AWS Amplify                               ← Next.js frontend
    ├── Amazon RDS for PostgreSQL                 ← Primary database
    ├── Amazon S3                                 ← Code diffs storage
    │   └── Bucket: codesense-code-diffs
    ├── AWS Secrets Manager                       ← Secrets (API keys, JWT, DB password)
    ├── IAM Roles                                 ← Managed identity (no credentials in code)
    ├── Amazon CloudWatch + AWS X-Ray             ← Monitoring and tracing
    └── AWS CodePipeline + CodeBuild              ← CI/CD pipelines
```

---

## Azure → AWS resource mapping

| Azure Resource | AWS Equivalent | Notes |
|---|---|---|
| App Service (B2) | Elastic Beanstalk or ECS Fargate | EB is simpler; ECS is more portable |
| Static Web Apps | AWS Amplify | Both have first-class Next.js SSR support |
| PostgreSQL Flexible Server | Amazon RDS for PostgreSQL | Direct equivalent |
| Blob Storage | Amazon S3 | S3 is the gold standard; same purpose |
| Key Vault + Managed Identity | Secrets Manager + IAM Roles | IAM Role = Managed Identity; SM = Key Vault |
| Application Insights | CloudWatch + X-Ray | CloudWatch = metrics/logs; X-Ray = tracing |
| Azure DevOps Pipelines | CodePipeline + CodeBuild | Or GitHub Actions (more common in practice) |
| Resource Group | AWS tags / CloudFormation Stack | No direct concept; use tags to group resources |

---

## Resource-by-resource decisions

### AWS Elastic Beanstalk (or ECS Fargate) — Spring Boot backend

**Azure equivalent:** App Service B2

**What the app needs:**
- Always-on JVM process (no cold starts)
- Persistent HTTP connections for SSE streaming (Claude token-by-token responses)
- Auto-restart on crash
- Access to RDS and Secrets Manager via IAM

**Option A — Elastic Beanstalk (recommended for this project):**
Elastic Beanstalk is the closest equivalent to Azure App Service — it's a managed platform that takes a JAR file and handles the EC2 instance, load balancer, health checks, and auto-scaling automatically. For a Spring Boot app you upload the JAR (or use the EB CLI), set environment variables, and it runs. No Docker required.

- Environment type: **Web server** (not Worker)
- Platform: **Java 21 on Amazon Linux 2023**
- Instance: **t3.small** (2 vCPU, 2 GB RAM — equivalent to B2 footprint)
- Load balancer: Application Load Balancer (included)
- IAM instance profile: grants access to Secrets Manager and S3

**Option B — ECS Fargate (for containerised deployment):**
If you containerise the Spring Boot app (`docker build`), ECS Fargate runs it serverlessly — no EC2 to manage. More portable (same image runs anywhere Docker runs), better for multi-environment setups (dev/staging/prod), but adds Docker complexity.

- Task definition: 1 vCPU, 2 GB memory
- Service: 1 desired task (scale up for prod)
- Cluster: Fargate (serverless compute)
- Load balancer: Application Load Balancer in front of the ECS service

**Recommendation:** Start with **Elastic Beanstalk** (mirrors the Azure App Service approach, least new complexity). Migrate to ECS Fargate if you need multiple environments or GitOps-style deployments later.

**What changes in the Spring Boot code:**
- `StorageService` bean condition: change `storage.type=local` to `storage.type=s3` (implement `S3StorageService`)
- Secrets: use `software.amazon.awssdk:secretsmanager` instead of `com.azure:azure-security-keyvault-secrets`
- Application Insights dependency: remove; add CloudWatch agent or Micrometer + CloudWatch exporter

---

### AWS Amplify — Next.js frontend

**Azure equivalent:** Azure Static Web Apps

**What the app needs:**
- Next.js 16 App Router with SSR (Server Components) — not a static export
- Global CDN distribution
- Automatic deployments from GitHub
- Free SSL/TLS

**Why Amplify over S3 + CloudFront:**
S3 + CloudFront works for purely static sites (no server-side rendering). Next.js 16 with App Router uses Node.js at runtime for SSR and Server Actions. Amplify hosts a Node.js compute layer for this. S3 + CloudFront would require a Lambda@Edge function to proxy SSR requests — significant added complexity.

Amplify Gen 2 has first-class Next.js 14+ support with SSR out of the box. Same as Static Web Apps, it connects to a GitHub repo and deploys on every push to `main`.

**What changes in the frontend code:**
- `NEXT_PUBLIC_API_URL`: point to the Elastic Beanstalk URL (or ALB DNS name)
- No other changes — the frontend is cloud-agnostic

**Cost:** Amplify free tier covers 1,000 build minutes/month and 5 GB storage — sufficient for a portfolio project.

---

### Amazon RDS for PostgreSQL — primary database

**Azure equivalent:** Azure Database for PostgreSQL Flexible Server

**Why this is a direct drop-in:**
RDS for PostgreSQL runs the same PostgreSQL engine (version 15/16). The JDBC connection string changes from `jdbc:postgresql://<azure-host>/codesense_db` to `jdbc:postgresql://<rds-endpoint>/codesense_db`. The schema, JPA entities, and all queries are identical — PostgreSQL SQL is portable.

**Configuration:**
- Engine: PostgreSQL 16
- Instance class: **db.t3.micro** (dev/portfolio) → **db.t3.small** (production)
- Storage: 20 GB gp3 (auto-scaling enabled)
- Multi-AZ: disabled for portfolio, enable for production
- VPC: place in private subnet — only accessible from the Elastic Beanstalk/ECS security group

**What changes in the backend code:**
Only `application.yml`: update `spring.datasource.url` to the RDS endpoint. The `DB_PASSWORD` secret moves from Key Vault to Secrets Manager (same env var name, different secret store).

**Alternative — Amazon Aurora PostgreSQL:**
Aurora is a MySQL/PostgreSQL-compatible engine rebuilt by AWS for cloud performance (3× faster writes, 6-way replication). For a portfolio project, the cost difference (~3× more than RDS) isn't justified. Aurora makes sense if you need multi-region replication or thousands of concurrent connections.

---

### Amazon S3 — code diffs storage

**Azure equivalent:** Azure Blob Storage

**Why this is a direct drop-in:**
S3 is the original cloud object store that Azure Blob Storage was designed to be compatible with. Same concept: upload a blob, get a key (S3 object key), retrieve by key. The `StorageService` interface in the codebase makes the swap a single implementation change.

**What changes in the backend code:**

1. Add dependency: `software.amazon.awssdk:s3`
2. Implement `S3StorageService implements StorageService`:
```java
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {
    private final S3Client s3;
    private final String bucket;

    public String store(String content) {
        String key = "code-diffs/" + UUID.randomUUID();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                     RequestBody.fromString(content));
        return key;
    }
}
```
3. Add `storage.type=s3` and `storage.s3.bucket=codesense-code-diffs` to `application.yml`
4. Grant the EC2/ECS IAM role `s3:PutObject` and `s3:GetObject` on the bucket

**Bucket configuration:**
- Block all public access: ON (blobs are private, accessed only by the backend)
- Versioning: OFF (each review creates a new key; no need to version)
- Lifecycle rule: delete objects older than 90 days (optional, cost control)

---

### AWS Secrets Manager + IAM Roles — secrets management

**Azure equivalent:** Azure Key Vault + Managed Identity

**How it maps:**

| Azure concept | AWS equivalent |
|---|---|
| Managed Identity | IAM Role attached to EC2 / ECS task |
| Key Vault | AWS Secrets Manager |
| Key Vault access policy | IAM policy on the role granting `secretsmanager:GetSecretValue` |

The same zero-credential principle applies: the EC2 instance (Elastic Beanstalk) or ECS task gets an IAM role at launch. That role has an inline policy permitting `secretsmanager:GetSecretValue` on the specific secrets. The backend uses the AWS SDK to fetch secrets at startup — no secret ever appears in a config file.

**Secrets to migrate:**
```
/codesense/claude-api-key          → CLAUDE_API_KEY
/codesense/github-client-secret    → GITHUB_CLIENT_SECRET
/codesense/jwt-secret              → JWT_SECRET
/codesense/db-password             → DB_PASSWORD
/codesense/github-api-token        → GITHUB_API_TOKEN
```

**What changes in the backend code:**
Replace the Azure Key Vault SDK with the AWS Secrets Manager SDK:
```java
// Add: software.amazon.awssdk:secretsmanager
SecretsManagerClient client = SecretsManagerClient.create();
GetSecretValueResponse response = client.getSecretValue(
    GetSecretValueRequest.builder().secretId("/codesense/claude-api-key").build()
);
String apiKey = response.secretString();
```

Spring Boot's AWS integration (`spring-cloud-aws-starter`) can inject secrets directly as `@Value("${/codesense/claude-api-key}")` — minimising code changes.

**Alternative — AWS Systems Manager Parameter Store:**
SSM Parameter Store is free for standard parameters (vs Secrets Manager at $0.40/secret/month). For 5 secrets = $2/month — negligible. Secrets Manager is preferred because it has automatic rotation support (useful for `DB_PASSWORD` if you enable RDS password rotation) and a cleaner secrets-oriented API.

---

### Amazon CloudWatch + AWS X-Ray — monitoring and observability

**Azure equivalent:** Azure Application Insights

**Why two services instead of one:**
Application Insights is an all-in-one APM tool. AWS splits this across services:
- **CloudWatch** — metrics, logs, alarms, dashboards (equivalent to App Insights metrics + log analytics)
- **X-Ray** — distributed tracing, service map, latency analysis (equivalent to App Insights distributed tracing)

**CloudWatch setup for Spring Boot:**
- Elastic Beanstalk streams application logs to CloudWatch Logs automatically
- Add `micrometer-registry-cloudwatch2` to publish custom metrics (JVM heap, request counts, Claude API latency)
- Create alarms: error rate > 5%, p99 latency > 10s, Claude API failures

**X-Ray setup:**
- Add `aws-xray-recorder-sdk-spring` dependency
- Traces every inbound HTTP request and outbound HTTP call (to Claude API, GitHub API)
- Visualise the full call graph: Frontend → Backend → Claude API → response

**What changes in the backend code:**
- Remove Application Insights agent (`applicationinsights-spring-boot-starter`)
- Add `micrometer-registry-cloudwatch2` and configure the CloudWatch namespace
- Add `aws-xray-recorder-sdk-spring` for tracing

**Alternative — AWS CloudWatch Application Insights:**
AWS has a feature called CloudWatch Application Insights that auto-discovers Spring Boot apps on EC2/Elastic Beanstalk and configures dashboards automatically. This is the closest single-service equivalent to Azure Application Insights — less configuration than manually wiring Micrometer + CloudWatch.

---

### AWS CodePipeline + CodeBuild — CI/CD

**Azure equivalent:** Azure DevOps Pipelines

**Pipeline design (equivalent to the Azure pipelines in `ARCHITECTURE.md`):**

**Backend pipeline:**
```
Source: GitHub (main branch push)
  → CodeBuild: mvn clean install (compile + test)
  → CodeBuild: mvn package (build JAR)
  → Elastic Beanstalk Deploy: upload new application version
```

**Frontend pipeline:**
```
Source: GitHub (main branch push)
  → Amplify Build: npm install + npm run build
  → Amplify Deploy: automatic (Amplify handles this natively)
```

Note: Amplify has its own built-in CI/CD (connects directly to GitHub). You don't need CodePipeline for the frontend — Amplify's build pipeline is sufficient and simpler.

**Alternative — GitHub Actions:**
GitHub Actions is more commonly used in practice than CodePipeline for GitHub-hosted repos. AWS provides official actions (`aws-actions/configure-aws-credentials`, `aws-actions/aws-elasticbeanstalk-deploy`). The pipeline steps are identical; the tooling is YAML in `.github/workflows/` rather than CodePipeline stages. Either works — GitHub Actions has less AWS-specific lock-in.

---

## Code changes required to migrate from Azure to AWS

| File | Change |
|---|---|
| `application.yml` | `spring.datasource.url` → RDS endpoint; `storage.type=s3` |
| `StorageService` | Add `S3StorageService` implementation; `@ConditionalOnProperty(storage.type=s3)` |
| `pom.xml` | Swap Azure SDK deps → AWS SDK deps (`software.amazon.awssdk:s3`, `secretsmanager`, `xray`) |
| `application.yml` | Remove azure-specific config blocks; add S3 bucket name, AWS region |
| `LocalStorageService` | No change — still useful for local dev |
| `ClaudeService` | No change — calls Anthropic API directly, not cloud-specific |
| `GitHubService` | No change — calls GitHub API directly |
| All business logic | No change — cloud-agnostic |

**Estimated migration effort:** 2–3 days. The `StorageService` interface (added specifically to enable this swap) and the secrets env var abstraction (STORY-108) mean the application layer is already decoupled from the cloud provider.

---

## Cost comparison (portfolio/dev workload)

| Resource | Azure (monthly) | AWS equivalent (monthly) |
|---|---|---|
| Backend compute | App Service B2 ~$75 | EB t3.small ~$15 (EC2) |
| Frontend | Static Web Apps Free | Amplify Free tier |
| Database | PostgreSQL Flexible B1ms ~$12 | RDS db.t3.micro ~$15 |
| Blob/Object storage | Blob ~$0.02 | S3 ~$0.02 |
| Secrets | Key Vault Standard ~$5 | Secrets Manager ~$2 |
| Monitoring | App Insights Free (5 GB) | CloudWatch Free tier |
| **Total** | **~$92/month** | **~$32/month** |

> AWS is roughly 3× cheaper at the dev tier because Elastic Beanstalk uses an EC2 instance billed by the second (can be stopped when not in use), while App Service bills per hour even when idle. For a live production app with uptime requirements, the gap narrows.
