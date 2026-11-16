package cz.vsb.catering.business.domain;

/** Observer (GoF): notified after every successful change of a MenuRevision (UC08 step 4). */
public interface MenuRevisionListener {
    void revisionChanged(MenuRevision revision);
}
