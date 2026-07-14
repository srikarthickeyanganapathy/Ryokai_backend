-- V32: Drop the 'category' column from roles table
-- Role hierarchy is now determined by the role name (ADMIN, DIRECTOR, MANAGER, EMPLOYEE)
-- using helper methods on the Role entity instead of a separate enum column.
ALTER TABLE roles DROP COLUMN IF EXISTS category;
