-- V48: Add performance indexes for high-frequency Task, TaskActivityLog, OrganizationMembership, and Notification query paths.

-- 1. Tasks Table composite and targeted indexes
CREATE INDEX IF NOT EXISTS idx_task_org_status ON tasks(org_id, current_status);
CREATE INDEX IF NOT EXISTS idx_task_assignee_status ON tasks(assigned_to, current_status);
CREATE INDEX IF NOT EXISTS idx_task_team ON tasks(team_id);
CREATE INDEX IF NOT EXISTS idx_task_crew ON tasks(crew_id);
CREATE INDEX IF NOT EXISTS idx_task_due_date ON tasks(due_date);

-- 2. Task Activity Logs indexes
CREATE INDEX IF NOT EXISTS idx_actlog_task_created ON task_activity_logs(task_id, created_at);
CREATE INDEX IF NOT EXISTS idx_actlog_actor_created ON task_activity_logs(actor_id, created_at);

-- 3. Organization Memberships composite index
CREATE INDEX IF NOT EXISTS idx_orgmem_org_user ON organization_memberships(organization_id, user_id);

-- 4. Notifications targeted index
CREATE INDEX IF NOT EXISTS idx_notif_user_read ON notifications(user_id, read);

