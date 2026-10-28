package cn.campus.resource.platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class MaintenanceStaffController {
    record Eligibility(long id, boolean eligible) { }
    record Staff(long id, String displayName) { }
    private final MaintenanceStaffMapper staff;
    private final AccessPolicy access;
    MaintenanceStaffController(MaintenanceStaffMapper staff, AccessPolicy access) {
        this.staff = staff;
        this.access = access;
    }

    @GetMapping("/api/v1/maintenance-staff")
    ApiEnvelope<List<Staff>> list(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        Actor actor = access.actor();
        access.require("maintenance:assign");
        if (!actor.roles().contains("PROPERTY_MANAGER")) {
            throw new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅物业主管可读取维修人员列表");
        }
        return ApiEnvelope.ok(staff.listEligible(limit, offset));
    }

    @GetMapping("/internal/v1/maintenance-staff/{id}")
    ApiEnvelope<Eligibility> eligibility(@PathVariable @Positive long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !"maintenance-service".equals(authentication.getPrincipal())) {
            throw new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权查询维修人员资格");
        }
        return ApiEnvelope.ok(new Eligibility(id, staff.eligible(id)));
    }
}

@Mapper
interface MaintenanceStaffMapper {
    @Select("SELECT u.id, u.display_name AS \"displayName\" FROM platform.\"user\" u "
            + "JOIN platform.user_role ur ON ur.user_id=u.id JOIN platform.role r ON r.id=ur.role_id "
            + "WHERE u.enabled AND r.code='PROPERTY_STAFF' ORDER BY u.id LIMIT #{limit} OFFSET #{offset}")
    List<MaintenanceStaffController.Staff> listEligible(@Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT EXISTS(SELECT 1 FROM platform.\"user\" u JOIN platform.user_role ur ON ur.user_id=u.id "
            + "JOIN platform.role r ON r.id=ur.role_id WHERE u.id=#{id} AND u.enabled AND r.code='PROPERTY_STAFF')")
    boolean eligible(long id);
}
