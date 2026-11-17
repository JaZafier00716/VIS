package dataaccess.persistence;

import dataaccess.unitofwork.StorageTransaction;

/** A Unit of Work commit over JSON files: files written during the commit are restored on rollback. */
public class JsonFileTransaction implements StorageTransaction {
    @Override public void begin() { JsonFiles.begin(); }
    @Override public void commit() { JsonFiles.commit(); }
    @Override public void rollback() { JsonFiles.rollback(); }
}
