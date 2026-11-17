package dataaccess.identity;

import business.domainmodel.BaseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Identity Map: one in-memory object per (type, ID) within a unit of work. */
public class IdentityMap {
    private final Map<String, Object> entities = new HashMap<>();

    /**
     * The key uses the root of the inheritance hierarchy (CV07): a {@code PersonCustomer} is registered and found
     * as {@code Customer#id}, so one ID maps to one instance whatever subtype is asked for. Different hierarchies
     * stay apart: {@code Customer#1} and {@code Order#1} are two keys.
     *
     * <p>The root is the highest superclass below {@code Object} or below the CV08 Layer Supertype
     * {@link BaseEntity}; otherwise every entity would share one key space and a customer and an order with the
     * same ID would collide.
     */
    public String keyFor(Class<?> type, String id) {
        return rootType(type).getName() + "#" + id;
    }

    private static Class<?> rootType(Class<?> type) {
        Class<?> root = type;
        while (root.getSuperclass() != null && root.getSuperclass() != Object.class
                && root.getSuperclass() != BaseEntity.class) {
            root = root.getSuperclass();
        }
        return root;
    }

    public void put(Object entity, String id) {
        entities.put(keyFor(entity.getClass(), id), entity);
    }

    public <T> Optional<T> get(Class<T> type, String id) {
        Object entity = entities.get(keyFor(type, id));
        return type.isInstance(entity) ? Optional.of(type.cast(entity)) : Optional.empty();
    }

    public boolean contains(Class<?> type, String id) {
        return entities.containsKey(keyFor(type, id));
    }

    public void remove(Object entity, String id) {
        entities.remove(keyFor(entity.getClass(), id));
    }

    public void clear() {
        entities.clear();
    }

    public int size() {
        return entities.size();
    }
}
