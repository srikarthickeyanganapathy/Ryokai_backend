-- ============================================================================
-- V55: Separate Organization Membership Lifecycle (Exit Requests)
--      and Workforce Management (Leave Requests)
-- ============================================================================

-- 1. Create exit_requests table for organization membership termination
CREATE TABLE IF NOT EXISTS exit_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision_comment TEXT,
    reviewed_by_id BIGINT REFERENCES users(id),
    requested_at TIMESTAMP DEFAULT NOW(),
    reviewed_at TIMESTAMP,
    effective_exit_date DATE
);

-- 2. Migrate existing records from legacy leave_requests (which were actually exit requests)
INSERT INTO exit_requests (id, user_id, organization_id, reason, status, decision_comment, reviewed_by_id, requested_at, reviewed_at)
SELECT id, user_id, organization_id, reason, status, admin_comment, reviewed_by_id, created_at, reviewed_at
FROM leave_requests
ON CONFLICT (id) DO NOTHING;

-- Synchronize sequence after data migration
SELECT setval('exit_requests_id_seq', COALESCE((SELECT MAX(id) + 1 FROM exit_requests), 1), false);

-- 3. Create new dedicated workforce leave requests table (employee_leave_requests)
--    The legacy leave_requests table remains in DB as deprecated for rollback safety and audit history
CREATE TABLE IF NOT EXISTS employee_leave_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    leave_type VARCHAR(30) NOT NULL,
    reason TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    working_days INT NOT NULL DEFAULT 0,
    calendar_days INT NOT NULL DEFAULT 0,
    is_half_day BOOLEAN NOT NULL DEFAULT FALSE,
    is_emergency BOOLEAN NOT NULL DEFAULT FALSE,
    attachment_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_comment TEXT,
    reviewed_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    reviewed_at TIMESTAMP
);

-- 4. Insert granular permissions for Exit Requests
INSERT INTO permissions (code, name, description, module, category, is_system, created_at)
VALUES
    ('EXIT_REQUEST_CREATE', 'EXIT_REQUEST_CREATE', 'Submit an organization exit request', 'MEMBER', 'WORKFLOW', TRUE, CURRENT_TIMESTAMP),
    ('EXIT_REQUEST_VIEW', 'EXIT_REQUEST_VIEW', 'View organization exit requests', 'MEMBER', 'WORKFLOW', TRUE, CURRENT_TIMESTAMP),
    ('EXIT_REQUEST_APPROVE', 'EXIT_REQUEST_APPROVE', 'Approve organization exit requests', 'MEMBER', 'WORKFLOW', TRUE, CURRENT_TIMESTAMP),
    ('EXIT_REQUEST_REJECT', 'EXIT_REQUEST_REJECT', 'Reject organization exit requests', 'MEMBER', 'WORKFLOW', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;
