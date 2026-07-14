-- V34: Add comment threading support and align column names

-- Add parent_id for threaded comments
ALTER TABLE task_comments ADD COLUMN parent_id BIGINT REFERENCES task_comments(id);

-- Add updated_at for edit tracking
ALTER TABLE task_comments ADD COLUMN updated_at TIMESTAMPTZ;

-- Rename user_id → author_id to align with class diagram
ALTER TABLE task_comments RENAME COLUMN user_id TO author_id;

-- Index for parent lookups
CREATE INDEX idx_task_comments_parent_id ON task_comments(parent_id);
