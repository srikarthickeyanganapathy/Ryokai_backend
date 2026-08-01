-- Add priority_tier to notifications
ALTER TABLE notifications ADD COLUMN priority_tier VARCHAR(20) DEFAULT 'ACTIVITY' NOT NULL;

-- Create session_memory table
CREATE TABLE session_memory (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    last_workspace_lens VARCHAR(50),
    active_filters JSONB,
    last_active_drawer VARCHAR(50),
    recent_searches JSONB,
    pinned_items JSONB,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_session_memory_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_session_memory_user_id ON session_memory (user_id);
