package cn.campus.resource.reservation;

import java.util.UUID;
import org.slf4j.MDC;

final class RequestIds {
    private RequestIds() { }

    static String current() {
        String value = MDC.get("requestId");
        return value == null ? UUID.randomUUID().toString() : value;
    }
}
