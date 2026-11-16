package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeUserMapper;
import cz.vsb.catering.business.fake.Fixtures;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private final FakeUserMapper users = new FakeUserMapper();
    private final AuthService service = new AuthService(users, new DirectTransactionRunner());

    { users.add(Fixtures.user("ehorakova", Role.WORKER)); }

    @Test void loginOk() {
        User u = service.login(" ehorakova ", "heslo123");
        assertEquals("ehorakova", u.getUsername());
        assertNotNull(u.getId());
    }

    @Test void wrongPasswordOrUnknownUser() {
        assertThrows(ValidationException.class, () -> service.login("ehorakova", "spatne"));
        assertThrows(ValidationException.class, () -> service.login("ehorakova", null));
        assertThrows(ValidationException.class, () -> service.login("nikdo", "heslo123"));
        assertThrows(ValidationException.class, () -> service.login(null, "heslo123"));
    }

    @Test void findActiveUserRereadsState() {
        long id = users.add(Fixtures.user("x", Role.MANAGER));
        assertTrue(service.findActiveUser(id).isPresent());
        users.stored(id).setActive(false);
        assertTrue(service.findActiveUser(id).isEmpty());
        assertTrue(service.findActiveUser(9999).isEmpty());
    }

    @Test void inactiveUserIsRejected() {
        User inactive = Fixtures.user("old", Role.WORKER);
        inactive.setActive(false);
        users.add(inactive);
        ValidationException e = assertThrows(ValidationException.class, () -> service.login("old", "heslo123"));
        assertTrue(e.getMessage().contains("deaktivován"));
    }
}
