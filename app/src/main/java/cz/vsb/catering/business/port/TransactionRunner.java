package cz.vsb.catering.business.port;

import java.util.function.Supplier;

/** Services run each business operation in one transaction (commit, rollback on exception).
 *  Defined by the business layer, implemented by data.oracle.Database. */
public interface TransactionRunner {
    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> { work.run(); return null; });
    }
}
