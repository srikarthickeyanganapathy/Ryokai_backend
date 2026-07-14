-- V36: Add joined_at column to team_members
ALTER TABLE team_members ADD COLUMN joined_at TIMESTAMPTZ DEFAULT NOW();
