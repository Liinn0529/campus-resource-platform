package cn.campus.resource.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class MaintenanceWorkflowTest extends MaintenanceApiTestSupport {
    @Autowired MaintenanceService service;
    protected String student,other,staff,manager,admin;
    protected long room,staffId;
    protected static final String PATH="/api/v1/maintenance-tickets";

    @BeforeEach
    void prepare() {
        clearMaintenanceRows(); student=login("demo_student_01"); other=login("demo_student_02");
        staff=login("demo_property_staff"); manager=login(MANAGER_USERNAME); admin=login("demo_admin");
        staffId=platformUserId("demo_property_staff");
        room=platformJdbc.queryForObject("SELECT id FROM platform.room WHERE enabled ORDER BY id LIMIT 1",Long.class);
    }
    @AfterEach void cleanup() { clearMaintenanceRows(); }
    protected long report() {
        var result=request(HttpMethod.POST,PATH,Map.of("roomId",room,"description","Isolated repair description"),student);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number)data(result).get("id")).longValue();
    }
    protected void assign(long id) { assertThat(request(HttpMethod.POST,PATH+"/"+id+"/assign",Map.of("assigneeId",staffId),manager).getStatusCode()).isEqualTo(HttpStatus.OK); }
    protected String state(long id) { return jdbc.queryForObject("SELECT status FROM maintenance.repair_ticket WHERE id=?",String.class,id); }

    @Test void reportPersistsOwnerEventAndRejectsUnrelatedReadersOrAdminOverride() {
        long id=report(); assertThat(state(id)).isEqualTo("REPORTED");
        assertThat(request(HttpMethod.GET,PATH+"/"+id,null,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET,PATH+"/"+id,null,admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET,PATH+"/"+id,null,staff).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET,PATH+"/"+id,null,manager).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rows(request(HttpMethod.GET,PATH,null,other))).isEmpty();
        assertThat(rows(request(HttpMethod.GET,PATH+"?view=managed",null,manager))).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance.ticket_event WHERE ticket_id=? AND action='REPORTED'",Long.class,id)).isEqualTo(1);
    }
    @Test void reportValidatesIdentityPermissionsRoomAndUnknownFields() {
        var input=Map.of("roomId",room,"description","Fault");
        assertThat(request(HttpMethod.POST,PATH,input,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(HttpMethod.POST,PATH,input,admin).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.POST,PATH,Map.of("roomId",room,"description"," "),student).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.POST,PATH,Map.of("roomId",room,"description","Fault","reporterId",1),student).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.POST,PATH,Map.of("roomId",Long.MAX_VALUE,"description","Fault"),student).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET,PATH+"?view=managed",null,admin).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void assignmentRequiresActualManagerAndCurrentEligibleStaff() {
        long id=report();
        for(String token:List.of(student,staff,admin)) {
            assertThat(request(HttpMethod.POST,PATH+"/"+id+"/assign",Map.of("assigneeId",staffId),token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/assign",Map.of("assigneeId",platformUserId("demo_student_02")),manager).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assign(id); assertThat(state(id)).isEqualTo("ASSIGNED");
        assertThat(rows(request(HttpMethod.GET,PATH+"?view=assigned",null,staff))).hasSize(1);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/assign",Map.of("assigneeId",staffId),manager).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void realM1RefusalPersistsFailureWithoutFakeMaintenanceOrOccupancy() {
        long id=report(); assign(id);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/start",null,student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/start",null,staff).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(state(id)).isEqualTo("BLOCKING_ROOM"); service.syncDue();
        assertThat(state(id)).isEqualTo("SYNC_FAILED");
        var row=jdbc.queryForMap("SELECT sync_attempts,sync_error,occupancy_id FROM maintenance.repair_ticket WHERE id=?",id);
        assertThat(((Number)row.get("sync_attempts")).intValue()).isEqualTo(1);
        assertThat(row.get("sync_error")).isEqualTo("M1_HTTP_403"); assertThat(row.get("occupancy_id")).isNull();
        assertThat(reservationJdbc().queryForObject("SELECT count(*) FROM reservation.seat_occupancy",Long.class)).isZero();
        service.syncDue(); assertThat(request(HttpMethod.POST,PATH+"/"+id+"/cancel",null,student).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void onlyReporterMayCancelBeforeBlockingAndEventsAreRetained() {
        long id=report(); assign(id);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/cancel",null,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/cancel",null,student).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(state(id)).isEqualTo("CANCELLED");
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/start",null,staff).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance.ticket_event WHERE ticket_id=?",Long.class,id)).isEqualTo(3);
    }
    @Test void finishAcceptRejectAndReviewCannotSkipWorkflow() {
        long id=report(); assign(id);
        for(String action:List.of("finish","accept","reject-acceptance")) {
            String actor=action.equals("finish")?staff:student;
            assertThat(request(HttpMethod.POST,PATH+"/"+id+"/"+action,Map.of("note","Decision"),actor).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/review",Map.of("rating",5),student).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/review",Map.of("rating",6),student).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void currentRoleRevocationImmediatelyStopsExistingStaffJwt() {
        long id=report(); assign(id);
        try {
            assertThat(platformRequest(HttpMethod.PUT,"/api/v1/users/"+staffId+"/roles",Map.of("roleCodes",List.of("STUDENT")),admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.POST,PATH+"/"+id+"/start",null,staff).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(HttpMethod.GET,PATH+"/"+id,null,staff).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally { assertThat(platformRequest(HttpMethod.PUT,"/api/v1/users/"+staffId+"/roles",Map.of("roleCodes",List.of("PROPERTY_STAFF")),admin).getStatusCode()).isEqualTo(HttpStatus.OK); }
    }
    @Test void escalationIsInclusivePersistedAndExactlyOncePerCycle() {
        long id=report(); assign(id); testClock.set(INITIAL_TIME.plusSeconds(86400-1)); service.scanEscalations();
        assertThat(jdbc.queryForObject("SELECT escalated FROM maintenance.repair_ticket WHERE id=?",Boolean.class,id)).isFalse();
        testClock.set(INITIAL_TIME.plusSeconds(86400)); service.scanEscalations(); service.scanEscalations();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance.ticket_event WHERE ticket_id=? AND action='ESCALATED' AND actor_user_id IS NULL",Long.class,id)).isEqualTo(1);
        var detail=data(request(HttpMethod.GET,PATH+"/"+id,null,student));
        assertThat(((Map<?,?>)detail.get("ticket")).get("escalated")).isEqualTo(true);
    }
    @Test void cancelledAndUnassignedTicketsNeverEscalate() {
        long first=report(),second=report(); assign(first);
        request(HttpMethod.POST,PATH+"/"+first+"/cancel",null,student);
        testClock.set(INITIAL_TIME.plusSeconds(172800)); service.scanEscalations();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance.ticket_event WHERE action='ESCALATED'",Long.class)).isZero();
        assertThat(state(second)).isEqualTo("REPORTED");
    }
    @Test void concurrentStartAndCancelHaveOneConsistentWinner() throws Exception {
        long id=report(); assign(id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->request(HttpMethod.POST,PATH+"/"+id+"/start",null,staff).getStatusCode().value());
            var b=pool.submit(()->request(HttpMethod.POST,PATH+"/"+id+"/cancel",null,student).getStatusCode().value());
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance.ticket_event WHERE ticket_id=? AND action IN ('CANCELLED','START_REQUESTED')",Long.class,id)).isEqualTo(1);
    }
}
