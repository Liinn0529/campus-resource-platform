INSERT INTO platform."user" (username, password_hash, display_name)
VALUES
    ('demo_admin', :adminHash, '演示系统管理员'),
    ('demo_student_01', :student1Hash, '演示学生 01'),
    ('demo_student_02', :student2Hash, '演示学生 02'),
    ('demo_room_admin', :roomAdminHash, '演示房间管理员'),
    ('demo_property_staff', :propertyStaffHash, '演示物业人员')
ON CONFLICT (username) DO NOTHING;
