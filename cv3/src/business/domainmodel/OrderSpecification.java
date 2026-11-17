package business.domainmodel;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Value object for the ordered dimensions (the CV06 "Dimensions" example).
 * Embedded Value: it has no ID and is stored as columns of the owning order record.
 */
@Getter
@EqualsAndHashCode
@ToString
public class OrderSpecification {
    private final double width;
    private final double height;
    private final double depth;
    private final String designNotes;

    @Builder
    public OrderSpecification(double width, double height, double depth, String designNotes) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.designNotes = designNotes;
        validate();
    }

    public void validate() {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Rozměry musí být větší než 0.");
        }
    }
}
