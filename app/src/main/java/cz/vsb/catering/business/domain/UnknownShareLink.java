package cz.vsb.catering.business.domain;

import java.time.LocalDateTime;

/** Special Case (Fowler): stands for a token that is not in the database, so callers need no null checks. */
public class UnknownShareLink extends ShareLink {
    public UnknownShareLink(String token) {
        super(0, 0, token, LocalDateTime.MIN, LocalDateTime.MIN, false);
    }

    @Override public boolean isValid(LocalDateTime now) { return false; }

    @Override public boolean exists() { return false; }
}
