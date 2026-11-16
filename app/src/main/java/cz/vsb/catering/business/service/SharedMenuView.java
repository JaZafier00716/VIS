package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.ShareLink;

/** Result of opening a share link on the web (UC11 step 5 / 5.1-5.3).
 *  status VALID: revision is filled; EXPIRED: currentToken is the newer valid link or null; UNKNOWN: nothing.
 *  readOnly: the project is archived, the customer can read the menu but not comment (UC03). */
public record SharedMenuView(Status status, ShareLink link, MenuRevision revision, String title,
                             int guestCount, String currentToken, boolean readOnly) {
    public enum Status { VALID, EXPIRED, UNKNOWN }
}
