package cz.vsb.catering.business.domain;

/** Layer Supertype: every entity has a technical id assigned by the data layer. */
public abstract class DomainObject {
    private Long id;

    public Long getId() { return id; }

    /** Called by a mapper after INSERT (Identity Field). */
    public void setId(Long id) { this.id = id; }

    public boolean isNew() { return id == null; }
}
