package business.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CV09 Command, the invoker: runs any {@link IOrderCommand} without knowing what it does and records the result
 * in a log. A rejected command (for example an invalid status transition) is logged as a failure, not rethrown.
 */
public class OrderCommandExecutor {
    private final List<CommandLogEntry> log = new ArrayList<>();

    public CommandLogEntry execute(IOrderCommand command) {
        CommandLogEntry entry;
        try {
            command.execute();
            entry = new CommandLogEntry(Instant.now(), command.getName(), command.getOrderId(), true, "OK");
        } catch (RuntimeException e) {
            entry = new CommandLogEntry(Instant.now(), command.getName(), command.getOrderId(), false,
                    e.getMessage());
        }
        log.add(entry);
        return entry;
    }

    public List<CommandLogEntry> getLog() {
        return Collections.unmodifiableList(log);
    }
}
