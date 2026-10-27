package cn.campus.resource.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "campus.maintenance.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class MaintenanceScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceScheduler.class);
    private final MaintenanceService maintenance;
    MaintenanceScheduler(MaintenanceService maintenance) { this.maintenance = maintenance; }

    @Scheduled(fixedDelayString = "${campus.maintenance.scan-delay-ms:30000}", initialDelayString = "${campus.maintenance.scan-delay-ms:30000}")
    void timeout() {
        try { maintenance.scanEscalations(); }
        catch (RuntimeException failure) { LOG.error("Maintenance timeout scan failed type={}", failure.getClass().getSimpleName()); }
    }

    @Scheduled(fixedDelayString = "${campus.maintenance.scan-delay-ms:30000}", initialDelayString = "${campus.maintenance.scan-delay-ms:30000}")
    void sync() {
        try { maintenance.syncDue(); }
        catch (RuntimeException failure) { LOG.error("Maintenance sync failed type={}", failure.getClass().getSimpleName()); }
    }
}
