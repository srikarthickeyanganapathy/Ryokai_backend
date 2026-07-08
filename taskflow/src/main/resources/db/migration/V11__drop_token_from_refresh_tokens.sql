-- MIG-006: V2->V10 had a redundant 'token' column added as NOT NULL UNIQUE.
-- This migration safely drops it as it was replaced by token_hash.
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS token;
