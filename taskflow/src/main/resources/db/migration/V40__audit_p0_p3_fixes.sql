-- ============================================================
-- V40__audit_p0_p3_fixes.sql
-- Addresses compliance audit findings P0–P3:
--   P0: Align tasks.priority CHECK with TaskPriority enum (MEDIUM not NORMAL)
--   P2: Mutual exclusivity CHECK for personal / crew / org task modes
--   P3: Drop dead attachments table (superseded by task_evidence in V38)
-- ============================================================

-- ------------------------------------------------------------
-- P0: chk_task_priority — Java enum is LOW/MEDIUM/HIGH/URGENT
-- V13 used NORMAL/NONE which rejected the default MEDIUM insert.
-- ------------------------------------------------------------
UPDATE tasks SET priority = 'MEDIUM' WHERE priority = 'NORMAL';
UPDATE tasks SET priority = 'LOW'    WHERE priority = 'NONE';

ALTER TABLE tasks DROP CONSTRAINT IF EXISTS chk_task_priority;
ALTER TABLE tasks ADD CONSTRAINT chk_task_priority
    CHECK (priority IS NULL OR priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));

-- ------------------------------------------------------------
-- P2: personal / crew / org mutual exclusivity
-- Spec flowchart:
--   personal: is_personal=true  → org_id NULL, team_id NULL, crew_id NULL
--   crew:     crew_id NOT NULL  → org_id NULL, team_id NULL, is_personal=false
--   org:      organization_id NOT NULL → crew_id NULL, is_personal=false
-- ------------------------------------------------------------
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS chk_task_mode_exclusivity;
ALTER TABLE tasks ADD CONSTRAINT chk_task_mode_exclusivity CHECK (
    -- Personal: pure personal boundary
    (is_personal = true
        AND organization_id IS NULL
        AND team_id IS NULL
        AND crew_id IS NULL)
    OR
    -- Crew: crew-scoped, no org/team/personal
    (is_personal = false
        AND crew_id IS NOT NULL
        AND organization_id IS NULL
        AND team_id IS NULL)
    OR
    -- Org: org-scoped (team optional), not personal, not crew
    (is_personal = false
        AND organization_id IS NOT NULL
        AND crew_id IS NULL)
    OR
    -- Legacy / unscoped non-personal (no org, no crew) — e.g. transitional rows
    -- Keep allowed so existing edge data does not break migration.
    (is_personal = false
        AND organization_id IS NULL
        AND crew_id IS NULL)
);

-- ------------------------------------------------------------
-- P3: drop dead attachments table (V8)
-- Superseded by task_evidence (V38). No JPA entity referenced it.
-- ------------------------------------------------------------
DROP TABLE IF EXISTS attachments;
