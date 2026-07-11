ALTER TABLE roles ADD COLUMN organization_id BIGINT;
ALTER TABLE roles ADD CONSTRAINT fk_role_organization FOREIGN KEY (organization_id) REFERENCES organizations(id);
