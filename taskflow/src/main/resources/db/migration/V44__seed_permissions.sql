-- Insert all standard permissions into the permissions table if they don't exist.
-- This replaces the hardcoded seed logic in PermissionService.java and OrganizationService.java.

INSERT INTO permissions (name, description)
VALUES 
    ('TASK_VIEW', 'Can view tasks'),
    ('TASK_ASSIGN', 'Can assign tasks'),
    ('TASK_EDIT', 'Can edit tasks'),
    ('TASK_DELETE', 'Can delete tasks'),
    ('TASK_REVIEW', 'Can review tasks'),
    ('TASK_DEPENDENCY_EDIT', 'Can edit task dependencies'),
    ('TASK_REASSIGN', 'Can reassign tasks'),
    ('TASK_ARCHIVE', 'Can archive tasks'),
    ('TASK_OVERRIDE', 'Can override task restrictions'),
    ('ROLE_MANAGE', 'Can manage roles'),
    ('ORG_MEMBER_INVITE', 'Can invite members to organization'),
    ('ORG_MEMBER_REMOVE', 'Can remove members from organization'),
    ('LEAVE_REQUEST_MANAGE', 'Can manage leave requests'),
    ('TEAM_CREATE', 'Can create teams'),
    ('TEAM_MANAGE', 'Can manage teams'),
    ('PROJECT_CREATE', 'Can create projects'),
    ('PROJECT_MANAGE', 'Can manage projects'),
    ('ANNOUNCEMENT_MANAGE', 'Can manage announcements'),
    ('GOAL_MANAGE', 'Can manage goals')
ON CONFLICT (name) DO NOTHING;
