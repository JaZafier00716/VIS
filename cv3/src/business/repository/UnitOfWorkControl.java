package business.repository;

/**
 * What the business layer needs from the Unit of Work: it decides when a business operation ends
 * (cv05 p.5 "BL řídí začátek a dokončení operace"). Recording and writing the changes is the data access
 * layer's job; the repositories register new, changed and removed objects.
 */
public interface UnitOfWorkControl {
    /** Saves all registered changes in one transaction and ends the unit of work. */
    void commit();

    /** Discards all registered changes and ends the unit of work. */
    void rollback();

    boolean hasChanges();
}
