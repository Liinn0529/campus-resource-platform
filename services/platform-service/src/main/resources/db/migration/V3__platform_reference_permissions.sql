-- These are application reference definitions, not local demonstration users or spaces.
INSERT INTO platform.role (code, name) VALUES
    ('STUDENT', '学生'), ('ROOM_ADMIN', '房间管理员'),
    ('DEPARTMENT_ADMIN', '部门管理员'), ('PROPERTY_STAFF', '物业人员'),
    ('PROPERTY_MANAGER', '物业主管'), ('OPERATOR', '运营人员'),
    ('SYSTEM_ADMIN', '系统管理员')
ON CONFLICT (code) DO NOTHING;

INSERT INTO platform.permission (code, name) VALUES
    ('reservation:create', '创建本人预约'), ('reservation:view:self', '查看本人预约'),
    ('classroom:apply', '申请教室'), ('classroom:approve', '审批教室'),
    ('maintenance:report', '提交报修'), ('maintenance:assign', '派发工单'),
    ('space:manage', '维护空间'), ('user:manage', '管理用户'), ('audit:view', '查看审计'),
    ('space:view', '查询空间'), ('settings:view', '查询系统参数'),
    ('settings:manage', '维护系统参数'), ('checkin-code:manage', '管理房间签到码'),
    ('role:manage', '管理角色权限');

INSERT INTO platform.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM platform.role r CROSS JOIN platform.permission p
WHERE r.code = 'SYSTEM_ADMIN';

INSERT INTO platform.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM platform.role r CROSS JOIN platform.permission p
WHERE (r.code = 'STUDENT' AND p.code IN ('reservation:create', 'reservation:view:self',
           'classroom:apply', 'maintenance:report', 'space:view', 'settings:view'))
   OR (r.code = 'ROOM_ADMIN' AND p.code IN ('classroom:approve', 'space:view',
           'space:manage', 'settings:view', 'checkin-code:manage'))
   OR (r.code = 'DEPARTMENT_ADMIN' AND p.code IN ('classroom:approve', 'space:view', 'settings:view'))
   OR (r.code = 'PROPERTY_STAFF' AND p.code IN ('space:view', 'settings:view'))
   OR (r.code = 'PROPERTY_MANAGER' AND p.code IN ('maintenance:assign', 'space:view', 'settings:view'))
   OR (r.code = 'OPERATOR' AND p.code IN ('space:view', 'settings:view'));

INSERT INTO platform.system_setting (key, value) VALUES ('max_reservation_hours', 4);
