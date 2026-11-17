package business.repository;

import business.domainmodel.Material;

import java.util.List;
import java.util.Optional;

/** Data Mapper for materials, including their finishes (Association Table Mapping). */
public interface MaterialRepository {
    String nextId();

    Optional<Material> findById(String materialId);

    List<Material> findAll();

    List<Material> findByFinishId(String finishId);

    void add(Material material);

    void update(Material material);
}
