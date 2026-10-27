package cn.campus.resource.classroom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "campus.classroom.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class ClassroomScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ClassroomScheduler.class);
    private final ClassroomService classroom;
    ClassroomScheduler(ClassroomService classroom) { this.classroom = classroom; }

    @Scheduled(fixedDelayString = "${campus.classroom.scan-delay-ms:30000}", initialDelayString = "${campus.classroom.scan-delay-ms:30000}")
    void timeout() {
        try { classroom.scanTimeouts(); }
        catch (RuntimeException failure) { LOG.error("Classroom timeout scan failed type={}", failure.getClass().getSimpleName()); }
    }

    @Scheduled(fixedDelayString = "${campus.classroom.scan-delay-ms:30000}", initialDelayString = "${campus.classroom.scan-delay-ms:30000}")
    void sync() {
        try { classroom.syncDue(); }
        catch (RuntimeException failure) { LOG.error("Classroom sync failed type={}", failure.getClass().getSimpleName()); }
    }

    @Scheduled(fixedDelayString = "${campus.classroom.scan-delay-ms:30000}", initialDelayString = "${campus.classroom.scan-delay-ms:30000}")
    void release() {
        try { classroom.releaseDue(); }
        catch (RuntimeException failure) { LOG.error("Classroom release failed type={}",failure.getClass().getSimpleName()); }
    }
}
