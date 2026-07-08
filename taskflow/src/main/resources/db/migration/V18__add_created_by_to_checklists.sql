ALTER TABLE checklist_items ADD COLUMN created_by_id BIGINT;
ALTER TABLE checklist_items ADD CONSTRAINT fk_checklist_items_created_by FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE SET NULL;
