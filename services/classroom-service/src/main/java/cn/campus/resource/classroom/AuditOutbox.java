package cn.campus.resource.classroom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service-local transactional outbox. D downtime never changes the business outcome. */
@Component
class AuditOutbox {
    record Entry(long id,String sourceEventId,String action,long entityId,Long actorUserId,Instant occurredAt,
            UUID sourceRequestId,String status,int attempts,Instant nextAttemptAt,UUID claimToken,Instant claimedUntil,String error) { }
    record Message(String sourceModule,String sourceEventId,String action,String entityType,String entityId,
            Long actorUserId,Instant occurredAt,UUID sourceRequestId) { }
    private record Accepted(long id,String status) { }
    @JsonIgnoreProperties(ignoreUnknown=true) private record Envelope(Accepted data,String requestId) { }
    private static final Logger LOG=LoggerFactory.getLogger(AuditOutbox.class);
    private final AuditOutboxMapper mapper;
    private final RestClient http;
    private final String token;
    private final Clock clock;
    private final TransactionTemplate tx;
    AuditOutbox(AuditOutboxMapper mapper,RestClient.Builder builder,Clock clock,PlatformTransactionManager manager,
            @Value("${campus.platform.base-url}") String base,@Value("${campus.platform.service-token}") String token) {
        this.mapper=mapper;this.clock=clock;this.token=token;tx=new TransactionTemplate(manager);tx.setTimeout(10);
        var transport=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory=new JdkClientHttpRequestFactory(transport);factory.setReadTimeout(Duration.ofSeconds(5));
        http=builder.baseUrl(base).requestFactory(factory).build();
    }
    @Scheduled(fixedDelayString="${campus.classroom.scan-delay-ms:30000}",initialDelayString="${campus.classroom.scan-delay-ms:30000}")
    void deliver() {
        for(long id:mapper.due(clock.instant())) {
            try{deliverOne(id);}catch(RuntimeException failure){LOG.error("Audit outbox failed id={} type={}",id,failure.getClass().getSimpleName());}
        }
    }
    void deliverOne(long id) {
        Entry entry=tx.execute(status -> {
            var row=mapper.lock(id);Instant now=clock.instant();
            if(row==null || !row.status().equals("PENDING")){return null;}
            if(row.claimToken()!=null && now.isBefore(row.claimedUntil())){return null;}
            if(row.claimToken()==null && now.isBefore(row.nextAttemptAt())){return null;}
            if(row.attempts()>=3){mapper.abandon(id);return null;}
            mapper.claim(id,UUID.randomUUID(),now.plusSeconds(30));return mapper.lock(id);
        });
        if(entry==null){return;}
        boolean retryable=false;String error=null;
        try{
            var response=http.post().uri("/internal/v1/audit-events").headers(h -> h.setBearerAuth(token))
                    .body(new Message("M2",entry.sourceEventId(),entry.action(),"CLASSROOM_REQUEST",Long.toString(entry.entityId()),
                            entry.actorUserId(),entry.occurredAt(),entry.sourceRequestId()))
                    .retrieve().toEntity(new ParameterizedTypeReference<Envelope>(){});
            var body=response.getBody();
            if((response.getStatusCode().value()!=200 && response.getStatusCode().value()!=201) || body==null || body.data()==null
                    || body.requestId()==null || body.requestId().isBlank() || body.requestId().length()>64
                    || body.data().id()<=0 || !"ACCEPTED".equals(body.data().status())){error="D_INVALID_RECEIPT";}
        }catch(RestClientResponseException failure){retryable=failure.getStatusCode().is5xxServerError();error="D_HTTP_"+failure.getStatusCode().value();}
        catch(ResourceAccessException failure){retryable=true;error="D_NETWORK_ERROR";}
        catch(RestClientException failure){error="D_INVALID_RECEIPT";}
        boolean again=error!=null && retryable && entry.attempts()<3;
        mapper.finish(id,entry.claimToken(),error==null?"DELIVERED":again?"PENDING":"FAILED",error,
                again?clock.instant().plusSeconds(30L*entry.attempts()):null);
    }
}

@Mapper
interface AuditOutboxMapper {
    @Select("SELECT * FROM classroom.audit_outbox WHERE id=#{id} FOR UPDATE") AuditOutbox.Entry lock(long id);
    @Select("SELECT id FROM classroom.audit_outbox WHERE status='PENDING' AND ((claim_token IS NULL AND next_attempt_at<=#{now}) OR claimed_until<=#{now}) ORDER BY id LIMIT 100")
    List<Long> due(Instant now);
    @Update("UPDATE classroom.audit_outbox SET attempts=attempts+1,claim_token=#{token},claimed_until=#{until} WHERE id=#{id}")
    int claim(@Param("id") long id,@Param("token") UUID token,@Param("until") Instant until);
    @Update("UPDATE classroom.audit_outbox SET status='FAILED',error='D_ACK_UNKNOWN',claim_token=NULL,claimed_until=NULL,next_attempt_at=NULL WHERE id=#{id}")
    int abandon(long id);
    @Update("UPDATE classroom.audit_outbox SET status=#{status},error=#{error},next_attempt_at=#{next},claim_token=NULL,claimed_until=NULL WHERE id=#{id} AND claim_token=#{token}")
    int finish(@Param("id") long id,@Param("token") UUID token,@Param("status") String status,@Param("error") String error,@Param("next") Instant next);
}
