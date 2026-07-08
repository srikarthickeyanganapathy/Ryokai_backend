-- V22__create_org_invites.sql
CREATE TABLE IF NOT EXISTS organization_invites (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invited_by_id BIGINT NOT NULL REFERENCES users(id),
    invitee_user_id BIGINT REFERENCES users(id),
    invitee_email VARCHAR(255),
    org_role VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_invitee CHECK (
        (invitee_user_id IS NOT NULL) OR (invitee_email IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_org_invites_org ON organization_invites(organization_id);
CREATE INDEX IF NOT EXISTS idx_org_invites_invitee ON organization_invites(invitee_user_id);
CREATE INDEX IF NOT EXISTS idx_org_invites_status ON organization_invites(status);
