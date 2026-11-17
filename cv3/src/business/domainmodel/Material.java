package business.domainmodel;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class Material extends BaseEntity {
    private final String name;
    // M:N with Finish; persisted as association records, not inside the material record.
    private final List<Finish> finishes = new ArrayList<>();

    @Builder
    private Material(String materialId, String name) {
        super(materialId);
        this.name = name;
    }

    public String getMaterialId() {
        return getId();
    }

    public List<Finish> getFinishes() {
        return Collections.unmodifiableList(finishes);
    }

    public boolean addFinish(Finish finish) {
        if (finish == null) {
            throw new IllegalArgumentException("Povrchová úprava je povinná.");
        }
        if (hasFinish(finish.getFinishId())) {
            return false;
        }
        finishes.add(finish);
        return true;
    }

    public boolean removeFinish(String finishId) {
        return finishes.removeIf(f -> f.getFinishId().equals(finishId));
    }

    public boolean hasFinish(String finishId) {
        return finishes.stream().anyMatch(f -> f.getFinishId().equals(finishId));
    }
}
