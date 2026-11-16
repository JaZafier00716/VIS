package cz.vsb.catering.presentation.web;

import cz.vsb.catering.business.service.SharingService;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** UC18 actor Čas: runs SharingService.deactivateExpiredLinks at start and then once per period
 *  (a day in the application) on a daemon thread of a JDK ScheduledExecutorService. */
public class ExpiredLinkScheduler {
    public static final Duration DAILY = Duration.ofHours(24);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "expired-link-scheduler");
        t.setDaemon(true);
        return t;
    });
    private final SharingService sharing;
    private final Duration period;

    public ExpiredLinkScheduler(SharingService sharing, Duration period) {
        this.sharing = sharing;
        this.period = period;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::runOnce, 0, period.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() { executor.shutdownNow(); }

    private void runOnce() {
        try {
            int count = sharing.deactivateExpiredLinks();
            if (count > 0) System.out.println("Zneplatněno prošlých odkazů: " + count);
        } catch (RuntimeException e) {
            // an exception would cancel all further runs; the next day tries again
            System.err.println("Zneplatnění prošlých odkazů selhalo: " + e.getMessage());
        }
    }
}
