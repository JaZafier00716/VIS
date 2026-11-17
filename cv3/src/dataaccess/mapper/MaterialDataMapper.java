package dataaccess.mapper;

import business.domainmodel.Material;
import business.repository.MaterialRepository;
import dataaccess.dto.MaterialDto;
import dataaccess.gateway.MaterialFinishGateway;
import dataaccess.gateway.MaterialGateway;
import dataaccess.identity.IdGenerator;
import dataaccess.unitofwork.UnitOfWork;

import java.util.List;
import java.util.Optional;

/**
 * Data Mapper for materials. The material record holds only its own columns; its finishes come from the
 * association table (Association Table Mapping) and are resolved through the finish mapper's Identity Map.
 */
public class MaterialDataMapper implements MaterialRepository {
    private final MaterialGateway gateway;
    private final MaterialFinishGateway materialFinishes;
    private final FinishDataMapper finishes;
    private final UnitOfWork unitOfWork;

    public MaterialDataMapper(MaterialGateway gateway, MaterialFinishGateway materialFinishes,
                              FinishDataMapper finishes, UnitOfWork unitOfWork) {
        this.gateway = gateway;
        this.materialFinishes = materialFinishes;
        this.finishes = finishes;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public String nextId() {
        return IdGenerator.assign(null, "M");
    }

    @Override
    public Optional<Material> findById(String materialId) {
        return unitOfWork.get(Material.class, materialId).or(() -> gateway.findById(materialId).map(this::load));
    }

    @Override
    public List<Material> findAll() {
        return gateway.findAll().stream().map(this::identityOrLoad).toList();
    }

    @Override
    public List<Material> findByFinishId(String finishId) {
        return materialFinishes.findMaterialIds(finishId).stream()
                .map(materialId -> findById(materialId).orElseThrow()).toList();
    }

    @Override
    public void add(Material material) {
        unitOfWork.registerNew(material, material.getMaterialId());
    }

    @Override
    public void update(Material material) {
        unitOfWork.registerDirty(material, material.getMaterialId());
    }

    private Material identityOrLoad(MaterialDto record) {
        return unitOfWork.get(Material.class, record.materialId()).orElseGet(() -> load(record));
    }

    private Material load(MaterialDto record) {
        Material material = MaterialMapper.toDomain(record);
        materialFinishes.findFinishIds(record.materialId())
                .forEach(finishId -> material.addFinish(finishes.findById(finishId).orElseThrow()));
        unitOfWork.remember(material, record.materialId());
        return material;
    }
}
