-- ============================================================================
-- V19: Organization tables, constraints, and role seeding
-- Fixes: #2 (one-org DB enforcement), #13 (missing tables), #14 (missing roles)
-- ============================================================================

-- Organizations table
CREATE TABLE IF NOT EXISTS organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Organization memberships with one-org-per-user constraint
CREATE TABLE IF NOT EXISTS organization_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    org_role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_membership_user_org UNIQUE (user_id, organization_id),
    CONSTRAINT uk_membership_one_org_per_user UNIQUE (user_id)
);

-- Teams table
CREATE TABLE IF NOT EXISTS teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    created_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Team members join table
CREATE TABLE IF NOT EXISTS team_members (
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, user_id)
);

-- Leave requests table
CREATE TABLE IF NOT EXISTS leave_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_comment TEXT,
    reviewed_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    reviewed_at TIMESTAMP
);

-- Add status column to organizations if it doesn't exist (for suspend/activate support)
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Add organization_id to tasks table if not present (for org-scoped tasks)
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS organization_id BIGINT REFERENCES organizations(id);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS team_id BIGINT REFERENCES teams(id);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_org_memberships_user ON organization_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_org_memberships_org ON organization_memberships(organization_id);
CREATE INDEX IF NOT EXISTS idx_teams_org ON teams(organization_id);
CREATE INDEX IF NOT EXISTS idx_team_members_user ON team_members(user_id);
CREATE INDEX IF NOT EXISTS idx_leave_requests_user_org ON leave_requests(user_id, organization_id);
CREATE INDEX IF NOT EXISTS idx_tasks_org ON tasks(organization_id);

-- ============================================================================
-- Seed missing roles so V4's permission mapping actually works
-- ============================================================================
INSERT INTO roles (name, description)
SELECT 'EMPLOYEE', 'Default employee role'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'EMPLOYEE');

INSERT INTO roles (name, description)
SELECT 'MANAGER', 'Manager role with team oversight'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'MANAGER');

INSERT INTO roles (name, description)
SELECT 'DIRECTOR', 'Director role with broad authority'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'DIRECTOR');

-- Re-apply V4's permission mappings now that the roles exist
-- EMPLOYEE permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'EMPLOYEE' AND p.name IN ('TASK_VIEW', 'TASK_COMMENT', 'TASK_CHECKLIST_EDIT')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MANAGER' AND p.name IN (
    'TASK_VIEW', 'TASK_CREATE', 'TASK_ASSIGN', 'TASK_EDIT', 'TASK_REVIEW',
    'TASK_COMMENT', 'TASK_CHECKLIST_EDIT', 'TASK_DEPENDENCY_EDIT',
    'TASK_REASSIGN', 'TEMPLATE_MANAGE'
)
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- DIRECTOR permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DIRECTOR' AND p.name IN (
    'TASK_VIEW', 'TASK_CREATE', 'TASK_ASSIGN', 'TASK_EDIT', 'TASK_DELETE',
    'TASK_REVIEW', 'TASK_COMMENT', 'TASK_CHECKLIST_EDIT', 'TASK_DEPENDENCY_EDIT',
    'TASK_REASSIGN', 'TASK_ARCHIVE', 'USER_MANAGE', 'TEMPLATE_MANAGE'
)
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
