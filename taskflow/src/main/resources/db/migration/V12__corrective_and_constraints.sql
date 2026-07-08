-- V12__corrective_and_constraints.sql

-- MIG-002: Add missing description TEXT to roles table idempotently
ALTER TABLE roles ADD COLUMN IF NOT EXISTS description TEXT;

-- MIG-003: Add UNIQUE constraint (task_id, blocks_task_id) to task_dependencies
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_task_dependencies_task_blocks'
    ) THEN
        ALTER TABLE task_dependencies
        ADD CONSTRAINT uq_task_dependencies_task_blocks UNIQUE (task_id, blocks_task_id);
    END IF;
END $$;
