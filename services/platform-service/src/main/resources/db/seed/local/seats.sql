INSERT INTO platform.seat (room_id, code, has_power_outlet)
SELECT demo_room.id, 'S' || LPAD(seat_number::TEXT, 2, '0'), seat_number <= 5
FROM (VALUES
    ('DEMO-A', 'A101'),
    ('DEMO-A', 'A201'),
    ('DEMO-B', 'B101'),
    ('DEMO-B', 'B201')
) AS seed(building_code, room_code)
JOIN platform.building AS demo_building ON demo_building.code = seed.building_code
JOIN platform.room AS demo_room
    ON demo_room.building_id = demo_building.id AND demo_room.code = seed.room_code
CROSS JOIN generate_series(1, 10) AS seat_number
ON CONFLICT (room_id, code) DO NOTHING;
