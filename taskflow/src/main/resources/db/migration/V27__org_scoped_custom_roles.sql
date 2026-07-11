
ALTER TABLE roles ADD COLUMN is_builtin BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE roles ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'CUSTOM';

UPDATE roles SET organization_id = NULL, is_builtin = true, category = 'BUILTIN_ADMIN'    WHERE name = 'ADMIN';
UPDATE roles SET organization_id = NULL, is_builtin = true, category = 'BUILTIN_DIRECTOR' WHERE name = 'DIRECTOR';
UPDATE roles SET organization_id = NULL, is_builtin = true, category = 'BUILTIN_MANAGER'  WHERE name = 'MANAGER';
UPDATE roles SET organization_id = NULL, is_builtin = true, category = 'BUILTIN_EMPLOYEE' WHERE name = 'EMPLOYEE';
UPDATE roles SET organization_id = NULL, is_builtin = true, category = 'BUILTIN_ADMIN'    WHERE name = 'SUPER_ADMIN';

ALTER TABLE organization_memberships ADD COLUMN org_role_id BIGINT REFERENCES roles(id);

UPDATE organization_memberships om
SET org_role_id = (SELECT id FROM roles r WHERE r.name = om.org_role LIMIT 1);

ALTER TABLE organization_invites ADD COLUMN org_role_id BIGINT REFERENCES roles(id);

UPDATE organization_invites oi
SET org_role_id = (SELECT id FROM roles r WHERE r.name = oi.org_role LIMIT 1);

ALTER TABLE organization_memberships DROP COLUMN org_role;
ALTER TABLE organization_invites DROP COLUMN org_role;

CREATE INDEX idx_roles_organization ON roles(organization_id);
