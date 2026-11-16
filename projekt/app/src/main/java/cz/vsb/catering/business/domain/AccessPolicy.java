package cz.vsb.catering.business.domain;

/** A-17: the one place that says which role unlocks which area. Roles are additive and each unlocks only its own
 *  area: WORKER the desktop worker UI and all worker actions, ADMIN the desktop admin mode (UC17) and nothing else,
 *  MANAGER the read-only /manager web (UC15, only projects of his own workers). */
public final class AccessPolicy {
    private AccessPolicy() { }

    public static boolean canUseDesktopWorker(User u) { return u != null && u.isActive() && u.hasRole(Role.WORKER); }

    public static boolean canUseAdminMode(User u) { return u != null && u.isActive() && u.hasRole(Role.ADMIN); }

    public static boolean canUseManagerWeb(User u) { return u != null && u.isActive() && u.hasRole(Role.MANAGER); }

    /** The desktop opens for a worker (main window) or an administrator (admin mode only). */
    public static boolean canLoginToDesktop(User u) { return canUseDesktopWorker(u) || canUseAdminMode(u); }

    public static void requireWorker(User u) {
        if (!canUseDesktopWorker(u)) throw new ValidationException("Tato funkce je dostupná jen pracovníkovi.");
    }

    public static void requireManager(User u) {
        if (!canUseManagerWeb(u)) throw new ValidationException("Tato funkce je dostupná jen manažerovi.");
    }
}
