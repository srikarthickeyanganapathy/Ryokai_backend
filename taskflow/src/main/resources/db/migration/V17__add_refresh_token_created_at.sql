-- V17: Add created_at to refresh_tokens
ALTER TABLE refresh_tokens ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
