package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.TransactionRunner;
import cz.vsb.catering.business.port.UserMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Facade for UC16 Spravovat uživatele a oprávnění (admin mode, UC17). */
public class UserService {
    static final String LAST_ADMIN = "Systém musí mít alespoň jednoho aktivního administrátora.";

    private final UserMapper users;
    private final TransactionRunner tx;

    public UserService(UserMapper users, TransactionRunner tx) {
        this.users = users;
        this.tx = tx;
    }

    /** UC16 step 2: only an administrator may list and change users. */
    public List<User> findAllUsers(User admin) {
        requireAdmin(admin);
        return tx.inTransaction(users::findAll);
    }

    /** UC16 steps 4-5. */
    public User createUser(User admin, String username, String fullName, String email, String password, Set<Role> roles) {
        requireAdmin(admin);
        User user = User.create(username, fullName, email, password, roles);
        return tx.inTransaction(() -> {
            if (users.findByUsername(user.getUsername()).isPresent()) {
                String msg = "Uživatel „" + user.getUsername() + "“ již existuje.";
                throw new ValidationException(msg, Map.of("username", msg));
            }
            users.insert(user);
            return user;
        });
    }

    /** UC16 steps 6-7. newPassword null or blank keeps the password. At least one active admin must remain (7.1). */
    public User updateUser(User admin, long userId, Set<Role> roles, boolean active, String newPassword) {
        requireAdmin(admin);
        return tx.inTransaction(() -> {
            List<User> all = users.findAll();
            User user = all.stream().filter(u -> u.getId() == userId).findFirst()
                    .orElseThrow(() -> new ValidationException("Uživatel neexistuje."));
            user.changeRoles(roles);
            user.setActive(active);
            if (newPassword != null && !newPassword.isEmpty()) user.changePassword(newPassword);
            // shortcut: the last-admin count is not locked; two admins demoting each other at the same instant could both pass. Lock the admin rows (SELECT … FOR UPDATE) if it matters.
            if (all.stream().noneMatch(User::isActiveAdmin))
                throw new ValidationException(LAST_ADMIN, Map.of("roles", LAST_ADMIN));
            users.update(user);
            return user;
        });
    }

    /** Checks the fresh row, not the caller's object, which may be stale (deactivated or demoted since login). */
    private void requireAdmin(User admin) {
        boolean ok = admin != null && tx.inTransaction(() -> users.findById(admin.getId())).filter(User::isActiveAdmin).isPresent();
        if (!ok) throw new ValidationException("Tato funkce je dostupná jen administrátorovi.");
    }
}
