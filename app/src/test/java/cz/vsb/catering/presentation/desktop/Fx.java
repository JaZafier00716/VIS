package cz.vsb.catering.presentation.desktop;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;

/** Starts the JavaFX toolkit once and runs test steps on the FX thread (no TestFX). */
final class Fx {
    private static boolean started;

    private Fx() {}

    /** Skips the test when there is no display. */
    static synchronized void start() {
        if (started) return;
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyRunning) {
            // started by another test class
        } catch (UnsupportedOperationException | Error noDisplay) {
            Assumptions.abort("JavaFX cannot start (no display): " + noDisplay);
        }
        Platform.setImplicitExit(false);
        started = true;
    }

    static <T> T call(Callable<T> step) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(step.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error err) throw err;
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void run(Runnable step) { call(() -> { step.run(); return null; }); }
}
