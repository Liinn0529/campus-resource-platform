package cn.campus.resource.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CommonService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    record Settings(int maxReservationHours) { }
    record CheckinCode(long roomId, LocalDate date, String code) { }
    record Verification(boolean valid) { }

    private final CommonMapper common;
    private final SpaceMapper spaces;
    private final AccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;
    private final SecureRandom random = new SecureRandom();

    CommonService(CommonMapper common, SpaceMapper spaces, AccessPolicy access, AuditService audit,
            Clock clock, ZoneId zone) {
        this.common = common;
        this.spaces = spaces;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.zone = zone;
    }

    Settings settings() {
        access.require("settings:view");
        return internalSettings();
    }

    @Transactional
    Settings updateSettings(CommonController.SettingsWrite input) {
        access.requireGlobal("settings:manage");
        if (common.setMaxReservationHours(input.maxReservationHours()) != 1) {
            throw new IllegalStateException("Required system setting is missing");
        }
        audit.record("SETTINGS_UPDATE", "SYSTEM_SETTING", "max_reservation_hours");
        return new Settings(input.maxReservationHours());
    }

    CheckinCode checkinCode(long roomId) {
        access.requireRoom(roomId, "checkin-code:manage");
        requireAvailableRoom(roomId, false);
        LocalDate date = today();
        String code = common.checkinCode(roomId, date);
        if (code == null) {
            throw new ApiFailure(HttpStatus.NOT_FOUND, "CHECKIN_CODE_NOT_FOUND", "Today's check-in code does not exist");
        }
        return new CheckinCode(roomId, date, code);
    }

    @Transactional
    CheckinCode ensureCheckinCode(long roomId) {
        access.requireRoom(roomId, "checkin-code:manage");
        requireAvailableRoom(roomId, true);
        LocalDate date = today();
        String existing = common.checkinCode(roomId, date);
        if (existing != null) {
            return new CheckinCode(roomId, date, existing);
        }
        char[] characters = new char[8];
        for (int i = 0; i < characters.length; i++) {
            characters[i] = CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)];
        }
        // The database key decides the winner when two requests generate today's code concurrently.
        if (common.insertCheckinCode(roomId, date, new String(characters), clock.instant()) == 1) {
            audit.record("CHECKIN_CODE_CREATE", "ROOM", Long.toString(roomId));
        }
        return new CheckinCode(roomId, date, common.checkinCode(roomId, date));
    }

    Settings internalSettings() {
        return new Settings(common.maxReservationHours());
    }

    Verification verify(CommonController.CodeVerification input) {
        String correct = common.checkinCode(input.roomId(), today());
        boolean valid = correct != null && MessageDigest.isEqual(correct.getBytes(StandardCharsets.US_ASCII),
                input.code().getBytes(StandardCharsets.US_ASCII));
        return new Verification(valid);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    private void requireAvailableRoom(long roomId, boolean lock) {
        var room = lock ? spaces.lockRoom(roomId) : spaces.room(roomId);
        if (room == null || !room.enabled()) {
            throw new ApiFailure(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found");
        }
    }
}
