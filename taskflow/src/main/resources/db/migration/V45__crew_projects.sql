-- Add crew_id to projects to distinguish naturally owned crew projects from merely shared ones
ALTER TABLE projects ADD COLUMN crew_id BIGINT;
ALTER TABLE projects ADD CONSTRAINT fk_projects_crew FOREIGN KEY (crew_id) REFERENCES crews(id);

-- Create a junction table for explicit project collaborators (GitHub style)
CREATE TABLE project_collaborators (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pc_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_pc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Index for quick lookups of a user's collaborative projects
CREATE INDEX idx_project_collaborators_user ON project_collaborators(user_id);
