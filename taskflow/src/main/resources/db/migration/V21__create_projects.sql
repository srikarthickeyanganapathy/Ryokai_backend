CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    organization_id BIGINT REFERENCES organizations(id),
    team_id BIGINT REFERENCES teams(id),
    created_by_id BIGINT REFERENCES users(id) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    due_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_projects_organization ON projects(organization_id);
CREATE INDEX IF NOT EXISTS idx_projects_team ON projects(team_id);
CREATE INDEX IF NOT EXISTS idx_projects_created_by ON projects(created_by_id);

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES projects(id);
CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks(project_id);
