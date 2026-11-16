package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.PasswordHasher;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeUserMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserServiceTest {
    private final FakeUserMapper users = new FakeUserMapper();
    private final UserService service = new UserService(users, new DirectTransactionRunner());
    private final User admin = Fixtures.user("mkral", Role.ADMIN);
    private final User worker = Fixtures.user("ehorakova", Role.WORKER);
    private final long adminId = users.add(admin);
    private final long workerId = users.add(worker);

    @Test void createUser() {
        User u = service.createUser(admin, "tsvoboda", "Tomáš Svoboda", "ts@catering.cz", "heslo123", Set.of(Role.WORKER));
        assertNotNull(u.getId());
        assertEquals(1, users.inserted.size());
        assertTrue(PasswordHasher.matches("heslo123", users.stored(u.getId()).getPasswordHash()));
        assertEquals(3, service.findAllUsers(admin).size());
    }

    @Test void duplicateUsernameIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.createUser(admin, " ehorakova ", "Eva", "e@catering.cz", "heslo123", Set.of(Role.WORKER)));
        assertTrue(e.getFieldErrors().containsKey("username"));
        assertTrue(users.inserted.isEmpty());
    }

    @Test void invalidFieldsAreRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.createUser(admin, "x", "", "bez-zavinace", "kratke", Set.of()));
        assertEquals(Set.of("fullName", "email", "password", "roles"), e.getFieldErrors().keySet());
        assertTrue(users.inserted.isEmpty());
    }

    @Test void nonAdminIsRejected() {
        assertThrows(ValidationException.class,
                () -> service.createUser(worker, "x", "X", "x@a.cz", "heslo123", Set.of(Role.WORKER)));
        assertThrows(ValidationException.class, () -> service.findAllUsers(worker));
        assertThrows(ValidationException.class, () -> service.findAllUsers(null));
        assertThrows(ValidationException.class, () -> service.updateUser(worker, workerId, Set.of(Role.ADMIN), true, null));
        users.stored(adminId).setActive(false);                      // deactivated in the DB: the fresh row decides (A-06)
        assertThrows(ValidationException.class, () -> service.findAllUsers(admin));
        assertTrue(users.inserted.isEmpty());
        assertTrue(users.updated.isEmpty());
    }

    @Test void updateRolesActiveAndPassword() {
        service.updateUser(admin, workerId, Set.of(Role.WORKER, Role.MANAGER), false, "noveheslo1");
        User stored = users.stored(workerId);
        assertEquals(Set.of(Role.WORKER, Role.MANAGER), stored.getRoles());
        assertFalse(stored.isActive());
        assertTrue(stored.checkPassword("noveheslo1"));
    }

    @Test void blankPasswordKeepsOldOne() {
        service.updateUser(admin, workerId, Set.of(Role.WORKER), true, "");
        assertTrue(users.stored(workerId).checkPassword("heslo123"));
        service.updateUser(admin, workerId, Set.of(Role.WORKER), true, null);
        assertTrue(users.stored(workerId).checkPassword("heslo123"));
    }

    @Test void shortPasswordIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.updateUser(admin, workerId, Set.of(Role.WORKER), true, "kratke"));
        assertTrue(e.getFieldErrors().containsKey("password"));
        assertTrue(users.updated.isEmpty());
    }

    @Test void unknownUserAndEmptyRoles() {
        assertThrows(ValidationException.class, () -> service.updateUser(admin, 99, Set.of(Role.WORKER), true, null));
        assertThrows(ValidationException.class, () -> service.updateUser(admin, workerId, Set.of(), true, null));
        assertTrue(users.updated.isEmpty());
    }

    @Test void lastActiveAdminCannotBeRemovedUntilAnotherAdminExists() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.updateUser(admin, adminId, Set.of(Role.WORKER), true, null));
        assertEquals(UserService.LAST_ADMIN, e.getMessage());
        assertThrows(ValidationException.class, () -> service.updateUser(admin, adminId, Set.of(Role.ADMIN), false, null));
        assertTrue(users.updated.isEmpty());
        assertTrue(users.stored(adminId).isActiveAdmin());

        service.updateUser(admin, workerId, Set.of(Role.WORKER, Role.ADMIN), true, null);   // 7.4
        service.updateUser(admin, adminId, Set.of(Role.WORKER), true, null);                // 7.5
        assertFalse(users.stored(adminId).hasRole(Role.ADMIN));
        assertTrue(users.stored(workerId).isActiveAdmin());
    }

    @Test void inactiveAdminDoesNotCount() {
        User other = Fixtures.user("old", Role.ADMIN);
        other.setActive(false);
        users.add(other);
        assertThrows(ValidationException.class, () -> service.updateUser(admin, adminId, Set.of(Role.WORKER), true, null));
    }
}
