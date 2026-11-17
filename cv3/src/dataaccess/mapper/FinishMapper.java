package dataaccess.mapper;

import business.domainmodel.Finish;
import dataaccess.dto.FinishDto;

public final class FinishMapper {
    private FinishMapper() {}

    public static Finish toDomain(FinishDto dto) {
        return Finish.builder().finishId(dto.finishId()).name(dto.name()).build();
    }

    public static FinishDto toDto(Finish finish) {
        return new FinishDto(finish.getFinishId(), finish.getName());
    }
}
