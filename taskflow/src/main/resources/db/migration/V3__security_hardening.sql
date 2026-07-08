ALTER TABLE refresh_tokens ADD COLUMN token_hash VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE refresh_tokens ADD COLUMN token_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN used BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE refresh_tokens ADD COLUMN used_at TIMESTAMP;
ALTER TABLE refresh_tokens ADD COLUMN device_info VARCHAR(500);

CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- Insert SUPER_ADMIN role if it does not exist
INSERT INTO roles (name, description)
SELECT 'SUPER_ADMIN', 'Super Administrator with all permissions'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SUPER_ADMIN');

-- If you don't have a description column yet, you will need to add it, or remove from the insert above if it already exists.
-- But the prompt asks to add description column to roles table.
ALTER TABLE roles ADD COLUMN IF NOT EXISTS description VARCHAR(255);

-- Let's update the existing SUPER_ADMIN if description was just added
UPDATE roles SET description = 'Super Administrator with all permissions' WHERE name = 'SUPER_ADMIN';
