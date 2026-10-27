package cn.campus.resource.classroom;

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
class ClassroomService {
    record View(long id, long applicantId, long roomId, Instant startAt, Instant endAt, String purpose,
            String status, Instant submittedAt, Instant updatedAt, Instant approvalDueAt,
            int syncAttempts, String syncError, Long occupancyId, String syncOperation, String releaseReason) {
        @Override public String toString() { return "ClassroomView[id=" + id + ",status=" + status + "]"; }
    }
    record Detail(View request, List<ClassroomMapper.Step> steps, List<ClassroomMapper.Action> actions,
            List<ClassroomMapper.Event> events) { }
    private record Claim(ClassroomMapper.RequestRow request, UUID token) { }
    private static final Set<String> APPROVAL_STATES = Set.of("SUBMITTED", "APPROVING");
    private final ClassroomMapper mapper;
    private final PlatformClient platform;
    private final OccupancyClient occupancy;
    private final AccessPolicy access;
    private final Clock clock;
    private final Duration approvalTimeout;
    private final TransactionTemplate transactions;
    private final TransactionTemplate snapshots;

    ClassroomService(ClassroomMapper mapper, PlatformClient platform, OccupancyClient occupancy,
            AccessPolicy access, Clock clock, PlatformTransactionManager manager,
            @Value("${campus.classroom.approval-timeout-hours:24}") int hours) {
        if (hours <= 0) { throw new IllegalStateException("Approval timeout hours must be positive"); }
        this.mapper = mapper;
        this.platform = platform;
        this.occupancy = occupancy;
        this.access = access;
        this.clock = clock;
        this.approvalTimeout = Duration.ofHours(hours);
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(10);
        this.snapshots = new TransactionTemplate(manager);
        this.snapshots.setReadOnly(true);
        this.snapshots.setTimeout(10);
        this.snapshots.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    View submit(ClassroomController.Submit input) {
        access.require("classroom:apply");
        Actor actor = access.actor();
        if (!actor.roles().contains("STUDENT")) { throw forbidden(); }
        Instant start = input.startAt().toInstant();
        Instant end = input.endAt().toInstant();
        if (start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0 || !end.isAfter(start)) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "CLASSROOM_INVALID_INTERVAL", "申请时间区间不符合要求");
        }
        requireFuture(start, clock.instant());
        requireRoom(input.roomId());
        return transactions.execute(transaction -> {
            Instant now = clock.instant();
            requireFuture(start, now);
            var created = mapper.insert(actor.id(), input.roomId(), start, end, input.purpose(), now, deadline(now, start));
            if (mapper.initialSteps(created.id(), created.approvalDueAt()) != 2) { throw inconsistent(); }
            event(created.id(), actor.id(), "SUBMITTED", now);
            return view(created);
        });
    }

    List<View> list(String type, int limit, int offset) {
        Actor actor = access.actor();
        if (type.equals("mine")) { return mapper.own(actor.id(), limit, offset).stream().map(this::view).toList(); }
        access.require("classroom:approve");
        if (!approvalRole(actor)) { throw forbidden(); }
        if (actor.roomIds().isEmpty()) { return List.of(); }
        return mapper.pending(actor.id(), actor.roomIds(), actor.roles().contains("ROOM_ADMIN"),
                actor.roles().contains("DEPARTMENT_ADMIN"), clock.instant(), limit, offset).stream().map(this::view).toList();
    }

    Detail detail(long id) {
        Actor actor = access.actor();
        return snapshots.execute(transaction -> {
            var row = mapper.request(id);
            if (row == null || (row.applicantId() != actor.id() && !canReview(actor, row.roomId()))) { throw missing(); }
            return new Detail(view(row), mapper.steps(id), mapper.actions(id), mapper.events(id));
        });
    }

    View decide(long id, boolean approve, String reason) {
        access.require("classroom:approve");
        Actor actor = access.actor();
        var previous = mapper.request(id);
        if (previous == null) { throw missing(); }
        requireApprovalState(previous);
        String expectedRole = previous.status().equals("SUBMITTED") ? "ROOM_ADMIN" : "DEPARTMENT_ADMIN";
        if (actor.id() == previous.applicantId() || !canReview(actor, previous.roomId())
                || !actor.roles().contains(expectedRole)) { throw forbidden(); }
        if (approve) { requireRoom(previous.roomId()); }
        return transactions.execute(transaction -> {
            var row = mapper.lockRequest(id);
            if (row == null) { throw missing(); }
            requireApprovalState(row);
            var step = mapper.pendingStep(id);
            requireStage(actor, row, step);
            Instant now = clock.instant();
            requireBeforeDeadline(row, now);
            if (mapper.decideStep(id, step.stepNumber(), approve ? "APPROVED" : "REJECTED", actor.id(), now) != 1
                    || mapper.action(id, step.stepNumber(), actor.id(), approve ? "APPROVE" : "REJECT", reason, now) != 1) {
                throw inconsistent();
            }
            if (!approve) {
                mapper.closeSteps(id, "CANCELLED", now);
                mapper.approvalState(id, "REJECTED", null, now);
                event(id, actor.id(), "REJECTED", now);
            } else if (step.stepNumber() == 1) {
                Instant due = deadline(now, row.startAt());
                if (mapper.activateDepartment(id, due) != 1) { throw inconsistent(); }
                mapper.approvalState(id, "APPROVING", due, now);
                event(id, actor.id(), "FIRST_APPROVED", now);
            } else {
                mapper.queueSync(id, now);
                event(id, actor.id(), "FINAL_APPROVED", now);
            }
            return view(mapper.request(id));
        });
    }

    View cancel(long id) {
        long actor = access.actor().id();
        return transactions.execute(transaction -> {
            var row = mapper.lockRequest(id);
            if (row == null || row.applicantId() != actor) { throw missing(); }
            Instant now = clock.instant();
            if (row.status().equals("APPROVED")) {
                if (!now.isBefore(row.startAt())) {
                    throw new ApiFailure(HttpStatus.CONFLICT,"CLASSROOM_CANCEL_TOO_LATE","已开始的教室申请不能取消");
                }
                queueRelease(row,"CANCEL",actor,now);
                return view(mapper.request(id));
            }
            if (row.status().equals("APPROVED_PENDING_SYNC")) {
                if (row.syncAttempts()!=0 || row.syncClaimToken()!=null) {
                    throw new ApiFailure(HttpStatus.CONFLICT,"CLASSROOM_SYNC_UNCERTAIN","占用结果尚未确认，请由审批人核实同步结果");
                }
                requireFuture(row.startAt(),now);
                mapper.syncFailure(id,"CANCELLED",null,null,now);
                event(id,actor,"CANCELLED",now);
                return view(mapper.request(id));
            }
            requireApprovalState(row);
            requireBeforeDeadline(row, now);
            mapper.closeSteps(id, "CANCELLED", now);
            mapper.approvalState(id, "CANCELLED", null, now);
            event(id, actor, "CANCELLED", now);
            return view(mapper.request(id));
        });
    }

    public void runOnce() { scanTimeouts(); syncDue(); releaseDue(); }

    void scanTimeouts() {
        RuntimeException first = null;
        for (long id : mapper.timedOut(clock.instant())) {
            try {
                transactions.executeWithoutResult(transaction -> {
                    var row = mapper.lockRequest(id);
                    if (row == null || !APPROVAL_STATES.contains(row.status())) { return; }
                    Instant now = clock.instant();
                    if (now.isBefore(row.approvalDueAt())) { return; }
                    mapper.closeSteps(id, "EXPIRED", now);
                    mapper.approvalState(id, "AUTO_CANCELLED", null, now);
                    event(id, null, "AUTO_CANCELLED", now);
                });
            } catch (RuntimeException failure) { if (first == null) { first = failure; } }
        }
        if (first != null) { throw first; }
    }

    void syncDue() {
        RuntimeException first = null;
        for (long id : mapper.syncDue(clock.instant())) {
            try { synchronize(id); }
            catch (RuntimeException failure) { if (first == null) { first = failure; } }
        }
        if (first != null) { throw first; }
    }

    private void synchronize(long id) {
        Claim claim = transactions.execute(transaction -> {
            var row = mapper.lockRequest(id);
            if (row == null || !row.status().equals("APPROVED_PENDING_SYNC")) { return null; }
            Instant now = clock.instant();
            if (row.syncClaimToken() != null && now.isBefore(row.syncClaimedUntil())) { return null; }
            if (row.syncClaimToken() == null && now.isBefore(row.syncNextAttemptAt())) { return null; }
            if (!now.isBefore(row.startAt()) || row.syncAttempts() >= 3) {
                mapper.syncFailure(id, "SYNC_FAILED", row.syncAttempts() >= 3 ? "M1_ACK_UNKNOWN" : "BOOKING_START_PASSED", null, now);
                event(id, null, "SYNC_FAILED", now);
                return null;
            }
            UUID token = UUID.randomUUID();
            mapper.claimSync(id, token, now.plusSeconds(30), now);
            event(id, null, "SYNC_ATTEMPTED", now);
            return new Claim(mapper.request(id), token);
        });
        if (claim == null) { return; }
        // The remote side can commit even if the response is lost. Never hold local locks over HTTP.
        Long receipt = null;
        OccupancyClient.SyncFailure error = null;
        try { receipt = occupancy.create(claim.request()); }
        catch (OccupancyClient.SyncFailure failure) { error = failure; }
        Long occupancyId = receipt;
        OccupancyClient.SyncFailure failure = error;
        transactions.executeWithoutResult(transaction -> {
            var current = mapper.lockRequest(id);
            if (current == null || !current.status().equals("APPROVED_PENDING_SYNC")
                    || !claim.token().equals(current.syncClaimToken())) { return; }
            Instant now = clock.instant();
            if (failure == null) {
                mapper.synchronizedState(id, occupancyId, now);
                event(id, null, "OCCUPANCY_CONFIRMED", now);
            } else {
                boolean retry = failure.retryable() && current.syncAttempts() < 3 && now.isBefore(current.startAt());
                mapper.syncFailure(id, retry ? "APPROVED_PENDING_SYNC" : "SYNC_FAILED", failure.code(),
                        retry ? now.plusSeconds(30L * current.syncAttempts()) : null, now);
                event(id, null, retry ? "SYNC_RETRY_SCHEDULED" : "SYNC_FAILED", now);
            }
        });
    }


    private void queueRelease(ClassroomMapper.RequestRow row,String reason,Long actor,Instant now) {
        mapper.queue(row.id(),reason,now);
        mapper.approvalState(row.id(),"RELEASING",null,now);
        event(row.id(),actor,reason.equals("CANCEL")?"CANCEL_REQUESTED":"EXPIRY_RELEASE_REQUESTED",now);
    }

    void releaseDue() {
        for (long id:mapper.expired(clock.instant())) {
            transactions.executeWithoutResult(tx -> {
                var row=mapper.lockRequest(id);Instant now=clock.instant();
                if (row!=null && row.status().equals("APPROVED") && !now.isBefore(row.endAt())) {
                    queueRelease(row,"EXPIRED",null,now);
                }
            });
        }
        RuntimeException first=null;
        for (long id:mapper.due(clock.instant())) {
            try { releaseOne(id); }
            catch (RuntimeException failure) { if(first==null){first=failure;} }
        }
        if(first!=null){throw first;}
    }

    private void releaseOne(long id) {
        Claim claim=transactions.execute(tx -> {
            var row=mapper.lockRequest(id);var release=mapper.release(id);Instant now=clock.instant();
            if(row==null || !row.status().equals("RELEASING") || release==null || !release.status().equals("PENDING")){return null;}
            if(release.claimToken()!=null && now.isBefore(release.claimedUntil())){return null;}
            if(release.claimToken()==null && now.isBefore(release.nextAttemptAt())){return null;}
            if(release.attempts()>=3){
                mapper.finish(id,"FAILED","M1_ACK_UNKNOWN",null);
                mapper.syncFailure(id,"SYNC_FAILED","M1_ACK_UNKNOWN",null,now);
                event(id,null,"SYNC_FAILED",now);return null;
            }
            UUID token=UUID.randomUUID();mapper.claim(id,token,now.plusSeconds(30));
            event(id,null,"RELEASE_ATTEMPTED",now);return new Claim(row,token);
        });
        if(claim==null){return;}
        OccupancyClient.SyncFailure error=null;
        try{occupancy.release(claim.request());}catch(OccupancyClient.SyncFailure failure){error=failure;}
        OccupancyClient.SyncFailure failure=error;
        transactions.executeWithoutResult(tx -> {
            var row=mapper.lockRequest(id);var release=mapper.release(id);
            if(row==null || release==null || !row.status().equals("RELEASING") || !claim.token().equals(release.claimToken())){return;}
            Instant now=clock.instant();
            if(failure==null){confirmRelease(row,release,now);}
            else{
                boolean retry=failure.retryable() && release.attempts()<3;
                mapper.finish(id,retry?"PENDING":"FAILED",failure.code(),retry?now.plusSeconds(30L*release.attempts()):null);
                if(!retry){mapper.syncFailure(id,"SYNC_FAILED",failure.code(),null,now);}
                event(id,null,retry?"RELEASE_RETRY_SCHEDULED":"SYNC_FAILED",now);
            }
        });
    }

    private void confirmRelease(ClassroomMapper.RequestRow row,ReleaseMapper.Release release,Instant now) {
        mapper.finish(row.id(),"CONFIRMED",null,null);
        mapper.releasedState(row.id(),release.reason().equals("CANCEL")?"CANCELLED":"COMPLETED",now);
        event(row.id(),null,"RELEASE_CONFIRMED",now);
    }

    View recover(long id,String reason) {
        Actor actor=access.actor();
        Claim claim=transactions.execute(tx -> {
            var row=mapper.lockRequest(id);
            if(row==null){throw missing();}
            if(!canReview(actor,row.roomId()) || actor.id()==row.applicantId()){throw forbidden();}
            if(!row.status().equals("SYNC_FAILED")){throw new ApiFailure(HttpStatus.CONFLICT,"CLASSROOM_STATE_CONFLICT","仅失败同步可核实恢复");}
            Instant now=clock.instant();
            if(row.syncClaimToken()!=null && now.isBefore(row.syncClaimedUntil())){throw new ApiFailure(HttpStatus.CONFLICT,"CLASSROOM_BUSY","已有核实请求正在处理中");}
            UUID token=UUID.randomUUID();mapper.claimRecovery(id,token,now.plusSeconds(30),now);
            mapper.recoveryEvent(id,actor.id(),reason,UUID.fromString(RequestIds.current()),now);mapper.enqueueAudit(id);
            return new Claim(row,token);
        });
        ReleaseMapper.Release release=mapper.release(id);
        Long receipt=null;OccupancyClient.SyncFailure error=null;
        try{receipt=release==null?occupancy.create(claim.request()):occupancy.release(claim.request());}
        catch(OccupancyClient.SyncFailure failure){error=failure;}
        Long confirmed=receipt;OccupancyClient.SyncFailure failure=error;
        transactions.executeWithoutResult(tx -> {
            var row=mapper.lockRequest(id);
            if(row==null || !row.status().equals("SYNC_FAILED") || !claim.token().equals(row.syncClaimToken())){return;}
            Instant now=clock.instant();
            if(failure==null){
                if(release==null){mapper.synchronizedState(id,confirmed,now);event(id,null,"OCCUPANCY_CONFIRMED",now);}
                else{confirmRelease(row,release,now);}
            }else{
                mapper.syncFailure(id,"SYNC_FAILED",failure.code(),null,now);
                if(release!=null){mapper.finish(id,"FAILED",failure.code(),null);}
                event(id,null,"SYNC_FAILED",now);
            }
        });
        return detail(id).request();
    }

    private void requireRoom(long roomId) {
        var room = platform.room(roomId);
        if (!room.enabled() || !"CLASSROOM".equals(room.type())) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "CLASSROOM_UNAVAILABLE", "教室当前不可申请");
        }
    }

    private boolean approvalRole(Actor actor) {
        return actor.roles().contains("ROOM_ADMIN") || actor.roles().contains("DEPARTMENT_ADMIN");
    }

    private boolean canReview(Actor actor, long room) {
        return actor.permissions().contains("classroom:approve") && approvalRole(actor) && actor.roomIds().contains(room);
    }

    private void requireStage(Actor actor, ClassroomMapper.RequestRow row, ClassroomMapper.Step step) {
        if (step == null) { throw inconsistent(); }
        if (row.applicantId() == actor.id() || !canReview(actor, row.roomId())
                || !actor.roles().contains(step.requiredRole()) || mapper.alreadyApproved(row.id(), actor.id())) {
            throw forbidden();
        }
    }

    private void requireApprovalState(ClassroomMapper.RequestRow row) {
        if (!APPROVAL_STATES.contains(row.status())) {
            throw new ApiFailure(HttpStatus.CONFLICT, "CLASSROOM_STATE_CONFLICT", "当前申请状态不允许此操作");
        }
    }

    private void requireBeforeDeadline(ClassroomMapper.RequestRow row, Instant now) {
        if (!now.isBefore(row.approvalDueAt()) || !now.isBefore(row.startAt())) {
            throw new ApiFailure(HttpStatus.CONFLICT, "CLASSROOM_APPROVAL_EXPIRED", "审批期限已到");
        }
    }

    private void requireFuture(Instant start, Instant now) {
        if (!start.isAfter(now)) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "CLASSROOM_START_IN_PAST", "申请开始时间必须在将来");
        }
    }

    private Instant deadline(Instant now, Instant start) {
        Instant deadline = now.plus(approvalTimeout);
        return deadline.isBefore(start) ? deadline : start;
    }

    private View view(ClassroomMapper.RequestRow row) {
        var release=mapper.release(row.id());
        return new View(row.id(), row.applicantId(), row.roomId(), row.startAt(), row.endAt(), row.purpose(), row.status(),
                row.submittedAt(), row.updatedAt(), row.approvalDueAt(),release==null?row.syncAttempts():release.attempts(),
                release==null?row.syncError():release.error(),row.occupancyId(),release==null?"BLOCK":"RELEASE",release==null?null:release.reason());
    }

    private void event(long id, Long actor, String action, Instant now) {
        String trace = RequestIds.current();
        mapper.event(id, actor, action, trace == null ? UUID.randomUUID() : UUID.fromString(trace), now);
        mapper.enqueueAudit(id);
    }

    private ApiFailure missing() { return new ApiFailure(HttpStatus.NOT_FOUND, "CLASSROOM_REQUEST_NOT_FOUND", "申请不存在"); }
    private ApiFailure forbidden() { return new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权执行此审批操作"); }
    private IllegalStateException inconsistent() { return new IllegalStateException("Classroom workflow data is inconsistent"); }
}
