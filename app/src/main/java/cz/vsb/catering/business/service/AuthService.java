package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.PasswordHasher;
import cz.vsb.catering.business.domain.AccessPolicy;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.TransactionRunner;
import cz.vsb.catering.business.port.UserMapper;
import java.util.Optional;

/** Facade for UC01 Přihlásit se. */
public class AuthService {
    /** Checked for an unknown username so the response time does not reveal which usernames exist. */
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-password");

    private final UserMapper users;
    private final TransactionRunner tx;

    public AuthService(UserMapper users, TransactionRunner tx) {
        this.users = users;
        this.tx = tx;
    }

    public User login(String username, String password) {
        User user = tx.inTransaction(() -> users.findByUsername(username == null ? "" : username.trim()).orElse(null));
        if (user == null) PasswordHasher.matches(password == null ? "" : password, DUMMY_HASH);
        if (user == null || password == null || !user.checkPassword(password))
            throw new ValidationException("Neplatné uživatelské jméno nebo heslo.");
        if (!user.isActive()) throw new ValidationException("Účet je deaktivován, kontaktujte administrátora.");
        return user;
    }

    /** UC01 in the desktop (A-17): WORKER opens the worker UI, ADMIN the admin mode; a MANAGER-only account
     *  belongs to the web overview. */
    public User loginDesktop(String username, String password) {
        User user = login(username, password);
        if (!AccessPolicy.canLoginToDesktop(user))
            throw new ValidationException("Účet manažera má přístup jen k webovému přehledu (adresa /manager).");
        return user;
    }

    /** UC15: the current state of a logged-in user (empty when gone or deactivated), re-read on every request. */
    public Optional<User> findActiveUser(long userId) {
        return tx.inTransaction(() -> users.findById(userId)).filter(User::isActive);
    }
}
