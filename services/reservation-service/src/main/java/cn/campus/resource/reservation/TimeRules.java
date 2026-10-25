package cn.campus.resource.reservation;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class TimeRules {
    record Interval(Instant startAt, Instant endAt) { }

    private final ZoneId zone;

    TimeRules(ZoneId zone) { this.zone = zone; }

    Interval interval(OffsetDateTime start, OffsetDateTime end) {
        var localStart = start.atZoneSameInstant(zone);
        var localEnd = end.atZoneSameInstant(zone);
        if (localStart.getMinute() != 0 || localStart.getSecond() != 0 || localStart.getNano() != 0
                || localEnd.getMinute() != 0 || localEnd.getSecond() != 0 || localEnd.getNano() != 0) {
            throw invalid("RESERVATION_HOUR_BOUNDARY", "预约时间必须是业务时区的整点小时");
        }
        Instant startAt = start.toInstant();
        Instant endAt = end.toInstant();
        if (!endAt.isAfter(startAt) || Duration.between(startAt, endAt).getSeconds() % 3600 != 0) {
            throw invalid("RESERVATION_INVALID_INTERVAL", "预约结束须晚于开始，且时长为完整小时");
        }
        return new Interval(startAt, endAt);
    }

    void bookable(Interval interval, int maxHours, Instant now) {
        if (maxHours <= 0) {
            throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_INVALID_RESPONSE", "预约参数暂不可用");
        }
        if (interval.startAt().isBefore(now)) {
            throw invalid("RESERVATION_START_IN_PAST", "预约开始不能早于当前时间");
        }
        if (Duration.between(interval.startAt(), interval.endAt()).getSeconds() > (long) maxHours * 3600) {
            throw invalid("RESERVATION_DURATION_EXCEEDED", "预约时长超过当前系统上限");
        }
    }

    private ApiFailure invalid(String code, String message) {
        return new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
