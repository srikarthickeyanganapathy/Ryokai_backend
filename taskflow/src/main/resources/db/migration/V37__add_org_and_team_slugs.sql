-- V37: Add slug columns to organizations and teams
ALTER TABLE organizations ADD COLUMN slug VARCHAR(100) UNIQUE;
ALTER TABLE teams ADD COLUMN slug VARCHAR(100);
