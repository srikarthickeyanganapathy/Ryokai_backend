-- Migration to update legacy permission names to match their canonical codes

UPDATE permissions SET name = code;
