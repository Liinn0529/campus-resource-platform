package cn.campus.resource.maintenance;

import static org.assertj.core.api.Assertions.*;
import cn.campus.resource.testing.Member03Database;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

/** Actual PostgreSQL; the remote M1/D response fixture does not constitute real service integration. */
class Member03MaintenanceWorkflowTest {
    static Member03Database db;
    static final Instant NOW=Instant.parse("2026-11-01T00:00:00Z");
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    HttpServer fixture;MaintenanceService service;MaintenanceMapper mapper;TestClock clock;AuditOutbox outbox;
    int releaseStatus,auditStatus;boolean wrongId;Map<String,Object> receipt,lastAudit;
    @BeforeAll static void database() throws Exception{db=new Member03Database("maintenance");}
    @AfterAll static void stopDatabase() throws Exception{if(db!=null){db.close();}}
    @BeforeEach void setup() throws Exception{
        db.clear();clock=new TestClock();releaseStatus=200;auditStatus=201;wrongId=false;
        fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        fixture.createContext("/",e -> {
            String path=e.getRequestURI().getPath();Map<String,Object> data;int code=200;
            if(path.startsWith("/internal/v1/rooms/")){
                data=Map.of("id",9,"buildingId",1,"code","C9","name","Test classroom","type","CLASSROOM","enabled",true);
            }else if(path.startsWith("/internal/v1/maintenance-staff/")){
                data=Map.of("id",4,"eligible",true);
            }else if(path.equals("/internal/v1/audit-events")){
                lastAudit=json.readValue(e.getRequestBody(),Map.class);data=Map.of("id",1,"status","ACCEPTED");code=auditStatus;
            }else if(e.getRequestMethod().equals("POST")){
                receipt=new LinkedHashMap<>(json.readValue(e.getRequestBody(),Map.class));receipt.put("id",91);receipt.put("status","ACTIVE");data=receipt;code=201;
            }else{
                data=new LinkedHashMap<>(receipt);data.put("status","RELEASED");if(wrongId){data.put("id",92);}code=releaseStatus;
            }
            byte[] body=json.writeValueAsBytes(Map.of("data",data,"requestId",UUID.randomUUID().toString()));
            e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(code,body.length);e.getResponseBody().write(body);e.close();
        });fixture.start();String url="http://127.0.0.1:"+fixture.getAddress().getPort();
        mapper=db.mapper(MaintenanceMapper.class);
        var platform=new PlatformClient(RestClient.builder(),url,"test-only-member03-platform-token-at-least-32-bytes");
        var occupancy=new OccupancyClient(RestClient.builder(),url,"test-only-member03-m1-token-at-least-32-bytes","test-only-member03-platform-token-at-least-32-bytes");
        service=new MaintenanceService(mapper,platform,occupancy,new AccessPolicy(),clock,db.manager(),24);
        outbox=new AuditOutbox(db.mapper(AuditOutboxMapper.class),RestClient.builder(),clock,db.manager(),url,"test-only-member03-platform-token-at-least-32-bytes");
    }
    @AfterEach void stop(){fixture.stop(0);SecurityContextHolder.clearContext();}
    void actor(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new Actor(id,"test-user",Set.of(role),Set.of("maintenance:report","maintenance:assign")),null,List.of()));}
    long assigned(){actor(1,"STUDENT");long id=service.report(new MaintenanceController.Report(9L,"private description")).id();actor(3,"PROPERTY_MANAGER");service.assign(id,4);return id;}
    long active(){long id=assigned();actor(4,"PROPERTY_STAFF");service.start(id);assertThat(mapper.ticket(id).status()).isEqualTo("BLOCKING_ROOM");service.syncDue();assertThat(mapper.ticket(id).status()).isEqualTo("IN_PROGRESS");return id;}
    long waiting(){long id=active();service.finish(id,"private finish note");return id;}
    @Test void acceptanceNeedsAnExactReleaseAndOnlyTheReporterCanRequestIt(){
        long id=waiting();actor(3,"PROPERTY_MANAGER");assertThatThrownBy(() -> service.acceptance(id,true,"manager cannot accept")).isInstanceOf(ApiFailure.class);
        actor(1,"STUDENT");service.acceptance(id,true,"checked");assertThat(mapper.ticket(id).status()).isEqualTo("WAITING_ACCEPTANCE");assertThat(mapper.ticket(id).syncOperation()).isEqualTo("RELEASE");
        service.syncDue();assertThat(mapper.ticket(id).status()).isEqualTo("COMPLETED");assertThat(mapper.ticket(id).occupancyId()).isEqualTo(91);
    }
    @Test void rejectingAcceptanceKeepsTheSourceAndStartsANewRepairCycle(){
        long id=active();clock.now=NOW.plusSeconds(24*3600);service.scanEscalations();assertThat(mapper.ticket(id).escalated()).isTrue();
        actor(4,"PROPERTY_STAFF");service.finish(id,"needs owner check");actor(1,"STUDENT");service.acceptance(id,false,"not fixed");
        var row=mapper.ticket(id);assertThat(row.status()).isEqualTo("IN_PROGRESS");assertThat(row.occupancyId()).isEqualTo(91);assertThat(row.blockStartAt()).isEqualTo(NOW);
        assertThat(row.repairCycle()).isEqualTo(2);assertThat(row.escalated()).isFalse();assertThat(row.dueAt()).isEqualTo(clock.now.plusSeconds(24*3600));assertThat(row.syncOperation()).isNull();
    }
    @Test void a404OrWrongOccupancyCannotPretendTheRoomWasRestored(){
        long id=waiting();actor(1,"STUDENT");service.acceptance(id,true,"checked");releaseStatus=404;service.syncDue();
        assertThat(mapper.ticket(id).status()).isEqualTo("SYNC_FAILED");assertThat(mapper.ticket(id).occupancyId()).isEqualTo(91);assertThat(mapper.ticket(id).syncOperation()).isEqualTo("RELEASE");
        actor(3,"PROPERTY_MANAGER");releaseStatus=200;wrongId=true;service.recover(id,"verify exact original occupancy");assertThat(mapper.ticket(id).status()).isEqualTo("SYNC_FAILED");
        assertThat(mapper.ticket(id).syncError()).isEqualTo("M1_INVALID_RECEIPT");
    }
    @Test void manualRecoveryKeepsAttemptsAndHistoryAndIsNotAStaffOverride(){
        long id=waiting();actor(1,"STUDENT");service.acceptance(id,true,"checked");releaseStatus=503;
        service.syncDue();clock.now=NOW.plusSeconds(30);service.syncDue();clock.now=NOW.plusSeconds(90);service.syncDue();assertThat(mapper.ticket(id).syncAttempts()).isEqualTo(3);
        actor(4,"PROPERTY_STAFF");assertThatThrownBy(() -> service.recover(id,"not a manager")).isInstanceOf(ApiFailure.class);
        actor(3,"PROPERTY_MANAGER");releaseStatus=200;service.recover(id,"Verified stable source and exact release");
        assertThat(mapper.ticket(id).status()).isEqualTo("COMPLETED");assertThat(mapper.ticket(id).syncAttempts()).isEqualTo(3);
        assertThat(mapper.events(id).stream().filter(e -> e.action().equals("RECOVERY_REQUESTED"))).hasSize(1);
    }
    @Test void onlyAssignedStaffCanWorkAndReviewIsOnceAfterCompletion(){
        long id=assigned();actor(8,"PROPERTY_STAFF");assertThatThrownBy(() -> service.start(id)).isInstanceOf(ApiFailure.class);
        actor(4,"PROPERTY_STAFF");service.start(id);service.syncDue();service.finish(id,"done");actor(1,"STUDENT");
        assertThatThrownBy(() -> service.review(id,new MaintenanceController.Rating(5,"too early"))).isInstanceOf(ApiFailure.class);
        service.acceptance(id,true,"checked");service.syncDue();service.review(id,new MaintenanceController.Rating(5,"good"));
        assertThatThrownBy(() -> service.review(id,new MaintenanceController.Rating(4,"duplicate"))).isInstanceOf(ApiFailure.class);
    }
    @Test void auditDeliveryRetainsStableSourceAndOmitsRepairDescriptions(){
        actor(1,"STUDENT");service.report(new MaintenanceController.Report(9L,"private body"));
        long id=db.jdbc().queryForObject("SELECT min(id) FROM maintenance.audit_outbox",Long.class);outbox.deliverOne(id);
        assertThat(db.jdbc().queryForObject("SELECT status FROM maintenance.audit_outbox WHERE id=?",String.class,id)).isEqualTo("DELIVERED");
        assertThat(lastAudit).containsEntry("sourceModule","M3").containsEntry("actorUserId",1);assertThat(lastAudit).doesNotContainKeys("description","note","token");
    }
    static class TestClock extends Clock{Instant now=NOW;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}}
}
