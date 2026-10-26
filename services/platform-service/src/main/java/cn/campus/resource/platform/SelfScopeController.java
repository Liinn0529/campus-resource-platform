package cn.campus.resource.platform;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SelfScopeController {
    private final ManagementService management;

    SelfScopeController(ManagementService management) { this.management = management; }

    @GetMapping("/api/v1/me/room-scopes")
    ApiEnvelope<List<Long>> ownRoomScopes() {
        return ApiEnvelope.ok(management.ownRoomScopes());
    }
}
