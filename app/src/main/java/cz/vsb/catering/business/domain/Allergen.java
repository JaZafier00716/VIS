package cz.vsb.catering.business.domain;

/** Value Object: one of the 14 EU allergens (id 1–14). */
public record Allergen(int id, String name) {
    @Override public String toString() { return id + " " + name; }
}
