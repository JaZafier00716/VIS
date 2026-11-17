package business.domainmodel;

import lombok.Getter;

import java.time.Instant;

/**
 * CV08 Layer Supertype (cv08 p.5–6): the common part of every domain entity – the Identity Field, when it was
 * created and when it was last changed. Only attributes and behaviour shared by all entities belong here.
 * The timestamps live in the domain model only; they are not persisted (cv08 p.18: no new persistence).
 */
@Getter
public abstract class BaseEntity {
    private final String id;
    private final Instant createdAt;
    private Instant updatedAt;

    protected BaseEntity(String id) {
        this(id, Instant.now());
    }

    protected BaseEntity(String id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = this.createdAt;
    }

    /** Records a change of the entity (cv08 p.6 step 3). */
    public void markUpdated() {
        updatedAt = Instant.now();
    }
}
