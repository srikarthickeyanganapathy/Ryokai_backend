-- BIZ-015: Audit snapshots
ALTER TABLE task_status_history ADD COLUMN assignee_username_snapshot VARCHAR(50);
ALTER TABLE task_status_history ADD COLUMN creator_username_snapshot VARCHAR(50);

-- Backfill legacy TaskTemplate priority
UPDATE task_templates SET default_priority = 'NORMAL' WHERE default_priority = 'MEDIUM';
