package business.domainmodel;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Finish extends BaseEntity {
    private final String name;

    @Builder
    private Finish(String finishId, String name) {
        super(finishId);
        this.name = name;
    }

    public String getFinishId() {
        return getId();
    }
}
