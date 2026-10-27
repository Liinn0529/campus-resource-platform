package cn.campus.resource.classroom;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

/** Real HTTP transport to an explicit response fixture; not a real-M1 integration test. */
class OccupancyReleaseClientTest {
    static final Instant START=Instant.parse("2026-11-01T01:00:00Z"),END=START.plusSeconds(7200);
    static final String TOKEN="test-only-member03-m1-token-at-least-32-bytes";
    HttpServer server;OccupancyClient client;ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    int status;Map<String,Object> receipt;String envelopeId="test-trace";
    AtomicReference<String> method=new AtomicReference<>(),path=new AtomicReference<>(),authorization=new AtomicReference<>();
    @BeforeEach void start() throws Exception {
        status=200;receipt=new LinkedHashMap<>();receipt.put("id",91);receipt.put("roomId",9);receipt.put("startAt",START.toString());
        receipt.put("endAt",END.toString());receipt.put("sourceModule","M2");receipt.put("sourceType","CLASSROOM_BOOKING");receipt.put("sourceRefId","classroom-request:7");receipt.put("status","RELEASED");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            method.set(exchange.getRequestMethod());path.set(exchange.getRequestURI().getPath());authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            byte[] body=json.writeValueAsBytes(Map.of("data",receipt,"requestId",envelopeId));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            if(status==302){exchange.getResponseHeaders().set("Location","http://127.0.0.1:9/never-forward");}
            exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        client=new OccupancyClient(RestClient.builder(),"http://127.0.0.1:"+server.getAddress().getPort(),TOKEN,"test-only-different-platform-token-at-least-32-bytes");
    }
    @AfterEach void stop(){server.stop(0);}
    ClassroomMapper.RequestRow request(){return new ClassroomMapper.RequestRow(7,1,9,START,END,"test","APPROVED",START.minusSeconds(100),START.minusSeconds(100),null,1,null,null,null,null,91L);}
    @Test void releasesOnlyTheExactSourceAndChecksReceipt() {
        assertThat(client.release(request())).isEqualTo(91);assertThat(method.get()).isEqualTo("DELETE");assertThat(path.get()).isEqualTo("/internal/v1/room-blocks/M2/classroom-request:7");
        assertThat(authorization.get()).isEqualTo("Bearer "+TOKEN);
    }
    @Test void rejectsOtherOccupancyIds(){receipt.put("id",92);invalid();}
    @Test void rejectsOtherRooms(){receipt.put("roomId",10);invalid();}
    @Test void rejectsOtherSources(){receipt.put("sourceRefId","other");invalid();}
    @Test void rejectsOtherSourceTypes(){receipt.put("sourceType","RESERVATION");invalid();}
    @Test void rejectsOtherStartTimes(){receipt.put("startAt",START.plusSeconds(1).toString());invalid();}
    @Test void rejectsOtherEndTimes(){receipt.put("endAt",END.plusSeconds(1).toString());invalid();}
    @Test void doesNotTreatActiveAsReleased(){receipt.put("status","ACTIVE");invalid();}
    @Test void doesNotTreat201AsReleaseSuccess(){status=201;invalid();}
    @Test void requiresARequestId(){envelopeId="";invalid();}
    @Test void neverTreats404AsSuccessfulRelease(){status=404;var e=catchThrowableOfType(() -> client.release(request()),OccupancyClient.SyncFailure.class);assertThat(e.code()).isEqualTo("M1_HTTP_404");assertThat(e.retryable()).isFalse();}
    @Test void onlyServerErrorsAreAutomaticallyRetryable(){status=503;var e=catchThrowableOfType(() -> client.release(request()),OccupancyClient.SyncFailure.class);assertThat(e.retryable()).isTrue();}
    @Test void doesNotFollowRedirects(){status=302;invalid();}
    void invalid(){var e=catchThrowableOfType(() -> client.release(request()),OccupancyClient.SyncFailure.class);assertThat(e.code()).isEqualTo("M1_INVALID_RECEIPT");assertThat(e.retryable()).isFalse();}
}
