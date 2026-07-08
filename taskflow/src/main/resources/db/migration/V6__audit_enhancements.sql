-- Add new columns
ALTER TABLE task_status_history ADD COLUMN from_status VARCHAR(30);
ALTER TABLE task_status_history ADD COLUMN to_status VARCHAR(30);
ALTER TABLE task_status_history ADD COLUMN reason TEXT;
ALTER TABLE task_status_history ADD COLUMN event_type VARCHAR(30) NOT NULL DEFAULT 'STATUS_CHANGE';
ALTER TABLE task_status_history ADD COLUMN task_title_snapshot VARCHAR(255);
ALTER TABLE task_status_history ADD COLUMN actor_username_snapshot VARCHAR(50);

ALTER TABLE tasks ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE;


-- Backfill data
-- Note: from_status cannot be backfilled from the old schema.
-- All pre-migration rows will have from_status = NULL.
-- Only post-migration rows will have accurate from_status.

-- Backfill to_status and event_type
UPDATE task_status_history SET event_type = 'STATUS_CHANGE', to_status = status WHERE to_status IS NULL;

-- Backfill snapshots
UPDATE task_status_history SET task_title_snapshot = (SELECT title FROM tasks WHERE id = task_id) WHERE task_title_snapshot IS NULL;
UPDATE task_status_history SET actor_username_snapshot = (SELECT username FROM users WHERE id = changed_by) WHERE actor_username_snapshot IS NULL;

-- Create composite indexes for fast querying in activity feeds
CREATE INDEX idx_history_task_time ON task_status_history(task_id, changed_at DESC);
CREATE INDEX idx_history_actor_time ON task_status_history(changed_by, changed_at DESC);
CREATE INDEX idx_history_event_time ON task_status_history(event_type, changed_at DESC);
