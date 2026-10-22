INSERT INTO platform.room (building_id, code, name, type)
SELECT demo_building.id, seed.code, seed.name, seed.type
FROM (VALUES
    ('DEMO-A', 'A101', '演示自习室 A101', 'STUDY_ROOM'),
    ('DEMO-A', 'A201', '演示教室 A201', 'CLASSROOM'),
    ('DEMO-B', 'B101', '演示自习室 B101', 'STUDY_ROOM'),
    ('DEMO-B', 'B201', '演示教室 B201', 'CLASSROOM')
) AS seed(building_code, code, name, type)
JOIN platform.building AS demo_building ON demo_building.code = seed.building_code
ON CONFLICT (building_id, code) DO NOTHING;
