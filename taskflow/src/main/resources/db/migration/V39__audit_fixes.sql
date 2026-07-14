-- ============================================================
-- V39__audit_fixes.sql
-- Addresses findings from the backend compliance audit:
--   RB-C02: relax roles.name UNIQUE -> (name, organization_id)
--   SEC-M01 / ER-M05: password_reset_tokens.token_hash UNIQUE
--   ER-M01: notifications.metadata jsonb (spec-required)
--   ER-M02: projects.color (spec-required)
--   ER-M03: checklist_items.completed_at (spec-required)
--   ER-M04: roles.created_at (spec-required)
--   ER-M06: relax task_status_history.status NOT NULL (legacy column)
--   SM-M01: partial CHECK on tasks.rejection_reason (NOT NULL when REJECTED)
-- ============================================================

-- ------------------------------------------------------------
-- RB-C02: roles.name UNIQUE -> composite (name, organization_id)
-- Allows per-org builtin roles (ADMIN/DIRECTOR/MANAGER/EMPLOYEE)
-- to coexist with the global builtin rows seeded by V19/V27.
-- ------------------------------------------------------------
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_key;
-- COALESCE trick: treats NULL organization_id as -1 ("global") so
-- the unique index works correctly with Postgres NULL semantics.
CREATE UNIQUE INDEX IF NOT EXISTS uq_roles_name_org
    ON roles (name, COALESCE(organization_id, -1));

-- ------------------------------------------------------------
-- ER-M04: roles.created_at (spec-required, never created)
-- ------------------------------------------------------------
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- ------------------------------------------------------------
-- SEC-M01 / ER-M05: password_reset_tokens.token_hash UNIQUE
-- Spec requires UK; V7 only created a non-unique index.
-- ------------------------------------------------------------
ALTER TABLE password_reset_tokens
    DROP CONSTRAINT IF EXISTS uk_password_reset_tokens_token_hash;
ALTER TABLE password_reset_tokens
    ADD CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash);

-- ------------------------------------------------------------
-- ER-M01: notifications.metadata jsonb (spec-required, never created)
-- ------------------------------------------------------------
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS metadata JSONB;

-- ------------------------------------------------------------
-- ER-M02: projects.color (spec-required, never created)
-- ------------------------------------------------------------
ALTER TABLE projects ADD COLUMN IF NOT EXISTS color VARCHAR(20);

-- ------------------------------------------------------------
-- ER-M03: checklist_items.completed_at (spec-required, never created)
-- ------------------------------------------------------------
ALTER TABLE checklist_items ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

-- ------------------------------------------------------------
-- ER-M06: relax task_status_history.status NOT NULL
-- Legacy column from V1; V6 introduced from_status/to_status but
-- never dropped `status`. New inserts that don't set it would fail.
-- ------------------------------------------------------------
ALTER TABLE task_status_history ALTER COLUMN status DROP NOT NULL;

-- ------------------------------------------------------------
-- SM-M01: partial CHECK on tasks.rejection_reason
-- Spec: "rejection_reason NOT NULL, enforced at DTO + DB level"
-- Enforced only when current_status = 'REJECTED' (other statuses
-- may have NULL rejection_reason — e.g. on submit we clear it).
-- ------------------------------------------------------------
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS chk_rejection_reason_when_rejected;
ALTER TABLE tasks ADD CONSTRAINT chk_rejection_reason_when_rejected
    CHECK (current_status <> 'REJECTED' OR rejection_reason IS NOT NULL);

-- ------------------------------------------------------------
-- Missing FK indexes (16 of them — Minor performance findings)
-- Adding the high-traffic ones here; full list in audit report.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_users_manager_id          ON users (manager_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_team_id            ON tasks (team_id);
CREATE INDEX IF NOT EXISTS idx_task_comments_author_id  ON task_comments (author_id);
CREATE INDEX IF NOT EXISTS idx_checklist_created_by_id  ON checklist_items (created_by_id);
CREATE INDEX IF NOT EXISTS idx_task_deps_created_by     ON task_dependencies (created_by);
CREATE INDEX IF NOT EXISTS idx_orgs_created_by_id       ON organizations (created_by_id);
CREATE INDEX IF NOT EXISTS idx_org_members_org_role_id  ON organization_memberships (org_role_id);
CREATE INDEX IF NOT EXISTS idx_org_invites_invited_by   ON organization_invites (invited_by_id);
CREATE INDEX IF NOT EXISTS idx_org_invites_org_role_id  ON organization_invites (org_role_id);
CREATE INDEX IF NOT EXISTS idx_leave_requests_reviewed_by ON leave_requests (reviewed_by_id);
CREATE INDEX IF NOT EXISTS idx_teams_created_by_id      ON teams (created_by_id);
CREATE INDEX IF NOT EXISTS idx_crews_creator_id         ON crews (creator_id);
CREATE INDEX IF NOT EXISTS idx_crew_messages_task_id    ON crew_messages (task_id);
CREATE INDEX IF NOT EXISTS idx_crew_invites_invited_by  ON crew_invites (invited_by);
CREATE INDEX IF NOT EXISTS idx_crew_projects_added_by  ON crew_projects (added_by);
