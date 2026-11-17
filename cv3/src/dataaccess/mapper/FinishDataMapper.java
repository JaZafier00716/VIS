package dataaccess.mapper;

import business.domainmodel.Finish;
import business.repository.FinishRepository;
import dataaccess.gateway.FinishGateway;
import dataaccess.identity.IdGenerator;
import dataaccess.unitofwork.UnitOfWork;

import java.util.Optional;

/** Data Mapper for finishes, with the Identity Map check. */
public class FinishDataMapper implements FinishRepository {
    private final FinishGateway gateway;
    private final UnitOfWork unitOfWork;

    public FinishDataMapper(FinishGateway gateway, UnitOfWork unitOfWork) {
        this.gateway = gateway;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public String nextId() {
        return IdGenerator.assign(null, "F");
    }

    @Override
    public Optional<Finish> findById(String finishId) {
        return unitOfWork.get(Finish.class, finishId).or(() -> gateway.findById(finishId).map(record -> {
            Finish finish = FinishMapper.toDomain(record);
            unitOfWork.remember(finish, finishId);
            return finish;
        }));
    }

    @Override
    public void add(Finish finish) {
        unitOfWork.registerNew(finish, finish.getFinishId());
    }
}
