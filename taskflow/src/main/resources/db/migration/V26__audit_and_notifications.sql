-- Audit table for entity changes (role, org, membership, team, leave)
CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id),
    actor_username_snapshot VARCHAR(50),
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT,
    old_value_json JSONB,
    new_value_json JSONB,
    reason VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_events(entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_actor ON audit_events(actor_user_id, occurred_at DESC);

-- Audit table for security/auth events
CREATE TABLE security_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id), -- NULL for LOGIN_FAILED where user may not exist
    actor_username_snapshot VARCHAR(50),
    ip_address VARCHAR(45),
    device_info TEXT, -- Raw User-Agent header
    metadata_json JSONB, -- Structured fields
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),
    success BOOLEAN NOT NULL
);

CREATE INDEX idx_security_audit_actor ON security_audit_events(actor_user_id, occurred_at DESC);
CREATE INDEX idx_security_audit_event_type ON security_audit_events(event_type, occurred_at DESC);

-- Notification actor tracking
ALTER TABLE notifications ADD COLUMN actor_id BIGINT REFERENCES users(id);
CREATE INDEX idx_notifications_actor ON notifications(actor_id);

-- TaskStatusHistory metadata tracking
ALTER TABLE task_status_history ADD COLUMN metadata_json JSONB;
