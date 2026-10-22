INSERT INTO platform.user_role (user_id, role_id)
SELECT demo_user.id, demo_role.id
FROM (VALUES
    ('demo_admin', 'SYSTEM_ADMIN'),
    ('demo_student_01', 'STUDENT'),
    ('demo_student_02', 'STUDENT'),
    ('demo_room_admin', 'ROOM_ADMIN'),
    ('demo_property_staff', 'PROPERTY_STAFF')
) AS seed(username, role_code)
JOIN platform."user" AS demo_user ON demo_user.username = seed.username
JOIN platform.role AS demo_role ON demo_role.code = seed.role_code
ON CONFLICT (user_id, role_id) DO NOTHING;
