-- V14__add_missing_indexes.sql

-- PERF-005: Add missing indexes on hot FK columns and WHERE/JOIN columns

-- tasks table
CREATE INDEX IF NOT EXISTS idx_tasks_assigned_to ON tasks(assigned_to);
CREATE INDEX IF NOT EXISTS idx_tasks_created_by ON tasks(created_by);
CREATE INDEX IF NOT EXISTS idx_tasks_reviewed_by ON tasks(reviewed_by);
CREATE INDEX IF NOT EXISTS idx_tasks_current_status ON tasks(current_status);
CREATE INDEX IF NOT EXISTS idx_tasks_is_archived ON tasks(is_archived);
CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(due_date);

-- task_comments table
CREATE INDEX IF NOT EXISTS idx_task_comments_task_id ON task_comments(task_id);

-- checklist_items table
CREATE INDEX IF NOT EXISTS idx_checklist_items_task_id ON checklist_items(task_id);

-- task_dependencies table
CREATE INDEX IF NOT EXISTS idx_task_dependencies_task_id ON task_dependencies(task_id);
CREATE INDEX IF NOT EXISTS idx_task_dependencies_blocks_task_id ON task_dependencies(blocks_task_id);

-- user_roles table
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
