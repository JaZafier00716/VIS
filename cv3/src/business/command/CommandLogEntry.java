package business.command;

import java.time.Instant;

/** CV09 Command: one line of the command log – when, what, on which order and how it ended. */
public record CommandLogEntry(Instant time, String operation, String orderId, boolean success, String message) {
}
