package cn.campus.resource.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "campus.reservation.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class LifecycleScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(LifecycleScheduler.class);
    private final LifecycleService lifecycle;
    private final AuditDeliveryService audit;
    private final boolean auditEnabled;

    LifecycleScheduler(LifecycleService lifecycle, AuditDeliveryService audit,
            @org.springframework.beans.factory.annotation.Value("${campus.reservation.audit-dispatch-enabled:false}") boolean auditEnabled) {
        this.lifecycle = lifecycle; this.audit = audit; this.auditEnabled = auditEnabled;
    }

    @Scheduled(fixedDelayString = "${campus.reservation.scan-delay-ms:30000}",
            initialDelayString = "${campus.reservation.scan-delay-ms:30000}")
    void audit() {
        if (!auditEnabled) { return; }
        try { audit.dispatchDue(); }
        catch (RuntimeException failure) { LOG.error("Reservation audit dispatch failed type={}", failure.getClass().getSimpleName()); }
    }

    @Scheduled(fixedDelayString = "${campus.reservation.scan-delay-ms:30000}",
            initialDelayString = "${campus.reservation.scan-delay-ms:30000}")
    void scan() {
        try {
            lifecycle.scanDue();
        } catch (RuntimeException failure) {
            LOG.error("Reservation lifecycle scan failed type={}", failure.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${campus.reservation.scan-delay-ms:30000}",
            initialDelayString = "${campus.reservation.scan-delay-ms:30000}")
    void dispatch() {
        try {
            lifecycle.dispatchDue();
        } catch (RuntimeException failure) {
            LOG.error("Reservation reminder dispatch failed type={}", failure.getClass().getSimpleName());
        }
    }
}
