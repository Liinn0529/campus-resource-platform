package cn.campus.resource.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class MaintenanceService {
    record View(long id, long reporterId, long roomId, String description, String status, Long assigneeId,
            Instant reportedAt, Instant updatedAt, Instant dueAt, int repairCycle, boolean escalated,
            Instant blockStartAt, Long occupancyId, String syncOperation, int syncAttempts, String syncError) {
        @Override public String toString() { return "MaintenanceView[id=" + id + ",status=" + status + "]"; }
    }
    record Detail(View ticket, List<MaintenanceMapper.Event> events, MaintenanceMapper.Review review) { }
    private record Claim(MaintenanceMapper.Ticket ticket, UUID token) { }
    private final MaintenanceMapper mapper;
    private final PlatformClient platform;
    private final OccupancyClient occupancy;
    private final AccessPolicy access;
    private final Clock clock;
    private final Duration timeout;
    private final TransactionTemplate transactions;
    private final TransactionTemplate snapshots;

    MaintenanceService(MaintenanceMapper mapper, PlatformClient platform, OccupancyClient occupancy,
            AccessPolicy access, Clock clock, PlatformTransactionManager manager,
            @Value("${campus.maintenance.repair-timeout-hours:24}") int hours) {
        if (hours <= 0) { throw new IllegalStateException("Repair timeout must be positive"); }
        this.mapper=mapper; this.platform=platform; this.occupancy=occupancy; this.access=access; this.clock=clock;
        timeout=Duration.ofHours(hours);
        transactions=new TransactionTemplate(manager); transactions.setTimeout(10);
        snapshots=new TransactionTemplate(manager); snapshots.setTimeout(10); snapshots.setReadOnly(true);
        snapshots.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    View report(MaintenanceController.Report input) {
        access.require("maintenance:report");
        Actor actor=access.actor();
        if (!actor.roles().contains("STUDENT")) { throw forbidden(); }
        enabledRoom(input.roomId());
        return transactions.execute(tx -> {
            Instant now=clock.instant();
            var row=mapper.insert(actor.id(),input.roomId(),input.description(),now);
            event(row,actor.id(),"REPORTED",null,now);
            return view(row);
        });
    }

    List<View> list(String type,int limit,int offset) {
        Actor actor=access.actor();
        if (type.equals("managed")) { requireManager(actor); }
        if (type.equals("assigned") && !actor.roles().contains("PROPERTY_STAFF")) { throw forbidden(); }
        return mapper.list(type,actor.id(),limit,offset).stream().map(this::view).toList();
    }

    Detail detail(long id) {
        Actor actor=access.actor();
        return snapshots.execute(tx -> {
            var row=mapper.ticket(id);
            if (row==null || !(row.reporterId()==actor.id() || manager(actor)
                    || (actor.roles().contains("PROPERTY_STAFF") && Long.valueOf(actor.id()).equals(row.assigneeId())))) { throw missing(); }
            return new Detail(view(row),mapper.events(id),mapper.review(id));
        });
    }

    View assign(long id,long assignee) {
        Actor actor=access.actor(); requireManager(actor);
        if (!platform.eligibleStaff(assignee)) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"MAINTENANCE_INVALID_ASSIGNEE","维修人员当前不可派单");
        }
        return transactions.execute(tx -> {
            var row=required(id); requireState(row,"REPORTED");
            if (row.reporterId()==assignee) { throw forbidden(); }
            Instant now=clock.instant(); mapper.assign(id,assignee,now.plus(timeout),now);
            event(mapper.ticket(id),actor.id(),"ASSIGNED",null,now);
            return view(mapper.ticket(id));
        });
    }

    View start(long id) {
        Actor actor=access.actor();
        var before=mapper.ticket(id); requireStaff(actor,before); enabledRoom(before.roomId());
        return transactions.execute(tx -> {
            var row=required(id); requireStaff(actor,row); requireState(row,"ASSIGNED");
            Instant now=clock.instant(); mapper.start(id,now); event(row,actor.id(),"START_REQUESTED",null,now);
            return view(mapper.ticket(id));
        });
    }

    View finish(long id,String note) {
        Actor actor=access.actor();
        return transactions.execute(tx -> {
            var row=required(id); requireStaff(actor,row); requireState(row,"IN_PROGRESS");
            Instant now=clock.instant(); mapper.state(id,"WAITING_ACCEPTANCE",now);
            event(row,actor.id(),"FINISHED",note,now); return view(mapper.ticket(id));
        });
    }

    View acceptance(long id,boolean accept,String note) {
        Actor actor=access.actor();
        return transactions.execute(tx -> {
            var row=owned(id,actor); requireState(row,"WAITING_ACCEPTANCE");
            if (row.syncOperation()!=null) { throw conflict(); }
            Instant now=clock.instant();
            if (accept) { mapper.release(id,now); }
            else { mapper.rework(id,now.plus(timeout),now); }
            event(mapper.ticket(id),actor.id(),accept?"ACCEPT_REQUESTED":"ACCEPTANCE_REJECTED",note,now);
            return view(mapper.ticket(id));
        });
    }

    View cancel(long id) {
        Actor actor=access.actor();
        return transactions.execute(tx -> {
            var row=owned(id,actor);
            if (!Set.of("REPORTED","ASSIGNED").contains(row.status())) { throw conflict(); }
            Instant now=clock.instant(); mapper.state(id,"CANCELLED",now); event(row,actor.id(),"CANCELLED",null,now);
            return view(mapper.ticket(id));
        });
    }

    MaintenanceMapper.Review review(long id,MaintenanceController.Rating input) {
        Actor actor=access.actor();
        return transactions.execute(tx -> {
            var row=owned(id,actor); requireState(row,"COMPLETED");
            if (mapper.review(id)!=null) { throw conflict(); }
            Instant now=clock.instant(); mapper.addReview(id,actor.id(),input.rating(),input.comment(),now);
            event(row,actor.id(),"REVIEWED",null,now); return mapper.review(id);
        });
    }

    void scanEscalations() {
        RuntimeException first=null;
        for (long id:mapper.overdue(clock.instant())) {
            try {
                transactions.executeWithoutResult(tx -> {
                    var row=mapper.lock(id); Instant now=clock.instant();
                    if (row==null || row.escalated() || row.dueAt()==null || now.isBefore(row.dueAt())
                            || !Set.of("ASSIGNED","BLOCKING_ROOM","IN_PROGRESS","SYNC_FAILED").contains(row.status())) { return; }
                    mapper.escalate(id,now); event(row,null,"ESCALATED",null,now);
                });
            } catch (RuntimeException failure) { if (first==null) { first=failure; } }
        }
        if (first!=null) { throw first; }
    }

    void syncDue() {
        RuntimeException first=null;
        for (long id:mapper.syncDue(clock.instant())) {
            try { synchronize(id); }
            catch (RuntimeException failure) { if (first==null) { first=failure; } }
        }
        if (first!=null) { throw first; }
    }

    private void synchronize(long id) {
        Claim claim=transactions.execute(tx -> {
            var row=mapper.lock(id); Instant now=clock.instant();
            if (row==null || row.syncOperation()==null || !Set.of("BLOCKING_ROOM","WAITING_ACCEPTANCE").contains(row.status())) { return null; }
            if (row.syncClaimToken()!=null && now.isBefore(row.syncClaimedUntil())) { return null; }
            if (row.syncClaimToken()==null && now.isBefore(row.syncNextAttemptAt())) { return null; }
            if (row.syncAttempts()>=3) {
                mapper.failed(id,"SYNC_FAILED","M1_ACK_UNKNOWN",null,now); event(row,null,"SYNC_FAILED",null,now); return null;
            }
            UUID token=UUID.randomUUID(); mapper.claim(id,token,now.plusSeconds(30),now);
            event(row,null,"SYNC_ATTEMPTED",null,now); return new Claim(mapper.ticket(id),token);
        });
        if (claim==null) { return; }
        Long receipt=null; OccupancyClient.SyncFailure error=null;
        try { receipt=occupancy.synchronize(claim.ticket()); }
        catch (OccupancyClient.SyncFailure failure) { error=failure; }
        Long result=receipt; OccupancyClient.SyncFailure failure=error;
        transactions.executeWithoutResult(tx -> {
            var row=mapper.lock(id);
            if (row==null || !claim.token().equals(row.syncClaimToken())
                    || !claim.ticket().syncOperation().equals(row.syncOperation())) { return; }
            Instant now=clock.instant();
            if (failure==null) {
                boolean release=row.syncOperation().equals("RELEASE");
                mapper.confirmed(id,release?"COMPLETED":"IN_PROGRESS",result,now);
                event(row,null,release?"RELEASE_CONFIRMED":"OCCUPANCY_CONFIRMED",null,now);
            } else {
                boolean retry=failure.retryable() && row.syncAttempts()<3;
                mapper.failed(id,retry?row.status():"SYNC_FAILED",failure.code(),retry?now.plusSeconds(30L*row.syncAttempts()):null,now);
                event(row,null,retry?"SYNC_RETRY_SCHEDULED":"SYNC_FAILED",null,now);
            }
        });
    }


    View recover(long id,String reason) {
        Actor actor=access.actor();requireManager(actor);
        Claim claim=transactions.execute(tx -> {
            var row=required(id);requireState(row,"SYNC_FAILED");
            if(row.syncOperation()==null){throw conflict();}
            Instant now=clock.instant();
            if(row.syncClaimToken()!=null && now.isBefore(row.syncClaimedUntil())){throw conflict();}
            UUID token=UUID.randomUUID();mapper.claimRecovery(id,token,now.plusSeconds(30),now);
            event(row,actor.id(),"RECOVERY_REQUESTED",reason,now);return new Claim(row,token);
        });
        Long receipt=null;OccupancyClient.SyncFailure error=null;
        try{receipt=occupancy.synchronize(claim.ticket());}catch(OccupancyClient.SyncFailure failure){error=failure;}
        Long result=receipt;OccupancyClient.SyncFailure failure=error;
        transactions.executeWithoutResult(tx -> {
            var row=mapper.lock(id);
            if(row==null || !row.status().equals("SYNC_FAILED") || !claim.token().equals(row.syncClaimToken())
                    || !claim.ticket().syncOperation().equals(row.syncOperation())){return;}
            Instant now=clock.instant();
            if(failure==null){
                boolean release=row.syncOperation().equals("RELEASE");mapper.confirmed(id,release?"COMPLETED":"IN_PROGRESS",result,now);
                event(row,null,release?"RELEASE_CONFIRMED":"OCCUPANCY_CONFIRMED",null,now);
            }else{mapper.failed(id,"SYNC_FAILED",failure.code(),null,now);event(row,null,"SYNC_FAILED",null,now);}
        });
        return detail(id).ticket();
    }

    private boolean manager(Actor actor) { return actor.roles().contains("PROPERTY_MANAGER") && actor.permissions().contains("maintenance:assign"); }
    private void requireManager(Actor actor) { if (!manager(actor)) { throw forbidden(); } }
    private void requireStaff(Actor actor,MaintenanceMapper.Ticket row) {
        if (row==null) { throw missing(); }
        if (!actor.roles().contains("PROPERTY_STAFF") || !Long.valueOf(actor.id()).equals(row.assigneeId())) { throw forbidden(); }
    }
    private MaintenanceMapper.Ticket required(long id) { var row=mapper.lock(id); if(row==null){throw missing();} return row; }
    private MaintenanceMapper.Ticket owned(long id,Actor actor) { var row=required(id); if(row.reporterId()!=actor.id()){throw missing();} return row; }
    private void requireState(MaintenanceMapper.Ticket row,String state) { if(!row.status().equals(state)){throw conflict();} }
    private void enabledRoom(long id) { if(!platform.room(id).enabled()){throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"MAINTENANCE_ROOM_DISABLED","房间未启用");} }
    private void event(MaintenanceMapper.Ticket row,Long actor,String action,String note,Instant now) {
        mapper.event(row.id(),actor,action,row.repairCycle(),note,now);mapper.enqueueAudit(row.id(),UUID.fromString(RequestIds.current()));
    }
    private View view(MaintenanceMapper.Ticket r) { return new View(r.id(),r.reporterId(),r.roomId(),r.description(),r.status(),r.assigneeId(),r.reportedAt(),r.updatedAt(),r.dueAt(),r.repairCycle(),r.escalated(),r.blockStartAt(),r.occupancyId(),r.syncOperation(),r.syncAttempts(),r.syncError()); }
    private ApiFailure forbidden() { return new ApiFailure(HttpStatus.FORBIDDEN,"FORBIDDEN","无权执行此操作"); }
    private ApiFailure missing() { return new ApiFailure(HttpStatus.NOT_FOUND,"MAINTENANCE_NOT_FOUND","工单不存在"); }
    private ApiFailure conflict() { return new ApiFailure(HttpStatus.CONFLICT,"MAINTENANCE_STATE_CONFLICT","工单状态不允许此操作"); }
}
