package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {
    @Test void createTrimsAndHashes() {
        User u = User.create(" jnovak ", " Jan Novák ", " jan@firma.cz ", "heslo123", Set.of(Role.WORKER));
        assertEquals("jnovak", u.getUsername());
        assertEquals("Jan Novák", u.getFullName());
        assertEquals("jan@firma.cz", u.getEmail());
        assertTrue(PasswordHasher.matches("heslo123", u.getPasswordHash()));
        assertTrue(u.isActive());
        assertTrue(u.checkPassword("heslo123"));
        assertFalse(u.checkPassword("jineheslo"));
        assertFalse(u.isActiveAdmin());
    }

    @Test void createReportsFieldErrors() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> User.create("", null, "spatny-email", "kratke", Set.of()));
        assertEquals(Set.of("username", "fullName", "email", "password", "roles"), e.getFieldErrors().keySet());
        e = assertThrows(ValidationException.class,
                () -> User.create("x".repeat(31), "A", "a@b.cz", "heslo123", null));
        assertEquals(Set.of("username", "roles"), e.getFieldErrors().keySet());
    }

    @Test void changePassword() {
        User u = User.create("a", "A", "a@b.cz", "heslo123", Set.of(Role.WORKER));
        String old = u.getPasswordHash();
        ValidationException e = assertThrows(ValidationException.class, () -> u.changePassword("1234567"));
        assertTrue(e.getFieldErrors().containsKey("password"));
        assertEquals(old, u.getPasswordHash());
        u.changePassword("noveheslo");
        assertTrue(u.checkPassword("noveheslo"));
    }

    @Test void changeRolesAndActiveAdmin() {
        User u = User.create("a", "A", "a@b.cz", "heslo123", Set.of(Role.WORKER));
        assertThrows(ValidationException.class, () -> u.changeRoles(Set.of()));
        assertEquals(Set.of(Role.WORKER), u.getRoles());
        u.changeRoles(Set.of(Role.ADMIN, Role.MANAGER));
        assertEquals(Set.of(Role.ADMIN, Role.MANAGER), u.getRoles());
        assertTrue(u.hasRole(Role.ADMIN));
        assertTrue(u.isActiveAdmin());
        u.setActive(false);
        assertFalse(u.isActiveAdmin());
    }

    @Test void nameAndEmailLengthLimits() {
        String email254 = "a".repeat(254 - "@b.cz".length()) + "@b.cz";
        assertDoesNotThrow(() -> User.create("u", "x".repeat(100), email254, "heslo123", Set.of(Role.WORKER)));
        ValidationException e = assertThrows(ValidationException.class,
                () -> User.create("u", "x".repeat(101), email254 + "z", "heslo123", Set.of(Role.WORKER)));
        assertEquals(Set.of("fullName", "email"), e.getFieldErrors().keySet());
    }
}
