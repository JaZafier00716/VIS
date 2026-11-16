package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.port.TransactionRunner;
import java.util.function.Supplier;

/** Test double: runs the work directly, without a database transaction. */
public class DirectTransactionRunner implements TransactionRunner {
    @Override public <T> T inTransaction(Supplier<T> work) { return work.get(); }
}
