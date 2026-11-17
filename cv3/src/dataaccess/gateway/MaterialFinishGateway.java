package dataaccess.gateway;

import java.util.Set;

/**
 * Table Data Gateway used by the Data Mapper (table material_finishes; DTOs in and out, no state).
 * Association Table Mapping for the M:N relation Material <-> Finish.
 */
public interface MaterialFinishGateway {
    Set<String> findFinishIds(String materialId);

    Set<String> findMaterialIds(String finishId);

    /** Makes the stored associations of the material equal to {@code finishIds}. */
    void synchronize(String materialId, Set<String> finishIds);
}
