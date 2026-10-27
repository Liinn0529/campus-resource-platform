package cn.campus.resource.classroom;

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

/** Actual PostgreSQL state-machine tests; M1/D responses are explicitly scoped HTTP fixtures. */
class Member03ReleaseWorkflowTest {
    static Member03Database db;
    static final Instant NOW=Instant.parse("2026-11-01T00:00:00Z"),START=NOW.plusSeconds(7200),END=START.plusSeconds(7200);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    HttpServer fixture;ClassroomService service;ClassroomMapper mapper;TestClock clock;AuditOutbox outbox;
    int releaseStatus,auditStatus;boolean wrongId;Map<String,Object> lastAudit;int releaseCalls;
    Map<String,Object> receipt;
    volatile boolean holdFirstRelease;
    final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),allow=new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.ExecutorService httpPool;
    @BeforeAll static void database() throws Exception{db=new Member03Database("classroom");}
    @AfterAll static void stopDatabase() throws Exception{db.close();}
    @BeforeEach void setup() throws Exception{
        db.clear();clock=new TestClock();releaseStatus=200;auditStatus=201;wrongId=false;releaseCalls=0;
        fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        fixture.createContext("/",e -> {
            String path=e.getRequestURI().getPath();Map<String,Object> data;int code=200;
            if(path.startsWith("/internal/v1/rooms/")){
                data=Map.of("id",9,"buildingId",1,"code","C9","name","Test classroom","type","CLASSROOM","enabled",true);
            }else if(path.equals("/internal/v1/audit-events")){
                lastAudit=json.readValue(e.getRequestBody(),Map.class);data=Map.of("id",1,"status","ACCEPTED");code=auditStatus;
            }else if(e.getRequestMethod().equals("POST")){
                receipt=new LinkedHashMap<>(json.readValue(e.getRequestBody(),Map.class));receipt.put("id",91);receipt.put("status","ACTIVE");data=receipt;code=201;
            }else{
                releaseCalls++;
                if(holdFirstRelease && releaseCalls==1){entered.countDown();try{if(!allow.await(10,java.util.concurrent.TimeUnit.SECONDS)){throw new java.io.IOException("test latch timed out");}}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new java.io.IOException(failure);}}
                data=new LinkedHashMap<>(receipt);data.put("status","RELEASED");if(wrongId){data.put("id",92);}code=releaseStatus;
            }
            byte[] body=json.writeValueAsBytes(Map.of("data",data,"requestId",UUID.randomUUID().toString()));
            e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(code,body.length);e.getResponseBody().write(body);e.close();
        });httpPool=java.util.concurrent.Executors.newCachedThreadPool();fixture.setExecutor(httpPool);fixture.start();String url="http://127.0.0.1:"+fixture.getAddress().getPort();
        mapper=db.mapper(ClassroomMapper.class);
        var platform=new PlatformClient(RestClient.builder(),url,"test-only-member03-platform-token-at-least-32-bytes");
        var occupancy=new OccupancyClient(RestClient.builder(),url,"test-only-member03-m1-token-at-least-32-bytes","test-only-member03-platform-token-at-least-32-bytes");
        service=new ClassroomService(mapper,platform,occupancy,new AccessPolicy(),clock,db.manager(),24);
        outbox=new AuditOutbox(db.mapper(AuditOutboxMapper.class),RestClient.builder(),clock,db.manager(),url,"test-only-member03-platform-token-at-least-32-bytes");
    }
    @AfterEach void stop(){allow.countDown();fixture.stop(0);httpPool.shutdownNow();SecurityContextHolder.clearContext();}
    void actor(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new Actor(id,"test-user",Set.of(role),Set.of("classroom:apply","classroom:approve"),Set.of(9L)),null,List.of()));}
    long pending(){actor(1,"STUDENT");long id=service.submit(new ClassroomController.Submit(9L,START.atOffset(ZoneOffset.UTC),END.atOffset(ZoneOffset.UTC),"test purpose")).id();actor(2,"ROOM_ADMIN");service.decide(id,true,null);actor(3,"DEPARTMENT_ADMIN");service.decide(id,true,null);return id;}
    long approved(){long id=pending();service.syncDue();assertThat(mapper.request(id).status()).isEqualTo("APPROVED");return id;}
    @Test void approvedCancellationWaitsForExactReleaseAndRetainsHistory(){
        long id=approved();actor(1,"STUDENT");assertThat(service.cancel(id).status()).isEqualTo("RELEASING");assertThat(mapper.request(id).occupancyId()).isEqualTo(91);
        service.releaseDue();assertThat(mapper.request(id).status()).isEqualTo("CANCELLED");assertThat(mapper.release(id).status()).isEqualTo("CONFIRMED");assertThat(releaseCalls).isEqualTo(1);
        service.releaseDue();assertThat(releaseCalls).isEqualTo(1);assertThat(mapper.actions(id)).hasSize(2);
    }
    @Test void normalExpiryReleasesAndCompletesAtTheExactBoundary(){
        long id=approved();clock.now=END.minusNanos(1000);service.releaseDue();assertThat(mapper.request(id).status()).isEqualTo("APPROVED");
        clock.now=END;service.releaseDue();assertThat(mapper.request(id).status()).isEqualTo("COMPLETED");assertThat(mapper.release(id).reason()).isEqualTo("EXPIRED");
    }
    @Test void cancellationAfterStartIsRejectedWithoutRemoteChanges(){
        long id=approved();clock.now=START;actor(1,"STUDENT");assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(ApiFailure.class);assertThat(releaseCalls).isZero();
    }
    @Test void unattemptedApprovalCanCancelWithoutCreatingAnOccupancy(){
        long id=pending();actor(1,"STUDENT");assertThat(service.cancel(id).status()).isEqualTo("CANCELLED");service.syncDue();assertThat(receipt).isNull();
    }
    @Test void badReleaseReceiptAnd404KeepTheKnownOccupancy(){
        long id=approved();actor(1,"STUDENT");service.cancel(id);wrongId=true;service.releaseDue();assertThat(mapper.request(id).status()).isEqualTo("SYNC_FAILED");assertThat(mapper.request(id).occupancyId()).isEqualTo(91);assertThat(mapper.release(id).error()).isEqualTo("M1_INVALID_RECEIPT");
        actor(2,"ROOM_ADMIN");wrongId=false;releaseStatus=404;service.recover(id,"Confirm previous source status");assertThat(mapper.request(id).status()).isEqualTo("SYNC_FAILED");assertThat(mapper.release(id).error()).isEqualTo("M1_HTTP_404");
    }
    @Test void releaseHasThreeAutomaticAttemptsAndManualRecoveryNeverResetsThem(){
        long id=approved();actor(1,"STUDENT");service.cancel(id);releaseStatus=503;
        service.releaseDue();clock.now=NOW.plusSeconds(30);service.releaseDue();clock.now=NOW.plusSeconds(90);service.releaseDue();
        assertThat(mapper.release(id).attempts()).isEqualTo(3);assertThat(mapper.request(id).status()).isEqualTo("SYNC_FAILED");
        assertThatThrownBy(() -> service.recover(id,"owner must not recover")).isInstanceOf(ApiFailure.class);
        releaseStatus=200;actor(2,"ROOM_ADMIN");assertThat(service.recover(id,"Verified source; retry exact release").status()).isEqualTo("CANCELLED");
        assertThat(mapper.release(id).attempts()).isEqualTo(3);assertThat(mapper.events(id).stream().filter(e -> e.action().equals("RECOVERY_REQUESTED"))).hasSize(1);
    }
    @Test void auditOutboxIsAtomicDurableAndOmitsPrivateContent(){
        actor(1,"STUDENT");service.submit(new ClassroomController.Submit(9L,START.atOffset(ZoneOffset.UTC),END.atOffset(ZoneOffset.UTC),"private purpose"));
        long id=db.jdbc().queryForObject("SELECT min(id) FROM classroom.audit_outbox",Long.class);auditStatus=503;outbox.deliverOne(id);
        assertThat(db.jdbc().queryForObject("SELECT attempts FROM classroom.audit_outbox WHERE id=?",Integer.class,id)).isEqualTo(1);
        assertThat(db.jdbc().queryForObject("SELECT status FROM classroom.audit_outbox WHERE id=?",String.class,id)).isEqualTo("PENDING");
        assertThat(lastAudit).containsEntry("sourceModule","M2").containsEntry("actorUserId",1);assertThat(lastAudit).doesNotContainKeys("purpose","note","token");
        clock.now=NOW.plusSeconds(30);auditStatus=200;outbox.deliverOne(id);
        assertThat(db.jdbc().queryForObject("SELECT status FROM classroom.audit_outbox WHERE id=?",String.class,id)).isEqualTo("DELIVERED");
    }

    @Test void httpDoesNotHoldLocalLocksAndALateWorkerCannotOverwriteANewerReceipt() throws Exception {
        long id=approved();actor(1,"STUDENT");service.cancel(id);holdFirstRelease=true;
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=workers.submit(() -> service.releaseDue());
            assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var lock=workers.submit(() -> new org.springframework.transaction.support.TransactionTemplate(db.manager()).execute(tx -> mapper.lockRequest(id).status()));
            assertThat(lock.get(2,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("RELEASING");
            clock.now=NOW.plusSeconds(31);service.releaseDue();
            assertThat(mapper.request(id).status()).isEqualTo("CANCELLED");
            allow.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(mapper.request(id).status()).isEqualTo("CANCELLED");assertThat(mapper.release(id).attempts()).isEqualTo(2);
            assertThat(mapper.events(id).stream().filter(e -> e.action().equals("RELEASE_CONFIRMED"))).hasSize(1);
        }
    }
    static class TestClock extends Clock{Instant now=NOW;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}}
}
