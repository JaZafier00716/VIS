package dataaccess.unitofwork;

/** The storage-specific transaction a Unit of Work commit runs in. */
public interface StorageTransaction {
    void begin();

    void commit();

    void rollback();
}
