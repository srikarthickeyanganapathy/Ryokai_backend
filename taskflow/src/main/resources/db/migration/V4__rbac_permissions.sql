CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(60) UNIQUE NOT NULL,
    description TEXT
);

CREATE TABLE role_permissions (
    role_id BIGINT REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY(role_id, permission_id)
);

-- Insert all PermissionType values
INSERT INTO permissions (name, description) VALUES
    ('TASK_VIEW', 'View tasks'),
    ('TASK_CREATE', 'Create new tasks'),
    ('TASK_ASSIGN', 'Assign tasks to users'),
    ('TASK_EDIT', 'Edit task details'),
    ('TASK_DELETE', 'Delete tasks'),
    ('TASK_REVIEW', 'Review submitted tasks'),
    ('TASK_COMMENT', 'Add comments to tasks'),
    ('TASK_CHECKLIST_EDIT', 'Edit task checklists'),
    ('TASK_DEPENDENCY_EDIT', 'Edit task dependencies'),
    ('TASK_REASSIGN', 'Reassign tasks to different users'),
    ('TASK_ARCHIVE', 'Archive tasks'),
    ('USER_MANAGE', 'Manage user accounts'),
    ('ROLE_MANAGE', 'Manage roles and permissions'),
    ('TEMPLATE_MANAGE', 'Manage task templates');

-- Map permissions to EMPLOYEE
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'EMPLOYEE' AND p.name IN (
    'TASK_VIEW', 'TASK_COMMENT', 'TASK_CHECKLIST_EDIT'
);

-- Map permissions to MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MANAGER' AND p.name IN (
    'TASK_VIEW', 'TASK_CREATE', 'TASK_ASSIGN', 'TASK_EDIT', 'TASK_REVIEW', 
    'TASK_COMMENT', 'TASK_CHECKLIST_EDIT', 'TASK_DEPENDENCY_EDIT', 
    'TASK_REASSIGN', 'TEMPLATE_MANAGE'
);

-- Map permissions to DIRECTOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DIRECTOR' AND p.name IN (
    'TASK_VIEW', 'TASK_CREATE', 'TASK_ASSIGN', 'TASK_EDIT', 'TASK_DELETE',
    'TASK_REVIEW', 'TASK_COMMENT', 'TASK_CHECKLIST_EDIT', 'TASK_DEPENDENCY_EDIT',
    'TASK_REASSIGN', 'TASK_ARCHIVE', 'USER_MANAGE', 'TEMPLATE_MANAGE'
);

-- Map permissions to SUPER_ADMIN (All permissions)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN';

-- Update roles with description if they don't have it (fallback)
UPDATE roles SET description = 'Default employee role' WHERE name = 'EMPLOYEE';
UPDATE roles SET description = 'Manager role with team oversight' WHERE name = 'MANAGER';
UPDATE roles SET description = 'Director role with broad authority' WHERE name = 'DIRECTOR';
