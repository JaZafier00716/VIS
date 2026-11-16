package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.User;
import java.util.List;
import java.util.Optional;

/** Data Mapper for users, roles and user_roles. */
public interface UserMapper {
    List<User> findAll();

    Optional<User> findById(long userId);

    Optional<User> findByUsername(String username);

    /** Inserts the user with its roles and sets the id. */
    void insert(User user);

    /** Updates name, e-mail, password hash, active flag and replaces the roles. */
    void update(User user);
}
