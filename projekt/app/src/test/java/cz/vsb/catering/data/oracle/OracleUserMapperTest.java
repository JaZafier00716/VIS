package cz.vsb.catering.data.oracle;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OracleUserMapperTest {
    private static final String USERNAME = "test_mapper_user";
    private static Database db;
    private final OracleUserMapper users = new OracleUserMapper();

    @BeforeAll
    static void setUp() { db = TestDatabase.init(); }

    @AfterAll
    static void tearDown() {
        db.inTransaction(() -> Jdbc.update("DELETE FROM users WHERE username = ?", USERNAME));
    }

    @Test
    void seedUsersHaveTrimmedRoles() {
        List<User> all = db.inTransaction(users::findAll);
        assertTrue(all.size() >= 10);
        User admin = db.inTransaction(() -> users.findById(1).orElseThrow());
        assertEquals(EnumSet.of(Role.ADMIN, Role.MANAGER), admin.getRoles());
        assertTrue(admin.checkPassword("heslo123"));
        assertEquals(admin.getId(), db.inTransaction(() -> users.findByUsername(admin.getUsername()).orElseThrow()).getId());
        assertTrue(db.inTransaction(() -> users.findByUsername("nobody-here")).isEmpty());
    }

    @Test
    void insertAndUpdateReplaceRoles() {
        User user = User.create(USERNAME, "Testovací Uživatel", "test.mapper@example.com", "tajneheslo", Set.of(Role.WORKER));
        db.inTransaction(() -> users.insert(user));
        assertNotNull(user.getId());

        User loaded = db.inTransaction(() -> users.findByUsername(USERNAME).orElseThrow());
        assertEquals(user.getId(), loaded.getId());
        assertEquals("Testovací Uživatel", loaded.getFullName());
        assertEquals(EnumSet.of(Role.WORKER), loaded.getRoles());
        assertTrue(loaded.checkPassword("tajneheslo"));
        assertTrue(loaded.isActive());

        loaded.changeRoles(Set.of(Role.ADMIN, Role.MANAGER));
        loaded.changePassword("noveheslo1");
        loaded.setActive(false);
        db.inTransaction(() -> users.update(loaded));
        User updated = db.inTransaction(() -> users.findById(user.getId()).orElseThrow());
        assertEquals(EnumSet.of(Role.ADMIN, Role.MANAGER), updated.getRoles());
        assertTrue(updated.checkPassword("noveheslo1"));
        assertFalse(updated.isActive());
    }
}
