package business.service;

import business.domainmodel.Finish;
import business.domainmodel.Material;
import business.repository.FinishRepository;
import business.repository.MaterialRepository;
import business.repository.UnitOfWorkControl;

import java.util.List;

public class MaterialService {
    private final MaterialRepository materials;
    private final FinishRepository finishes;
    private final UnitOfWorkControl unitOfWork;

    public MaterialService(MaterialRepository materials, FinishRepository finishes, UnitOfWorkControl unitOfWork) {
        this.materials = materials;
        this.finishes = finishes;
        this.unitOfWork = unitOfWork;
    }

    public Material createMaterial(String id, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Název materiálu je povinný.");
        String materialId = id == null || id.isBlank() ? materials.nextId() : id;
        Material material = Material.builder().materialId(materialId).name(name).build();
        materials.add(material);
        commit();
        return material;
    }

    public Material findMaterial(String id) {
        return materials.findById(id).orElseThrow(() -> new IllegalArgumentException("Materiál neexistuje."));
    }

    public List<Material> findAllMaterials() {
        return materials.findAll();
    }

    public Finish createFinish(String id, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Název povrchové úpravy je povinný.");
        String finishId = id == null || id.isBlank() ? finishes.nextId() : id;
        Finish finish = Finish.builder().finishId(finishId).name(name).build();
        finishes.add(finish);
        commit();
        return finish;
    }

    public Finish findFinish(String id) {
        return finishes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Povrchová úprava " + id + " neexistuje."));
    }

    /** @return false when the material already had the finish (no duplicate association is stored). */
    public boolean addFinish(String materialId, String finishId) {
        Material material = findMaterial(materialId);
        boolean added = material.addFinish(findFinish(finishId));
        if (added) {
            materials.update(material);
            commit();
        }
        return added;
    }

    public boolean removeFinish(String materialId, String finishId) {
        Material material = findMaterial(materialId);
        boolean removed = material.removeFinish(finishId);
        if (removed) {
            materials.update(material);
            commit();
        }
        return removed;
    }

    public List<Finish> findFinishes(String materialId) {
        return findMaterial(materialId).getFinishes();
    }

    public List<Material> findMaterialsWithFinish(String finishId) {
        findFinish(finishId);
        return materials.findByFinishId(finishId);
    }

    private void commit() {
        try {
            unitOfWork.commit();
        } catch (RuntimeException e) {
            unitOfWork.rollback();
            throw e;
        }
    }
}
