CREATE TABLE IF NOT EXISTS task_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    default_title VARCHAR(200) NOT NULL,
    default_description TEXT,
    default_priority VARCHAR(20) DEFAULT 'NORMAL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by_id BIGINT REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_task_templates_name ON task_templates(name);
