package business.repository;

import business.domainmodel.Finish;

import java.util.Optional;

public interface FinishRepository {
    String nextId();

    Optional<Finish> findById(String finishId);

    void add(Finish finish);
}
