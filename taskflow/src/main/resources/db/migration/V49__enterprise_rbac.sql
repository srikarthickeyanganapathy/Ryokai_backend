-- ============================================================================
-- V49: Enterprise RBAC Migration
--
-- Introduces scoped permissions, resource assignments, policies,
-- field restrictions, user overrides, and audit logging.
--
-- This migration is ADDITIVE — it creates new tables and columns alongside
-- the existing schema. The legacy role_permissions table is preserved until
-- Phase 4 cleanup.
-- ============================================================================

-- ──────────────────────────────────────────────────────────────────────────────
-- 1. SCOPES TABLE
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE scopes (
    id       BIGSERIAL PRIMARY KEY,
    code     VARCHAR(20) NOT NULL UNIQUE,
    priority INT NOT NULL
);

CREATE INDEX idx_scopes_priority ON scopes(priority);

INSERT INTO scopes (code, priority) VALUES
    ('OWN', 0),
    ('PROJECT', 10),
    ('TEAM', 20),
    ('ORGANIZATION', 30);

-- ──────────────────────────────────────────────────────────────────────────────
-- 2. EXTEND PERMISSIONS TABLE
-- ──────────────────────────────────────────────────────────────────────────────
-- Add new columns alongside existing (name, description)
-- 'code' will be the new primary identifier; 'name' is preserved for backward compat
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS code VARCHAR(80);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS module VARCHAR(40);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS category VARCHAR(20);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Backfill code from name for existing rows
UPDATE permissions SET code = name WHERE code IS NULL;

-- Make code NOT NULL and UNIQUE after backfill
ALTER TABLE permissions ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_permissions_code ON permissions(code);

-- Backfill module and category for existing permissions
UPDATE permissions SET module = 'TASK',         category = 'CRUD'       WHERE code = 'TASK_VIEW';
UPDATE permissions SET module = 'TASK',         category = 'WORKFLOW'   WHERE code = 'TASK_ASSIGN';
UPDATE permissions SET module = 'TASK',         category = 'CRUD'       WHERE code = 'TASK_EDIT';
UPDATE permissions SET module = 'TASK',         category = 'CRUD'       WHERE code = 'TASK_DELETE';
UPDATE permissions SET module = 'TASK',         category = 'WORKFLOW'   WHERE code = 'TASK_REVIEW';
UPDATE permissions SET module = 'TASK',         category = 'SETTINGS'   WHERE code = 'TASK_DEPENDENCY_EDIT';
UPDATE permissions SET module = 'TASK',         category = 'WORKFLOW'   WHERE code = 'TASK_REASSIGN';
UPDATE permissions SET module = 'TASK',         category = 'LIFECYCLE'  WHERE code = 'TASK_ARCHIVE';
UPDATE permissions SET module = 'TASK',         category = 'WORKFLOW'   WHERE code = 'TASK_OVERRIDE';
UPDATE permissions SET module = 'ROLE',         category = 'CRUD'       WHERE code = 'ROLE_MANAGE';
UPDATE permissions SET module = 'MEMBER',       category = 'MEMBERSHIP' WHERE code = 'ORG_MEMBER_INVITE';
UPDATE permissions SET module = 'MEMBER',       category = 'MEMBERSHIP' WHERE code = 'ORG_MEMBER_REMOVE';
UPDATE permissions SET module = 'LEAVE',        category = 'WORKFLOW'   WHERE code = 'LEAVE_REQUEST_MANAGE';
UPDATE permissions SET module = 'TEAM',         category = 'CRUD'       WHERE code = 'TEAM_CREATE';
UPDATE permissions SET module = 'TEAM',         category = 'CRUD'       WHERE code = 'TEAM_MANAGE';
UPDATE permissions SET module = 'PROJECT',      category = 'CRUD'       WHERE code = 'PROJECT_CREATE';
UPDATE permissions SET module = 'PROJECT',      category = 'CRUD'       WHERE code = 'PROJECT_MANAGE';
UPDATE permissions SET module = 'ANNOUNCEMENT', category = 'CRUD'       WHERE code = 'ANNOUNCEMENT_MANAGE';
UPDATE permissions SET module = 'GOAL',         category = 'CRUD'       WHERE code = 'GOAL_MANAGE';
UPDATE permissions SET module = 'DASHBOARD',    category = 'CRUD'       WHERE code = 'DASHBOARD_ORG_WIDE_VIEW';

