-- V56: Workspace-scoped Notes & Calendar Events
-- Notes and calendar events become per-workspace features:
--   personal (user_id only), organization (organization_id), crew (crew_id).
-- All scopes nullable so personal records stay untouched; exactly one workspace
-- dimension is set per row at the application layer.

-- ── Notes ─────────────────────────────────────────────────────────────
ALTER TABLE notes ADD COLUMN organization_id BIGINT NULL;
ALTER TABLE notes ADD COLUMN crew_id BIGINT NULL;

ALTER TABLE notes
    ADD CONSTRAINT fk_note_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id);

ALTER TABLE notes
    ADD CONSTRAINT fk_note_crew
    FOREIGN KEY (crew_id) REFERENCES crews (id);

CREATE INDEX idx_note_organization_updated
    ON notes (organization_id, updated_at DESC);
CREATE INDEX idx_note_crew_updated
    ON notes (crew_id, updated_at DESC);

-- Note tags (ElementCollection backing table)
CREATE TABLE note_tags (
    note_id BIGINT NOT NULL,
    tag     VARCHAR(50) NOT NULL,
    CONSTRAINT fk_note_tags_note
        FOREIGN KEY (note_id) REFERENCES notes (id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag)
);

CREATE INDEX idx_note_tags_tag ON note_tags (tag);

-- ── Calendar Events ───────────────────────────────────────────────────
ALTER TABLE calendar_events ADD COLUMN organization_id BIGINT NULL;
ALTER TABLE calendar_events ADD COLUMN crew_id BIGINT NULL;

ALTER TABLE calendar_events
    ADD CONSTRAINT fk_calendar_event_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id);

ALTER TABLE calendar_events
    ADD CONSTRAINT fk_calendar_event_crew
    FOREIGN KEY (crew_id) REFERENCES crews (id);

CREATE INDEX idx_calendar_event_organization_start
    ON calendar_events (organization_id, start_time);
CREATE INDEX idx_calendar_event_crew_start
    ON calendar_events (crew_id, start_time);
