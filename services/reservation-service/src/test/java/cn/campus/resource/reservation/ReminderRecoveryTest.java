package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class ReminderRecoveryTest extends ReservationApiTestSupport {
    private static final Instant START = INITIAL_TIME.plusSeconds(2 * 3600);
    private static final Instant FIRST = START.minusSeconds(15 * 60);
    @Autowired private LifecycleService lifecycle;

    @BeforeEach
    void prepareEmptyWorkflow() { clean(); }

    @AfterEach
    void restoreFixture() {
        platformJdbc.update("UPDATE platform.\"user\" SET enabled = TRUE WHERE username = 'demo_student_01'");
        clean();
        testClock.set(INITIAL_TIME);
    }

    @Test
    void aRealDRecipientRejectionIsTerminalAndDoesNotConsumeFurtherAttempts() {
        long reservation = create(0);
        platformJdbc.update("UPDATE platform.\"user\" SET enabled = FALSE WHERE username = 'demo_student_01'");
        testClock.set(FIRST);
        lifecycle.runOnce();
        assertThat(jdbc.queryForMap("SELECT status, attempt_count, last_error_code, notification_id, accepted_at "
                + "FROM reservation.reminder WHERE reservation_id = ?", reservation))
                .containsEntry("status", "FAILED")
                .containsEntry("attempt_count", 1)
                .containsEntry("last_error_code", "PLATFORM_HTTP_422")
                .containsEntry("notification_id", null)
                .containsEntry("accepted_at", null);
        testClock.set(FIRST.plusSeconds(60));
        lifecycle.runOnce();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM reservation.reminder WHERE reservation_id = ?",
                Integer.class, reservation)).isEqualTo(1);
        assertThat(platformJdbc.queryForObject("SELECT count(*) FROM platform.notification", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM reservation.seat_reservation WHERE id = ?",
                String.class, reservation)).isEqualTo("CREATED");
    }

    @Test
    void expiredClaimsRecoverWithTheSameSourceAndExhaustedClaimsDoNotMakeAFourthAttempt() {
        long recoverable = create(0);
        long exhausted = create(1);
        long recoverableReminder = crashedClaim(recoverable, 1);
        long exhaustedReminder = crashedClaim(exhausted, 3);
        testClock.set(FIRST);
        lifecycle.runOnce();

        assertThat(jdbc.queryForMap("SELECT status, attempt_count, claim_token, claimed_until "
                + "FROM reservation.reminder WHERE id = ?", recoverableReminder))
                .containsEntry("status", "ACCEPTED")
                .containsEntry("attempt_count", 2)
                .containsEntry("claim_token", null)
                .containsEntry("claimed_until", null);
        assertThat(jdbc.queryForMap("SELECT status, attempt_count, last_error_code, claim_token "
                + "FROM reservation.reminder WHERE id = ?", exhaustedReminder))
                .containsEntry("status", "FAILED")
                .containsEntry("attempt_count", 3)
                .containsEntry("last_error_code", "DELIVERY_ACK_UNKNOWN")
                .containsEntry("claim_token", null);
        assertThat(platformJdbc.queryForObject("SELECT count(*) FROM platform.notification", Integer.class)).isEqualTo(1);
        assertThat(platformJdbc.queryForMap("SELECT reservation_id, source_ref_id FROM platform.notification"))
                .containsEntry("reservation_id", recoverable)
                .containsEntry("source_ref_id", "reservation-reminder:" + recoverableReminder);
        lifecycle.runOnce();
        assertThat(platformJdbc.queryForObject("SELECT count(*) FROM platform.notification", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM reservation.reminder WHERE id = ?",
                Integer.class, exhaustedReminder)).isEqualTo(3);
    }

    private long create(int seatOffset) {
        long room = platformJdbc.queryForObject("SELECT id FROM platform.room WHERE enabled ORDER BY id LIMIT 1", Long.class);
        long seat = platformJdbc.queryForObject("SELECT id FROM platform.seat WHERE room_id = ? AND enabled "
                + "ORDER BY id LIMIT 1 OFFSET ?", Long.class, room, seatOffset);
        var response = request(HttpMethod.POST, "/api/v1/reservations", Map.of("roomId", room, "seatId", seat,
                "startAt", OffsetDateTime.ofInstant(START, ZoneOffset.ofHours(8)).toString(),
                "endAt", OffsetDateTime.ofInstant(START.plusSeconds(3600), ZoneOffset.ofHours(8)).toString()),
                login("demo_student_01"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) data(response).get("id")).longValue();
    }

    private long crashedClaim(long reservation, int attempts) {
        // These isolated DB rows model a process that died after durable claiming and before acknowledgement.
        return jdbc.queryForObject("INSERT INTO reservation.reminder "
                + "(reservation_id,kind,due_at,expires_at,status,attempt_count,next_attempt_at,claim_token,claimed_until) "
                + "VALUES (?,'CHECKIN_FIRST',?,?,'SENDING',?,?,?,?) RETURNING id", Long.class,
                reservation, Timestamp.from(FIRST), Timestamp.from(START.plusSeconds(10 * 60)), attempts,
                Timestamp.from(FIRST), UUID.randomUUID(), Timestamp.from(FIRST.minusSeconds(1)));
    }

    private void clean() {
        clearReservationRows();
        platformJdbc.update("DELETE FROM platform.notification");
    }
}
