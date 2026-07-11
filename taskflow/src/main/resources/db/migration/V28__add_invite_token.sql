ALTER TABLE organization_invites ADD COLUMN token VARCHAR(64) UNIQUE;
ALTER TABLE organization_invites ALTER COLUMN invitee_user_id DROP NOT NULL;