-- Mark all existing permissions as system
UPDATE permissions SET is_system = TRUE;

CREATE INDEX IF NOT EXISTS idx_permissions_module ON permissions(module);
CREATE INDEX IF NOT EXISTS idx_permissions_category ON permissions(category);

-- ──────────────────────────────────────────────────────────────────────────────
-- 3. INSERT NEW PERMISSIONS (83 total, skip duplicates)
-- ──────────────────────────────────────────────────────────────────────────────

-- Organization (7)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('ORG_VIEW', 'ORG_VIEW', 'ORGANIZATION', 'CRUD', 'View organization details', TRUE),
    ('ORG_PROFILE_UPDATE', 'ORG_PROFILE_UPDATE', 'ORGANIZATION', 'SETTINGS', 'Update organization profile', TRUE),
    ('ORG_ARCHIVE', 'ORG_ARCHIVE', 'ORGANIZATION', 'LIFECYCLE', 'Archive the organization', TRUE),
    ('ORG_RESTORE', 'ORG_RESTORE', 'ORGANIZATION', 'LIFECYCLE', 'Restore an archived organization', TRUE),
    ('ORG_SETTINGS_VIEW', 'ORG_SETTINGS_VIEW', 'ORGANIZATION', 'SETTINGS', 'View organization settings', TRUE),
    ('ORG_SETTINGS_UPDATE', 'ORG_SETTINGS_UPDATE', 'ORGANIZATION', 'SETTINGS', 'Modify organization settings', TRUE),
    ('ORG_TRANSFER_OWNERSHIP', 'ORG_TRANSFER_OWNERSHIP', 'ORGANIZATION', 'WORKFLOW', 'Transfer organization ownership', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Members (7)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('MEMBER_VIEW', 'MEMBER_VIEW', 'MEMBER', 'CRUD', 'View member profiles and directory', TRUE),
    ('MEMBER_INVITE', 'MEMBER_INVITE', 'MEMBER', 'MEMBERSHIP', 'Send membership invitations', TRUE),
    ('MEMBER_REMOVE', 'MEMBER_REMOVE', 'MEMBER', 'MEMBERSHIP', 'Remove a member from the organization', TRUE),
    ('MEMBER_ROLE_UPDATE', 'MEMBER_ROLE_UPDATE', 'MEMBER', 'MEMBERSHIP', 'Change a member assigned role', TRUE),
    ('MEMBER_SUSPEND', 'MEMBER_SUSPEND', 'MEMBER', 'WORKFLOW', 'Temporarily suspend a member', TRUE),
    ('MEMBER_REACTIVATE', 'MEMBER_REACTIVATE', 'MEMBER', 'WORKFLOW', 'Reactivate a suspended member', TRUE),
    ('MEMBER_EXPORT', 'MEMBER_EXPORT', 'MEMBER', 'EXPORT', 'Export member directory data', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Teams (8) — TEAM_CREATE already exists
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('TEAM_VIEW', 'TEAM_VIEW', 'TEAM', 'CRUD', 'View team details and roster', TRUE),
    ('TEAM_UPDATE', 'TEAM_UPDATE', 'TEAM', 'CRUD', 'Update team metadata', TRUE),
    ('TEAM_DELETE', 'TEAM_DELETE', 'TEAM', 'CRUD', 'Delete a team', TRUE),
    ('TEAM_ARCHIVE', 'TEAM_ARCHIVE', 'TEAM', 'LIFECYCLE', 'Archive a team', TRUE),
    ('TEAM_MEMBER_ADD', 'TEAM_MEMBER_ADD', 'TEAM', 'MEMBERSHIP', 'Add members to a team', TRUE),
    ('TEAM_MEMBER_REMOVE', 'TEAM_MEMBER_REMOVE', 'TEAM', 'MEMBERSHIP', 'Remove members from a team', TRUE),
    ('TEAM_MEMBER_ROLE_UPDATE', 'TEAM_MEMBER_ROLE_UPDATE', 'TEAM', 'MEMBERSHIP', 'Change member team role', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;
-- Update TEAM_CREATE
UPDATE permissions SET code = 'TEAM_CREATE', module = 'TEAM', category = 'CRUD' WHERE name = 'TEAM_CREATE';

-- Projects (11) — PROJECT_CREATE already exists
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('PROJECT_VIEW', 'PROJECT_VIEW', 'PROJECT', 'CRUD', 'View project details and metadata', TRUE),
    ('PROJECT_UPDATE', 'PROJECT_UPDATE', 'PROJECT', 'CRUD', 'Update project metadata', TRUE),
    ('PROJECT_DELETE', 'PROJECT_DELETE', 'PROJECT', 'CRUD', 'Permanently delete a project', TRUE),
    ('PROJECT_ARCHIVE', 'PROJECT_ARCHIVE', 'PROJECT', 'LIFECYCLE', 'Archive a project', TRUE),
    ('PROJECT_RESTORE', 'PROJECT_RESTORE', 'PROJECT', 'LIFECYCLE', 'Restore an archived project', TRUE),
    ('PROJECT_SETTINGS_UPDATE', 'PROJECT_SETTINGS_UPDATE', 'PROJECT', 'SETTINGS', 'Modify project-level settings', TRUE),
    ('PROJECT_MEMBER_ADD', 'PROJECT_MEMBER_ADD', 'PROJECT', 'MEMBERSHIP', 'Add collaborators to a project', TRUE),
    ('PROJECT_MEMBER_REMOVE', 'PROJECT_MEMBER_REMOVE', 'PROJECT', 'MEMBERSHIP', 'Remove collaborators', TRUE),
    ('PROJECT_MEMBER_ROLE_UPDATE', 'PROJECT_MEMBER_ROLE_UPDATE', 'PROJECT', 'MEMBERSHIP', 'Change collaborator project role', TRUE),
    ('PROJECT_EXPORT', 'PROJECT_EXPORT', 'PROJECT', 'EXPORT', 'Export project data', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;
UPDATE permissions SET code = 'PROJECT_CREATE', module = 'PROJECT', category = 'CRUD' WHERE name = 'PROJECT_CREATE';

-- Tasks (17) — several already exist
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('TASK_CREATE', 'TASK_CREATE', 'TASK', 'CRUD', 'Create new tasks', TRUE),
    ('TASK_UPDATE', 'TASK_UPDATE', 'TASK', 'CRUD', 'Update task fields', TRUE),
    ('TASK_RESTORE', 'TASK_RESTORE', 'TASK', 'LIFECYCLE', 'Restore an archived task', TRUE),
    ('TASK_START', 'TASK_START', 'TASK', 'WORKFLOW', 'Transition a task to in-progress', TRUE),
    ('TASK_SUBMIT', 'TASK_SUBMIT', 'TASK', 'WORKFLOW', 'Submit a task for review', TRUE),
    ('TASK_APPROVE', 'TASK_APPROVE', 'TASK', 'WORKFLOW', 'Approve a submitted task', TRUE),
    ('TASK_REJECT', 'TASK_REJECT', 'TASK', 'WORKFLOW', 'Reject a submitted task', TRUE),
    ('TASK_REOPEN', 'TASK_REOPEN', 'TASK', 'WORKFLOW', 'Reopen a completed or closed task', TRUE),
    ('TASK_CANCEL', 'TASK_CANCEL', 'TASK', 'WORKFLOW', 'Cancel a task without completion', TRUE),
    ('TASK_DEPENDENCY_UPDATE', 'TASK_DEPENDENCY_UPDATE', 'TASK', 'SETTINGS', 'Modify task dependencies', TRUE),
    ('TASK_COMMENT_CREATE', 'TASK_COMMENT_CREATE', 'TASK', 'CRUD', 'Create comments on tasks', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Goals (7)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('GOAL_VIEW', 'GOAL_VIEW', 'GOAL', 'CRUD', 'View goals and key results', TRUE),
    ('GOAL_CREATE', 'GOAL_CREATE', 'GOAL', 'CRUD', 'Create new goals', TRUE),
    ('GOAL_UPDATE', 'GOAL_UPDATE', 'GOAL', 'CRUD', 'Update goal metadata', TRUE),
    ('GOAL_DELETE', 'GOAL_DELETE', 'GOAL', 'CRUD', 'Delete a goal', TRUE),
    ('GOAL_ARCHIVE', 'GOAL_ARCHIVE', 'GOAL', 'LIFECYCLE', 'Archive a goal', TRUE),
    ('GOAL_ASSIGN', 'GOAL_ASSIGN', 'GOAL', 'WORKFLOW', 'Assign ownership of a goal', TRUE),
    ('GOAL_PROGRESS_UPDATE', 'GOAL_PROGRESS_UPDATE', 'GOAL', 'WORKFLOW', 'Update key result progress', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Announcements (5)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT', 'CRUD', 'View announcements', TRUE),
    ('ANNOUNCEMENT_CREATE', 'ANNOUNCEMENT_CREATE', 'ANNOUNCEMENT', 'CRUD', 'Create announcements', TRUE),
    ('ANNOUNCEMENT_UPDATE', 'ANNOUNCEMENT_UPDATE', 'ANNOUNCEMENT', 'CRUD', 'Edit announcements', TRUE),
    ('ANNOUNCEMENT_DELETE', 'ANNOUNCEMENT_DELETE', 'ANNOUNCEMENT', 'CRUD', 'Delete announcements', TRUE),
    ('ANNOUNCEMENT_PIN', 'ANNOUNCEMENT_PIN', 'ANNOUNCEMENT', 'WORKFLOW', 'Pin/unpin announcements', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Leave Management (7)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('LEAVE_VIEW', 'LEAVE_VIEW', 'LEAVE', 'CRUD', 'View leave requests', TRUE),
    ('LEAVE_CREATE', 'LEAVE_CREATE', 'LEAVE', 'CRUD', 'Submit a leave request', TRUE),
    ('LEAVE_UPDATE', 'LEAVE_UPDATE', 'LEAVE', 'CRUD', 'Modify a pending leave request', TRUE),
    ('LEAVE_DELETE', 'LEAVE_DELETE', 'LEAVE', 'CRUD', 'Cancel or delete a leave request', TRUE),
    ('LEAVE_APPROVE', 'LEAVE_APPROVE', 'LEAVE', 'WORKFLOW', 'Approve a pending leave request', TRUE),
    ('LEAVE_REJECT', 'LEAVE_REJECT', 'LEAVE', 'WORKFLOW', 'Reject a pending leave request', TRUE),
    ('LEAVE_SETTINGS_UPDATE', 'LEAVE_SETTINGS_UPDATE', 'LEAVE', 'SETTINGS', 'Configure leave policies', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Calendar (5)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('CALENDAR_VIEW', 'CALENDAR_VIEW', 'CALENDAR', 'CRUD', 'View calendar events', TRUE),
    ('CALENDAR_CREATE', 'CALENDAR_CREATE', 'CALENDAR', 'CRUD', 'Create calendar events', TRUE),
    ('CALENDAR_UPDATE', 'CALENDAR_UPDATE', 'CALENDAR', 'CRUD', 'Modify calendar events', TRUE),
    ('CALENDAR_DELETE', 'CALENDAR_DELETE', 'CALENDAR', 'CRUD', 'Delete calendar events', TRUE),
    ('CALENDAR_EXPORT', 'CALENDAR_EXPORT', 'CALENDAR', 'EXPORT', 'Export calendar data', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Dashboard & Analytics (3)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('DASHBOARD_VIEW', 'DASHBOARD_VIEW', 'DASHBOARD', 'CRUD', 'View dashboard metrics', TRUE),
    ('DASHBOARD_EXPORT', 'DASHBOARD_EXPORT', 'DASHBOARD', 'EXPORT', 'Export dashboard reports', TRUE),
    ('DASHBOARD_WIDGET_UPDATE', 'DASHBOARD_WIDGET_UPDATE', 'DASHBOARD', 'SETTINGS', 'Customize dashboard widgets', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Activity History (2)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('ACTIVITY_VIEW', 'ACTIVITY_VIEW', 'ACTIVITY', 'CRUD', 'View activity logs', TRUE),
    ('ACTIVITY_EXPORT', 'ACTIVITY_EXPORT', 'ACTIVITY', 'EXPORT', 'Export activity history', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- Roles & Permissions (4)
INSERT INTO permissions (name, code, module, category, description, is_system) VALUES
    ('ROLE_VIEW', 'ROLE_VIEW', 'ROLE', 'CRUD', 'View roles and permissions', TRUE),
    ('ROLE_CREATE', 'ROLE_CREATE', 'ROLE', 'CRUD', 'Create custom roles', TRUE),
    ('ROLE_UPDATE', 'ROLE_UPDATE', 'ROLE', 'CRUD', 'Modify role permissions', TRUE),
    ('ROLE_DELETE', 'ROLE_DELETE', 'ROLE', 'CRUD', 'Delete custom roles', TRUE)
ON CONFLICT (name) DO UPDATE SET
    code = EXCLUDED.code,
    module = EXCLUDED.module,
    category = EXCLUDED.category,
    is_system = EXCLUDED.is_system;

-- ──────────────────────────────────────────────────────────────────────────────
-- 4. EXTEND ROLES TABLE
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS max_scope_id BIGINT REFERENCES scopes(id);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Mark ADMIN and SUPER_ADMIN as system roles (cannot be deleted or modified)
UPDATE roles SET is_system = TRUE WHERE name IN ('ADMIN', 'SUPER_ADMIN');

-- Set max_scope for builtin roles
UPDATE roles SET max_scope_id = (SELECT id FROM scopes WHERE code = 'ORGANIZATION')
    WHERE name IN ('ADMIN', 'DIRECTOR', 'MANAGER', 'EMPLOYEE');

-- Set correct priorities for builtin roles
UPDATE roles SET priority = 10  WHERE name = 'ADMIN';
UPDATE roles SET priority = 20  WHERE name = 'DIRECTOR';
UPDATE roles SET priority = 30  WHERE name = 'MANAGER';
UPDATE roles SET priority = 50  WHERE name = 'EMPLOYEE';

-- ──────────────────────────────────────────────────────────────────────────────
-- 5. ROLE_PERMISSION_SCOPES TABLE
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE role_permission_scopes (
    id            BIGSERIAL PRIMARY KEY,
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    scope_id      BIGINT NOT NULL REFERENCES scopes(id) ON DELETE CASCADE,
    UNIQUE (role_id, permission_id, scope_id)
);

CREATE INDEX idx_rps_role ON role_permission_scopes(role_id);
CREATE INDEX idx_rps_permission ON role_permission_scopes(permission_id);

-- Migrate existing role_permissions into role_permission_scopes
-- Default all existing grants to ORGANIZATION scope
INSERT INTO role_permission_scopes (role_id, permission_id, scope_id)
SELECT rp.role_id, rp.permission_id, (SELECT id FROM scopes WHERE code = 'ORGANIZATION')
FROM role_permissions rp
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- 6. RESOURCE_ASSIGNMENTS TABLE
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE resource_assignments (
    id                       BIGSERIAL PRIMARY KEY,
    role_permission_scope_id BIGINT NOT NULL REFERENCES role_permission_scopes(id) ON DELETE CASCADE,
    resource_type            VARCHAR(20) NOT NULL,
    resource_id              BIGINT NOT NULL,
    UNIQUE (role_permission_scope_id, resource_type, resource_id)
);

CREATE INDEX idx_ra_resource ON resource_assignments(resource_type, resource_id);

-- ──────────────────────────────────────────────────────────────────────────────
-- 7. PERMISSION_POLICIES TABLE
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE permission_policies (
    id               BIGSERIAL PRIMARY KEY,
    permission_id    BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    policy_key       VARCHAR(60) NOT NULL,
    policy_params    JSONB,
    operator         VARCHAR(5) NOT NULL DEFAULT 'AND',
    evaluation_order INT NOT NULL DEFAULT 0,
    is_required      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_pp_permission ON permission_policies(permission_id);

-- ──────────────────────────────────────────────────────────────────────────────
-- 8. FIELD_RESTRICTIONS TABLE
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE field_restrictions (
    id            BIGSERIAL PRIMARY KEY,
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    resource_type VARCHAR(40) NOT NULL,
    field_name    VARCHAR(60) NOT NULL,
    access_level  VARCHAR(20) NOT NULL,
    UNIQUE (role_id, resource_type, field_name)
);

CREATE INDEX idx_fr_role_resource ON field_restrictions(role_id, resource_type);

-- ──────────────────────────────────────────────────────────────────────────────
-- 9. USER_PERMISSION_OVERRIDES TABLE
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE user_permission_overrides (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    permission_id   BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    scope_id        BIGINT NOT NULL REFERENCES scopes(id) ON DELETE CASCADE,
    override_type   VARCHAR(10) NOT NULL,
    granted_by      BIGINT NOT NULL REFERENCES users(id),
    reason          TEXT,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, organization_id, permission_id, scope_id)
);

-- ──────────────────────────────────────────────────────────────────────────────
-- 10. PERMISSION_AUDIT_LOG TABLE
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE permission_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    resource_type   VARCHAR(20),
    resource_id     BIGINT,
    decision        VARCHAR(10) NOT NULL,
    deny_reason     VARCHAR(100),
    evaluated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pal_user ON permission_audit_log(user_id);
CREATE INDEX idx_pal_decision ON permission_audit_log(decision);
CREATE INDEX idx_pal_evaluated ON permission_audit_log(evaluated_at);

-- ──────────────────────────────────────────────────────────────────────────────
-- 11. SEED DEFAULT ROLE PERMISSION SCOPES FOR NEW PERMISSIONS
--     (Only for org-scoped builtin roles: ADMIN, DIRECTOR, MANAGER, EMPLOYEE)
--     Uses a helper CTE to avoid repetition.
-- ──────────────────────────────────────────────────────────────────────────────

-- ADMIN gets ALL 83 permissions at ORGANIZATION scope
INSERT INTO role_permission_scopes (role_id, permission_id, scope_id)
SELECT r.id, p.id, (SELECT id FROM scopes WHERE code = 'ORGANIZATION')
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND r.organization_id IS NULL
ON CONFLICT DO NOTHING;

-- DIRECTOR gets all except ORG_ARCHIVE, ORG_RESTORE, ORG_TRANSFER_OWNERSHIP, ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE
INSERT INTO role_permission_scopes (role_id, permission_id, scope_id)
SELECT r.id, p.id, (SELECT id FROM scopes WHERE code = 'ORGANIZATION')
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'DIRECTOR' AND r.organization_id IS NULL
  AND p.code NOT IN ('ORG_ARCHIVE', 'ORG_RESTORE', 'ORG_TRANSFER_OWNERSHIP',
                      'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE')
ON CONFLICT DO NOTHING;

-- MANAGER gets operational permissions at ORGANIZATION scope
INSERT INTO role_permission_scopes (role_id, permission_id, scope_id)
SELECT r.id, p.id, (SELECT id FROM scopes WHERE code = 'ORGANIZATION')
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER' AND r.organization_id IS NULL
  AND p.code IN (
    -- Organization
    'ORG_VIEW',
    -- Members
    'MEMBER_VIEW', 'MEMBER_INVITE',
    -- Teams
    'TEAM_VIEW', 'TEAM_CREATE', 'TEAM_UPDATE', 'TEAM_ARCHIVE',
    'TEAM_MEMBER_ADD', 'TEAM_MEMBER_REMOVE', 'TEAM_MEMBER_ROLE_UPDATE',
    -- Projects
    'PROJECT_VIEW', 'PROJECT_CREATE', 'PROJECT_UPDATE', 'PROJECT_ARCHIVE', 'PROJECT_RESTORE',
    'PROJECT_MEMBER_ADD', 'PROJECT_MEMBER_REMOVE', 'PROJECT_MEMBER_ROLE_UPDATE', 'PROJECT_EXPORT',
    -- Tasks
    'TASK_VIEW', 'TASK_CREATE', 'TASK_UPDATE', 'TASK_DELETE',
    'TASK_ARCHIVE', 'TASK_RESTORE', 'TASK_ASSIGN', 'TASK_REASSIGN',
    'TASK_START', 'TASK_SUBMIT', 'TASK_APPROVE', 'TASK_REJECT',
    'TASK_REOPEN', 'TASK_CANCEL', 'TASK_DEPENDENCY_UPDATE', 'TASK_COMMENT_CREATE',
    -- Goals
    'GOAL_VIEW', 'GOAL_CREATE', 'GOAL_UPDATE', 'GOAL_ARCHIVE', 'GOAL_ASSIGN', 'GOAL_PROGRESS_UPDATE',
    -- Announcements
    'ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_CREATE', 'ANNOUNCEMENT_UPDATE', 'ANNOUNCEMENT_PIN',
    -- Leave
    'LEAVE_VIEW', 'LEAVE_CREATE', 'LEAVE_UPDATE', 'LEAVE_DELETE',
    'LEAVE_APPROVE', 'LEAVE_REJECT',
    -- Calendar
    'CALENDAR_VIEW', 'CALENDAR_CREATE', 'CALENDAR_UPDATE', 'CALENDAR_DELETE', 'CALENDAR_EXPORT',
    -- Dashboard
    'DASHBOARD_VIEW', 'DASHBOARD_EXPORT', 'DASHBOARD_WIDGET_UPDATE',
    -- Activity
    'ACTIVITY_VIEW',
    -- Roles
    'ROLE_VIEW'
  )
ON CONFLICT DO NOTHING;

-- EMPLOYEE gets basic contributor permissions
-- Most at ORGANIZATION scope for viewing, OWN scope for editing
INSERT INTO role_permission_scopes (role_id, permission_id, scope_id)
SELECT r.id, p.id, (SELECT id FROM scopes WHERE code = 'ORGANIZATION')
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE' AND r.organization_id IS NULL
  AND p.code IN (
    'ORG_VIEW', 'MEMBER_VIEW', 'TEAM_VIEW', 'PROJECT_VIEW',
    'TASK_VIEW', 'TASK_CREATE', 'TASK_START', 'TASK_SUBMIT', 'TASK_COMMENT_CREATE',
    'GOAL_VIEW', 'GOAL_PROGRESS_UPDATE',
    'ANNOUNCEMENT_VIEW',
    'LEAVE_VIEW', 'LEAVE_CREATE',
    'CALENDAR_VIEW', 'CALENDAR_CREATE', 'CALENDAR_EXPORT',
    'DASHBOARD_VIEW', 'ACTIVITY_VIEW'
  )
ON CONFLICT DO NOTHING;

-- EMPLOYEE: OWN scope for update permissions
INSERT INTO role_permission_scopes (role_id, permission_id, scope_id)
SELECT r.id, p.id, (SELECT id FROM scopes WHERE code = 'OWN')
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE' AND r.organization_id IS NULL
  AND p.code IN (
    'TASK_UPDATE', 'LEAVE_UPDATE', 'LEAVE_DELETE',
    'CALENDAR_UPDATE', 'CALENDAR_DELETE'
  )
ON CONFLICT DO NOTHING;
