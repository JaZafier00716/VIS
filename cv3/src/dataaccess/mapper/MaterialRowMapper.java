package dataaccess.mapper;

import dataaccess.dto.MaterialDto;
import dataaccess.row.MaterialRow;

public final class MaterialRowMapper {
    private MaterialRowMapper() {}

    public static MaterialRow toRow(MaterialDto dto) {
        return new MaterialRow(dto.materialId(), dto.name());
    }

    public static MaterialDto toDto(MaterialRow row) {
        return new MaterialDto(row.materialId(), row.name());
    }
}
