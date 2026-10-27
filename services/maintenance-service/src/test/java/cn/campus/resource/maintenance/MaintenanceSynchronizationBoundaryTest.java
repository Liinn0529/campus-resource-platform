package cn.campus.resource.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

// External M1 response fixture only. This does not implement or prove Phase8 room blocking/release.
@Import(MaintenanceSynchronizationBoundaryTest.BoundaryConfiguration.class)
class MaintenanceSynchronizationBoundaryTest extends MaintenanceApiTestSupport {
    private static final String CLIENT_TOKEN="test-only-maintenance-boundary-token-at-least-32-bytes";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,Map<String,Object>> RECEIPTS=new ConcurrentHashMap<>();
    private static final AtomicInteger CALLS=new AtomicInteger();
    private static volatile String mode="ok";
    private static final HttpServer REMOTE=startRemote();
    @Autowired MaintenanceService service;
    private String student,staff,manager,other;
    private long room,staffId;
    private static final String PATH="/api/v1/maintenance-tickets";

    private static HttpServer startRemote() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                CALLS.incrementAndGet();
                int status=200; Object body;
                if (!exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer "+CLIENT_TOKEN)) {
                    status=401; body=Map.of("code","UNAUTHORIZED");
                } else if (mode.equals("server")) { status=503; body=Map.of("code","UNAVAILABLE"); }
                else if (mode.equals("client")) { status=409; body=Map.of("code","CONFLICT"); }
                else if (mode.equals("redirect")) { status=307; exchange.getResponseHeaders().set("Location","http://127.0.0.1:9/forbidden"); body=Map.of(); }
                else {
                    Map<String,Object> row;
                    if (exchange.getRequestMethod().equals("POST")) {
                        row=new LinkedHashMap<>(JSON.readValue(exchange.getRequestBody(),Map.class));
                        String source=(String)row.get("sourceRefId");
                        row.put("id",1000L); row.put("status","ACTIVE");
                        RECEIPTS.put(source,new LinkedHashMap<>(row)); status=201;
                    } else {
                        String source=exchange.getRequestURI().getPath().substring("/internal/v1/room-blocks/M3/".length());
                        row=new LinkedHashMap<>(RECEIPTS.get(source)); row.put("status","RELEASED");
                    }
                    if (mode.equals("wrong")) { row.put("sourceModule","M2"); }
                    body=Map.of("data",row,"requestId","explicit-m1-boundary-fixture");
                }
                byte[] bytes=JSON.writeValueAsBytes(body);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start(); return server;
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class BoundaryConfiguration {
        @Bean @Primary OccupancyClient fixtureClient() {
            return new OccupancyClient(RestClient.builder(),"http://127.0.0.1:"+REMOTE.getAddress().getPort(),CLIENT_TOKEN,MAINTENANCE_TOKEN);
        }
    }
    @BeforeEach void prepare() {
        clearMaintenanceRows(); mode="ok"; CALLS.set(0); RECEIPTS.clear();
        student=login("demo_student_01"); other=login("demo_student_02"); staff=login("demo_property_staff"); manager=login(MANAGER_USERNAME);
        room=platformJdbc.queryForObject("SELECT id FROM platform.room WHERE enabled ORDER BY id LIMIT 1",Long.class);
        staffId=platformUserId("demo_property_staff");
    }
    @AfterEach void cleanup() { clearMaintenanceRows(); }
    @AfterAll static void stop() { REMOTE.stop(0); }
    private long start() {
        var report=request(HttpMethod.POST,PATH,Map.of("roomId",room,"description","Fixture workflow"),student);
        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id=((Number)data(report).get("id")).longValue();
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/assign",Map.of("assigneeId",staffId),manager).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/start",null,staff).getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }
    private String state(long id) { return jdbc.queryForObject("SELECT status FROM maintenance.repair_ticket WHERE id=?",String.class,id); }
    private void finish(long id) { assertThat(request(HttpMethod.POST,PATH+"/"+id+"/finish",Map.of("note","Repair performed"),staff).getStatusCode()).isEqualTo(HttpStatus.OK); }
    private void accept(long id) { assertThat(request(HttpMethod.POST,PATH+"/"+id+"/accept",Map.of("note","Verified repair"),student).getStatusCode()).isEqualTo(HttpStatus.OK); }

    @Test void exactReceiptsEnableFinishReworkAcceptanceAndOneOwnerReview() {
        long id=start(); service.syncDue(); assertThat(state(id)).isEqualTo("IN_PROGRESS");
        assertThat(RECEIPTS.get("maintenance-ticket:"+id).get("endAt")).isNull();
        testClock.set(INITIAL_TIME.plusSeconds(86400)); service.scanEscalations(); finish(id);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/accept",Map.of("note","No right"),other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/reject-acceptance",Map.of("note","Needs rework"),student).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(state(id)).isEqualTo("IN_PROGRESS"); assertThat(CALLS.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT repair_cycle FROM maintenance.repair_ticket WHERE id=?",Integer.class,id)).isEqualTo(2);
        testClock.set(INITIAL_TIME.plusSeconds(172800)); service.scanEscalations(); service.scanEscalations();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance.ticket_event WHERE ticket_id=? AND action='ESCALATED'",Long.class,id)).isEqualTo(2);
        finish(id); accept(id); assertThat(state(id)).isEqualTo("WAITING_ACCEPTANCE");
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/reject-acceptance",Map.of("note","Too late"),student).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/review",Map.of("rating",5),student).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        service.syncDue(); assertThat(state(id)).isEqualTo("COMPLETED"); assertThat(CALLS.get()).isEqualTo(2);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/review",Map.of("rating",4,"comment","Resolved"),other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/review",Map.of("rating",4,"comment","Resolved"),student).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/review",Map.of("rating",5),student).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance.ticket_review WHERE ticket_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void releaseFailureNeverClaimsCompletionOrAllowsUnsafeCancellation() {
        long id=start(); service.syncDue(); finish(id); accept(id); mode="client"; service.syncDue();
        assertThat(state(id)).isEqualTo("SYNC_FAILED");
        assertThat(jdbc.queryForObject("SELECT sync_operation FROM maintenance.repair_ticket WHERE id=?",String.class,id)).isEqualTo("RELEASE");
        assertThat(jdbc.queryForObject("SELECT occupancy_id FROM maintenance.repair_ticket WHERE id=?",Long.class,id)).isEqualTo(1000);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/cancel",null,student).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(HttpMethod.POST,PATH+"/"+id+"/review",Map.of("rating",5),student).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void serverFailuresRetryAtThirtyAndSixtySecondsAtMostThreeTotalCalls() {
        long id=start(); mode="server"; service.syncDue(); assertThat(state(id)).isEqualTo("BLOCKING_ROOM");
        testClock.set(INITIAL_TIME.plusSeconds(29)); service.syncDue(); assertThat(CALLS.get()).isEqualTo(1);
        testClock.set(INITIAL_TIME.plusSeconds(30)); service.syncDue(); assertThat(CALLS.get()).isEqualTo(2);
        testClock.set(INITIAL_TIME.plusSeconds(89)); service.syncDue(); assertThat(CALLS.get()).isEqualTo(2);
        testClock.set(INITIAL_TIME.plusSeconds(90)); service.syncDue(); service.syncDue();
        assertThat(CALLS.get()).isEqualTo(3); assertThat(state(id)).isEqualTo("SYNC_FAILED");
    }
    @Test void mismatchedReceiptAndRedirectAreTerminalRatherThanSuccess() {
        for(String value:List.of("wrong","redirect")) {
            long id=start(); mode=value; service.syncDue();
            assertThat(state(id)).isEqualTo("SYNC_FAILED");
            assertThat(jdbc.queryForObject("SELECT occupancy_id FROM maintenance.repair_ticket WHERE id=?",Long.class,id)).isNull();
        }
        assertThat(CALLS.get()).isEqualTo(2);
    }
    @Test void expiredLeaseRecoversButExhaustedClaimNeverMakesAFourthCall() {
        long id=start();
        jdbc.update("UPDATE maintenance.repair_ticket SET sync_attempts=1,sync_claim_token=?,sync_claimed_until=? WHERE id=?",UUID.randomUUID(),java.sql.Timestamp.from(INITIAL_TIME.plusSeconds(30)),id);
        service.syncDue(); assertThat(CALLS.get()).isZero();
        testClock.set(INITIAL_TIME.plusSeconds(30)); service.syncDue(); assertThat(state(id)).isEqualTo("IN_PROGRESS");
        long exhausted=start();
        jdbc.update("UPDATE maintenance.repair_ticket SET sync_attempts=3,sync_claim_token=?,sync_claimed_until=? WHERE id=?",UUID.randomUUID(),java.sql.Timestamp.from(INITIAL_TIME),exhausted);
        service.syncDue(); assertThat(CALLS.get()).isEqualTo(1); assertThat(state(exhausted)).isEqualTo("SYNC_FAILED");
        assertThat(jdbc.queryForObject("SELECT sync_error FROM maintenance.repair_ticket WHERE id=?",String.class,exhausted)).isEqualTo("M1_ACK_UNKNOWN");
    }
}
