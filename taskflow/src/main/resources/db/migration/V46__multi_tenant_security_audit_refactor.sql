-- V46: Multi-Tenant Security, Audit & Scope Refactor Migration

-- 1. Add Optimistic Locking and Soft-Delete flags to core domain aggregates
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS scope VARCHAR(20) DEFAULT 'PERSONAL' NOT NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE projects ADD CONSTRAINT fk_projects_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id);

ALTER TABLE checklist_items ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE checklist_items ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE task_evidence ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE task_evidence ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL;

-- 2. Update project_collaborators with granular roles and capability overrides
ALTER TABLE project_collaborators ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'VIEWER' NOT NULL;
ALTER TABLE project_collaborators ADD COLUMN IF NOT EXISTS can_manage_collaborators BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE project_collaborators ADD COLUMN IF NOT EXISTS can_manage_settings BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE project_collaborators ADD COLUMN IF NOT EXISTS can_archive BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE project_collaborators ADD COLUMN IF NOT EXISTS can_delete BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE project_collaborators ADD COLUMN IF NOT EXISTS can_create_tasks BOOLEAN DEFAULT TRUE NOT NULL;

-- 3. Create Append-Only Task Activity Log Table
CREATE TABLE IF NOT EXISTS task_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    actor_id BIGINT REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    metadata JSONB,
    source VARCHAR(30) DEFAULT 'API' NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 4. Create Append-Only Project Activity Log Table
CREATE TABLE IF NOT EXISTS project_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    actor_id BIGINT REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    metadata JSONB,
    source VARCHAR(30) DEFAULT 'API' NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 5. Performance and Security Indexes
CREATE INDEX IF NOT EXISTS idx_task_activity_task_created ON task_activity_logs(task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_activity_correlation ON task_activity_logs(correlation_id);
CREATE INDEX IF NOT EXISTS idx_project_activity_proj_created ON project_activity_logs(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_projects_scope_owner ON projects(scope, owner_user_id);
CREATE INDEX IF NOT EXISTS idx_projects_scope_crew ON projects(scope, crew_id);
CREATE INDEX IF NOT EXISTS idx_projects_scope_org ON projects(scope, organization_id);
