# CodeSense AI — Project Brief

## Project Name

**CodeSense AI** — An AI-Powered Code Review Assistant

## Problem Statement

Developers waste hours on manual code reviews that are inconsistent, slow, and dependent on reviewer availability. Junior developers get little explanation of _why_ something is wrong. Security issues slip through undetected. There is no intelligent, always-available assistant that gives instant, structured, educational code review feedback with actionable fix suggestions.

## Vision

A web application where any developer — solo, enterprise, or learner — submits a GitHub PR URL or raw code, and an AI engine instantly returns structured findings across bugs, security, performance, and code quality, complete with suggested fixed code snippets and a chat interface to ask follow-up questions. All reviews are saved to a personal history dashboard.

## Target Users

| User Type           | Their Need                                      |
| ------------------- | ----------------------------------------------- |
| Solo developer      | Expert-level review without a team              |
| Enterprise dev team | Pre-screen PRs before human review              |
| Junior developer    | Learn _why_ something is wrong, not just _what_ |
| Senior developer    | Showcase AI-integrated engineering on LinkedIn  |

## Core Goals

1. Accept a GitHub PR URL or raw code paste as input
2. Analyse code using Claude AI across 4 dimensions: bugs, security, performance, quality
3. Return structured findings with severity levels and fixed code snippets
4. Allow follow-up chat with AI about any finding
5. App is publicly accessible — anyone can submit code without login
6. Users who want to save history must register or log in via GitHub OAuth or Email/Password
7. Deploy publicly on Azure, accessible via a shareable URL

## Out of Scope (v1)

| Feature                    | Reason                    |
| -------------------------- | ------------------------- |
| Auto-push fixes to GitHub  | Too complex, risky for v1 |
| GitLab / Bitbucket support | GitHub-first strategy     |
| Team workspaces            | Multi-user complexity     |
| IDE plugin                 | Separate product surface  |
| Billing / subscriptions    | Not needed for portfolio  |

## Success Criteria

- Developer submits PR URL or pastes code and receives full AI review within 30 seconds
- Each finding shows severity, description, and suggested code fix
- Users can chat with AI about any specific finding
- All reviews saved and accessible from personal dashboard
- App is live on Azure with public URL
- GitHub repo is clean, documented, and portfolio-ready

## Tech Stack

| Layer           | Technology                    |
| --------------- | ----------------------------- |
| Frontend        | React + Next.js               |
| Backend         | Java 21 + Spring Boot 3       |
| AI Engine       | Claude API (Anthropic)        |
| Authentication  | GitHub OAuth + Email/Password |
| Database        | PostgreSQL (Azure Database)   |
| Storage         | Azure Blob Storage            |
| Cloud           | Microsoft Azure               |
| CI/CD           | Azure DevOps Pipelines        |
| Frontend Deploy | Azure Static Web Apps         |
| Backend Deploy  | Azure App Service             |
| Monitoring      | Azure Application Insights    |
