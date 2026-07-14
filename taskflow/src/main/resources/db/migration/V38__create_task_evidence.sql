-- V38: Create task_evidence table per ER diagram

CREATE TABLE task_evidence (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL,
    added_by      BIGINT NOT NULL REFERENCES users(id),
    title         VARCHAR(255),

    -- LINK / GITHUB / RECORDING
    url           TEXT,
    unfurl_json   JSONB,

    -- GITHUB-specific
    gh_repo       VARCHAR(255),
    gh_pr_no      INT,
    gh_commit     VARCHAR(64),
    gh_state      VARCHAR(20),

    -- SCREENSHOT-specific
    image_key     VARCHAR(500),
    image_w       INT,
    image_h       INT,

    -- RECORDING-specific
    video_url     TEXT,
    duration_s    INT,

    -- SNIPPET-specific
    code_lang     VARCHAR(50),
    code_body     TEXT,

    -- NOTE-specific
    note_md       TEXT,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_evidence_task_id ON task_evidence(task_id);
CREATE INDEX idx_task_evidence_added_by ON task_evidence(added_by);
