-- V24 Migration: Add unique constraint to refresh_tokens.token_hash
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS uk_refresh_tokens_token_hash;
ALTER TABLE refresh_tokens ADD CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash);
