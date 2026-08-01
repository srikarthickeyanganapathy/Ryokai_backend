-- Fix the IN_PROGRESS constraint bug (Bug #6)
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS chk_task_status;
ALTER TABLE tasks ADD CONSTRAINT chk_task_status CHECK (current_status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'ASSIGNED', 'SUBMITTED', 'APPROVED', 'REJECTED'));

-- Fix N9: Seed permission policies for NOT_SELF
-- The assignee cannot approve or reject their own tasks.
INSERT INTO permission_policies (permission_id, policy_key, policy_params, operator, evaluation_order, is_required)
SELECT id, 'NOT_SELF', '{}'::jsonb, 'AND', 1, TRUE 
FROM permissions 
WHERE code IN ('TASK_APPROVE', 'TASK_REJECT');
