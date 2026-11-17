package dataaccess.unitofwork;

import business.repository.UnitOfWorkControl;
import dataaccess.identity.IdentityMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UnitOfWork implements UnitOfWorkControl {
    public interface EntityPersister {
        void insert(Object entity);
        void update(Object entity);
        void delete(Object entity);
    }

    private final IdentityMap identityMap;
    private final EntityPersister persister;
    private final StorageTransaction transaction;
    private final Map<String, Object> newEntities = new LinkedHashMap<>();
    private final Map<String, Object> dirtyEntities = new LinkedHashMap<>();
    private final Map<String, Object> deletedEntities = new LinkedHashMap<>();

    public UnitOfWork(IdentityMap identityMap, EntityPersister persister, StorageTransaction transaction) {
        this.identityMap = identityMap;
        this.persister = persister;
        this.transaction = transaction;
    }

    public void registerNew(Object entity, String id) {
        if (entity == null) {
            return;
        }
        String key = identityMap.keyFor(entity.getClass(), id);
        removeContradictoryState(key);
        newEntities.put(key, entity);
        identityMap.put(entity, id);
    }

    public void registerDirty(Object entity, String id) {
        if (entity == null) {
            return;
        }
        String key = identityMap.keyFor(entity.getClass(), id);
        if (newEntities.containsKey(key)) {
            return;
        }
        removeContradictoryState(key);
        dirtyEntities.put(key, entity);
        identityMap.put(entity, id);
    }

    public void registerDeleted(Object entity, String id) {
        if (entity == null) {
            return;
        }
        String key = identityMap.keyFor(entity.getClass(), id);
        if (newEntities.containsKey(key)) {
            newEntities.remove(key);
            identityMap.remove(entity, id);
            return;
        }
        dirtyEntities.remove(key);
        deletedEntities.put(key, entity);
        identityMap.remove(entity, id);
    }

    public void remember(Object entity, String id) {
        if (entity == null) {
            return;
        }
        identityMap.put(entity, id);
    }

    public <T> Optional<T> get(Class<T> type, String id) {
        return identityMap.get(type, id);
    }

    /**
     * Commits all pending changes in the current unit of work inside one {@link StorageTransaction}.
     *
     * Guarantee: if any write (or the final commit) fails with an exception, the storage is put back to its state
     * before the commit - with H2 by a JDBC rollback, with JSON by restoring every file written during this commit -
     * the failure is rethrown, and the pending registrations stay intact so the caller can retry or call
     * {@link #rollback()}. If undoing also fails, the exception says so explicitly. For JSON this is
     * application-level recovery and does not cover a process crash in the middle of a commit.
     *
     * A successful commit ends the unit of work: the Identity Map is cleared, so it never outlives one unit of
     * work (cv05 p.9 "nejde o globální cache").
     */
    @Override
    public void commit() {
        if (!hasChanges()) {
            identityMap.clear();
            return;
        }

        transaction.begin();
        try {
            // Referenced records first (customer, finish -> material, address -> order), deletes in reverse order.
            for (Object entity : inDependencyOrder(newEntities.values(), false)) {
                persister.insert(entity);
            }
            for (Object entity : inDependencyOrder(dirtyEntities.values(), false)) {
                persister.update(entity);
            }
            for (Object entity : inDependencyOrder(deletedEntities.values(), true)) {
                persister.delete(entity);
            }
            transaction.commit();
        } catch (RuntimeException ex) {
            try {
                transaction.rollback();
            } catch (RuntimeException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
                throw new IllegalStateException("Unit of Work commit failed and the storage could NOT be restored; "
                        + "it may contain part of the commit.", ex);
            }
            throw new IllegalStateException("Unit of Work commit failed; storage restored to its state before "
                    + "the commit; pending changes remain registered.", ex);
        }
        clearPendingState();
        identityMap.clear();
    }

    public void clear() {
        clearPendingState();
        identityMap.clear();
    }

    @Override
    public boolean hasChanges() {
        return !newEntities.isEmpty() || !dirtyEntities.isEmpty() || !deletedEntities.isEmpty();
    }

    /**
     * Abandons the current unit of work: discards all pending registrations and the Identity Map.
     *
     * Nothing is written before {@link #commit()}, and a failed commit has already restored the storage, so
     * after rollback the storage holds the last successfully committed state. Rollback does not touch storage and
     * cannot undo a commit that already succeeded; in-memory domain objects changed earlier are not reverted, they
     * are simply no longer cached.
     */
    @Override
    public void rollback() {
        clearPendingState();
        identityMap.clear();
    }

    public void clearPendingState() {
        newEntities.clear();
        dirtyEntities.clear();
        deletedEntities.clear();
    }

    private static List<Object> inDependencyOrder(Collection<Object> entities, boolean reverse) {
        Comparator<Object> byRank = Comparator.comparingInt(UnitOfWork::dependencyRank);
        List<Object> ordered = new ArrayList<>(entities);
        ordered.sort(reverse ? byRank.reversed() : byRank);
        return ordered;
    }

    private static int dependencyRank(Object entity) {
        if (entity instanceof business.domainmodel.Order) {
            return 2;
        }
        if (entity instanceof business.domainmodel.Material || entity instanceof business.domainmodel.Address) {
            return 1;
        }
        return 0;
    }

    private void removeContradictoryState(String key) {
        newEntities.remove(key);
        dirtyEntities.remove(key);
        deletedEntities.remove(key);
    }
}
