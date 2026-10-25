package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TimeRulesTest {
    private final TimeRules rules = new TimeRules(ZoneId.of("Asia/Shanghai"));

    @Test
    void normalizesOffsetInputsAndAllowsWholeHoursAcrossMidnight() {
        var first = rules.interval(at("2026-11-01T23:00:00+08:00"), at("2026-11-02T01:00:00+08:00"));
        var equivalent = rules.interval(at("2026-11-01T15:00:00Z"), at("2026-11-01T17:00:00Z"));
        assertThat(first).isEqualTo(equivalent);
        rules.bookable(first, 4, Instant.parse("2026-11-01T15:00:00Z"));
    }

    @Test
    void rejectsPartialHoursZeroLengthAndReversedIntervals() {
        for (String[] pair : List.of(
                new String[] {"2026-11-01T10:30:00+08:00", "2026-11-01T11:00:00+08:00"},
                new String[] {"2026-11-01T10:00:00.000000001+08:00", "2026-11-01T11:00:00+08:00"},
                new String[] {"2026-11-01T10:00:00+08:00", "2026-11-01T11:00:01+08:00"},
                new String[] {"2026-11-01T10:00:00+08:00", "2026-11-01T10:00:00+08:00"},
                new String[] {"2026-11-01T11:00:00+08:00", "2026-11-01T10:00:00+08:00"})) {
            ApiFailure failure = assertThrows(ApiFailure.class, () -> rules.interval(at(pair[0]), at(pair[1])));
            assertThat(failure.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Test
    void acceptsExactCurrentStartAndRejectsAStartOneNanosecondInThePast() {
        var interval = rules.interval(at("2026-11-01T10:00:00+08:00"), at("2026-11-01T11:00:00+08:00"));
        rules.bookable(interval, 4, interval.startAt());
        ApiFailure failure = assertThrows(ApiFailure.class,
                () -> rules.bookable(interval, 4, interval.startAt().plusNanos(1)));
        assertThat(failure.code()).isEqualTo("RESERVATION_START_IN_PAST");
    }

    @Test
    void appliesTheCurrentMaximumAndFailsClosedForAnInvalidUpstreamSetting() {
        var fourHours = rules.interval(at("2026-11-01T10:00:00+08:00"), at("2026-11-01T14:00:00+08:00"));
        rules.bookable(fourHours, 4, fourHours.startAt());
        assertThat(assertThrows(ApiFailure.class,
                () -> rules.bookable(fourHours, 3, fourHours.startAt())).code())
                .isEqualTo("RESERVATION_DURATION_EXCEEDED");
        ApiFailure unavailable = assertThrows(ApiFailure.class,
                () -> rules.bookable(fourHours, 0, fourHours.startAt()));
        assertThat(unavailable.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private OffsetDateTime at(String value) { return OffsetDateTime.parse(value); }
}
