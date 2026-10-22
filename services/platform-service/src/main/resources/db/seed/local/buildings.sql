INSERT INTO platform.building (code, name)
VALUES
    ('DEMO-A', '演示教学楼 A'),
    ('DEMO-B', '演示教学楼 B')
ON CONFLICT (code) DO NOTHING;
