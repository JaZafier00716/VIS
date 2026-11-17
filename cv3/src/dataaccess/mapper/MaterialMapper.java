package dataaccess.mapper;

import business.domainmodel.Finish;
import business.domainmodel.Material;
import dataaccess.dto.MaterialDto;

import java.util.Set;
import java.util.TreeSet;

/**
 * The material record holds only its own columns; finishes are stored separately as association
 * records (Association Table Mapping) and attached by the service.
 */
public final class MaterialMapper {
    private MaterialMapper() {}

    public static Material toDomain(MaterialDto dto) {
        return Material.builder().materialId(dto.materialId()).name(dto.name()).build();
    }

    public static MaterialDto toDto(Material material) {
        return new MaterialDto(material.getMaterialId(), material.getName());
    }

    public static Set<String> finishIds(Material material) {
        Set<String> ids = new TreeSet<>();
        for (Finish finish : material.getFinishes()) {
            ids.add(finish.getFinishId());
        }
        return ids;
    }
}
