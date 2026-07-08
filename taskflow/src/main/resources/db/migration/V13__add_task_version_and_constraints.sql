-- V13__add_task_version_and_constraints.sql

-- ENT-001: Add optimistic locking version column
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Add updated_at for auditing
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
UPDATE tasks SET updated_at = created_at WHERE updated_at IS NULL;

-- ENT-002, ENT-003: Add check constraints to enforce enum values at the DB level
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_task_status'
    ) THEN
        ALTER TABLE tasks ADD CONSTRAINT chk_task_status CHECK (current_status IN ('ASSIGNED', 'SUBMITTED', 'APPROVED', 'REJECTED'));
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_task_priority'
    ) THEN
        ALTER TABLE tasks ADD CONSTRAINT chk_task_priority CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW', 'NONE'));
    END IF;
END $$;
