package cn.campus.resource.platform;

import jakarta.validation.constraints.Positive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class MaintenanceStaffController {
    record Eligibility(long id, boolean eligible) { }
    private final MaintenanceStaffMapper staff;
    MaintenanceStaffController(MaintenanceStaffMapper staff) { this.staff = staff; }

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
    @Select("SELECT EXISTS(SELECT 1 FROM platform.\"user\" u JOIN platform.user_role ur ON ur.user_id=u.id "
            + "JOIN platform.role r ON r.id=ur.role_id WHERE u.id=#{id} AND u.enabled AND r.code='PROPERTY_STAFF')")
    boolean eligible(long id);
}
