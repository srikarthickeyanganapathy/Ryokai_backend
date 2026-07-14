-- V33: Create Crew (Personal Mode) tables and add crew_id FK to tasks

-- Crews
CREATE TABLE crews (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    avatar_url  VARCHAR(500),
    creator_id  BIGINT NOT NULL REFERENCES users(id),
    visibility  VARCHAR(20) NOT NULL DEFAULT 'INVITE_ONLY',
    member_cap  INT NOT NULL DEFAULT 15,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

-- Crew Members (composite PK)
CREATE TABLE crew_members (
    crew_id   BIGINT NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (crew_id, user_id)
);

-- Crew Channels
CREATE TABLE crew_channels (
    id         BIGSERIAL PRIMARY KEY,
    crew_id    BIGINT NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    position   INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Crew Messages
CREATE TABLE crew_messages (
    id         BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES crew_channels(id) ON DELETE CASCADE,
    author_id  BIGINT NOT NULL REFERENCES users(id),
    content    TEXT NOT NULL,
    task_id    BIGINT REFERENCES tasks(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    edited_at  TIMESTAMPTZ
);

-- Crew Invites (UUID PK)
CREATE TABLE crew_invites (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    crew_id    BIGINT NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    invited_by BIGINT NOT NULL REFERENCES users(id),
    email      VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Crew ↔ Project link table (composite PK)
CREATE TABLE crew_projects (
    crew_id    BIGINT NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    added_by   BIGINT NOT NULL REFERENCES users(id),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (crew_id, project_id)
);

-- Add crew_id FK to tasks
ALTER TABLE tasks ADD COLUMN crew_id BIGINT REFERENCES crews(id);

-- Indexes
CREATE INDEX idx_crew_members_user_id ON crew_members(user_id);
CREATE INDEX idx_crew_channels_crew_id ON crew_channels(crew_id);
CREATE INDEX idx_crew_messages_channel_id ON crew_messages(channel_id);
CREATE INDEX idx_crew_messages_author_id ON crew_messages(author_id);
CREATE INDEX idx_crew_invites_crew_id ON crew_invites(crew_id);
CREATE INDEX idx_tasks_crew_id ON tasks(crew_id);
