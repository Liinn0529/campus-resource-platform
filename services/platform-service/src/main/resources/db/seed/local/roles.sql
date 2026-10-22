INSERT INTO platform.role (code, name)
VALUES
    ('STUDENT', '学生'),
    ('ROOM_ADMIN', '房间管理员'),
    ('DEPARTMENT_ADMIN', '部门管理员'),
    ('PROPERTY_STAFF', '物业工作人员'),
    ('PROPERTY_MANAGER', '物业主管'),
    ('OPERATOR', '运营人员'),
    ('SYSTEM_ADMIN', '系统管理员')
ON CONFLICT (code) DO NOTHING;
