DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id FROM permissions WHERE name = 'TEMPLATE_MANAGE'
);

DELETE FROM permissions WHERE name = 'TEMPLATE_MANAGE';
