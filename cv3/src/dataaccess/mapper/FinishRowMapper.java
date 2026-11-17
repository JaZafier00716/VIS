package dataaccess.mapper;

import dataaccess.dto.FinishDto;
import dataaccess.row.FinishRow;

public final class FinishRowMapper {
    private FinishRowMapper() {}

    public static FinishRow toRow(FinishDto dto) {
        return new FinishRow(dto.finishId(), dto.name());
    }

    public static FinishDto toDto(FinishRow row) {
        return new FinishDto(row.finishId(), row.name());
    }
}
