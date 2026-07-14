-- V35: Refactor task_dependencies to composite PK
-- Rename blocks_task_id → depends_on_id, drop surrogate id, add created_by/created_at

-- 1. Drop the unique constraint (will be replaced by composite PK)
ALTER TABLE task_dependencies DROP CONSTRAINT IF EXISTS uq_task_dependencies_task_blocks;

-- 2. Drop the index on blocks_task_id
DROP INDEX IF EXISTS idx_task_dependencies_blocks_task_id;

-- 3. Rename column
ALTER TABLE task_dependencies RENAME COLUMN blocks_task_id TO depends_on_id;

-- 4. Add new columns
ALTER TABLE task_dependencies ADD COLUMN created_by BIGINT REFERENCES users(id);
ALTER TABLE task_dependencies ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW();

-- 5. Drop the surrogate primary key
ALTER TABLE task_dependencies DROP CONSTRAINT IF EXISTS task_dependencies_pkey;
ALTER TABLE task_dependencies DROP COLUMN IF EXISTS id;

-- 6. Set composite primary key
ALTER TABLE task_dependencies ADD PRIMARY KEY (task_id, depends_on_id);

-- 7. Re-create index on depends_on_id
CREATE INDEX idx_task_dependencies_depends_on_id ON task_dependencies(depends_on_id);
