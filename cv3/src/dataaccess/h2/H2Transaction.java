package dataaccess.h2;

import dataaccess.unitofwork.StorageTransaction;

/** A Unit of Work commit as one real JDBC transaction. */
public class H2Transaction implements StorageTransaction {
    private final H2Database database;

    public H2Transaction(H2Database database) {
        this.database = database;
    }

    @Override public void begin() { database.begin(); }
    @Override public void commit() { database.commit(); }
    @Override public void rollback() { database.rollback(); }
}
