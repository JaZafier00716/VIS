package cz.vsb.catering.business.domain;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** Link with limited validity through which a customer sees one revision (UC11). */
public class ShareLink extends DomainObject {
    public static final Duration VALIDITY = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final long menuId;
    private final int revisionNo;
    private final String token;
    private final LocalDateTime createdAt;
    private final LocalDateTime validUntil;
    private boolean active;

    public ShareLink(long menuId, int revisionNo, String token, LocalDateTime createdAt,
                     LocalDateTime validUntil, boolean active) {
        this.menuId = menuId;
        this.revisionNo = revisionNo;
        this.token = token;
        this.createdAt = createdAt;
        this.validUntil = validUntil;
        this.active = active;
    }

    /** New active link with a random 32-character token, valid 14 days. */
    public static ShareLink create(long menuId, int revisionNo, LocalDateTime now) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return new ShareLink(menuId, revisionNo, HexFormat.of().formatHex(bytes), now, now.plus(VALIDITY), true);
    }

    /** UC11 step 5: the link exists, is active and not expired. */
    public boolean isValid(LocalDateTime now) { return active && now.isBefore(validUntil); }

    /** false only for the Special Case UnknownShareLink. */
    public boolean exists() { return true; }

    public void deactivate() { active = false; }

    public long getMenuId() { return menuId; }
    public int getRevisionNo() { return revisionNo; }
    public String getToken() { return token; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public boolean isActive() { return active; }
}
