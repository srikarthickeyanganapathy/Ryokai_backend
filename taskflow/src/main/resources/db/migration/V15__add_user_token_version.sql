-- Add token_version to users for JWT invalidation on password reset
ALTER TABLE users ADD COLUMN token_version INT DEFAULT 0 NOT NULL;
