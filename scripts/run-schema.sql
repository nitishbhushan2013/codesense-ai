-- CodeSense AI — Database Schema
-- Run against the Azure PostgreSQL database after provisioning:
--   psql "jdbc:postgresql://<host>:5432/codesense_db?sslmode=require&user=<admin>&password=<pw>" -f scripts/run-schema.sql
-- Or using psql URI format:
--   psql "postgresql://<admin>:<pw>@<host>:5432/codesense_db?sslmode=require" -f scripts/run-schema.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) UNIQUE,
    name        VARCHAR(255) NOT NULL,
    password    VARCHAR(255),
    github_id   VARCHAR(100) UNIQUE,
    avatar_url  VARCHAR(500),
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    submission_type VARCHAR(20) NOT NULL,
    language        VARCHAR(50),
    pr_url          VARCHAR(500),
    blob_key        VARCHAR(500),
    score           INTEGER,
    summary         TEXT,
    status          VARCHAR(20) DEFAULT 'completed',
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS findings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id       UUID REFERENCES reviews(id) ON DELETE CASCADE,
    category        VARCHAR(20) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    line_reference  VARCHAR(100),
    description     TEXT NOT NULL,
    suggested_fix   TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id   UUID REFERENCES reviews(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);
