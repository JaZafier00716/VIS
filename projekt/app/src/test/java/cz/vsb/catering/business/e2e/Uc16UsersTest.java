package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.PasswordHasher;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** UC16 Spravovat uživatele a oprávnění: main flow, alternatives 4.1 (invalid data, duplicate username,
 *  short new password) and 7.1-7.5 (last active administrator), plus UC01 login of a deactivated user. */
class Uc16UsersTest extends E2eBaseTest {

    @Test
    void onlyAdministratorListsUsers() {
        assertEquals(count("SELECT COUNT(*) FROM users"), users.findAllUsers(admin).size());             // step 2
        User worker = auth.login("pnovak", PASSWORD);
        assertThrows(ValidationException.class, () -> users.findAllUsers(worker));
        assertThrows(ValidationException.class, () -> users.createUser(worker, "e2e_x", "X", "x@example.com",
                "heslo1234", Set.of(Role.WORKER)));
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE username = 'e2e_x'"));
    }

    @Test
    void mainFlowCreatesUserAndUpdatesRolesPasswordAndActiveFlag() throws Exception {
        User created = users.createUser(admin, " e2e_novak ", "Jan Novák", "jan.novak@example.com",       // steps 3-5
                "tajneheslo1", EnumSet.of(Role.WORKER, Role.MANAGER));
        createdUsers.add(created.getId());
        Map<String, Object> u = row("SELECT * FROM users WHERE user_id = ?", created.getId());
        assertEquals("e2e_novak", u.get("USERNAME"));
        assertEquals("Jan Novák", u.get("FULL_NAME"));
        assertEquals("jan.novak@example.com", u.get("EMAIL"));
        assertEquals("Y", u.get("ACTIVE"));
        String firstHash = (String) u.get("PASSWORD_HASH");
        assertTrue(PasswordHasher.matches("tajneheslo1", firstHash), "PBKDF2 hash stored");
        assertEquals(Set.of("MANAGER", "WORKER"), roles(created.getId()));
        assertEquals(created.getId(), auth.login("e2e_novak", "tajneheslo1").getId());

        users.updateUser(admin, created.getId(), EnumSet.of(Role.ADMIN), true, null);                 // steps 6-7
        assertEquals(Set.of("ADMIN"), roles(created.getId()));
        assertEquals(firstHash, passwordHash(created.getId()), "blank password keeps the hash");

        users.updateUser(admin, created.getId(), EnumSet.of(Role.WORKER), true, "noveheslo99");         // forgotten password
        String secondHash = passwordHash(created.getId());
        assertTrue(PasswordHasher.matches("noveheslo99", secondHash));
        assertEquals(Set.of("WORKER"), roles(created.getId()));
        assertNotNull(auth.login("e2e_novak", "noveheslo99"));
        assertThrows(ValidationException.class, () -> auth.login("e2e_novak", "tajneheslo1"));

        users.updateUser(admin, created.getId(), EnumSet.of(Role.WORKER), false, "");                  // deactivate
        assertEquals("N", row("SELECT active FROM users WHERE user_id = ?", created.getId()).get("ACTIVE"));
        ValidationException e = assertThrows(ValidationException.class, () -> auth.login("e2e_novak", "noveheslo99"));
        assertTrue(e.getMessage().contains("deaktivován"), e.getMessage());
        assertEquals(secondHash, passwordHash(created.getId()));
    }

    @Test
    void alt41InvalidDataOrDuplicateUsernameIsNotStored() {
        long before = count("SELECT COUNT(*) FROM users");
        ValidationException e = assertThrows(ValidationException.class,
                () -> users.createUser(admin, "mkral", "Duplicitní", "dup@example.com", "heslo1234", Set.of(Role.WORKER)));
        assertTrue(e.getFieldErrors().containsKey("username"));
        e = assertThrows(ValidationException.class,
                () -> users.createUser(admin, "", "", "neni-email", "kratke", Set.of()));
        assertEquals(Set.of("username", "fullName", "email", "password", "roles"), e.getFieldErrors().keySet());
        assertEquals(before, count("SELECT COUNT(*) FROM users"));
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE username = 'mkral'"));
    }

    @Test
    void adminDeactivatedAfterLoginIsRefused() {                      // A-06: the caller's object is stale
        User created = users.createUser(admin, "e2e_stale", "Stará Správa", "stale@example.com", "dlouheheslo",
                EnumSet.of(Role.ADMIN));
        createdUsers.add(created.getId());
        User stale = auth.login("e2e_stale", "dlouheheslo");
        assertEquals(count("SELECT COUNT(*) FROM users"), users.findAllUsers(stale).size());
        users.updateUser(admin, created.getId(), EnumSet.of(Role.ADMIN), false, null);
        assertThrows(ValidationException.class, () -> users.findAllUsers(stale));
    }

    @Test
    void alt41ShortNewPasswordOrNoRoleKeepsUserUnchanged() {
        User created = users.createUser(admin, "e2e_heslo", "Petra Heslová", "petra@example.com", "dlouheheslo",
                Set.of(Role.WORKER));
        createdUsers.add(created.getId());
        Map<String, Object> before = row("SELECT * FROM users WHERE user_id = ?", created.getId());

        ValidationException e = assertThrows(ValidationException.class,
                () -> users.updateUser(admin, created.getId(), Set.of(Role.MANAGER), true, "kratke"));
        assertTrue(e.getFieldErrors().containsKey("password"));
        assertThrows(ValidationException.class, () -> users.updateUser(admin, created.getId(), Set.of(), true, null));
        assertEquals(before, row("SELECT * FROM users WHERE user_id = ?", created.getId()));
        assertEquals(Set.of("WORKER"), roles(created.getId()));
    }

    /** The seed has three active admins (mkral, jzamostny, kpokorny). The two others temporarily lose ADMIN;
     *  their rows are restored exactly in finally with plain JDBC. */
    @Test
    void alt71LastActiveAdministratorCannotBeRemoved() {
        List<Long> admins = rows("SELECT user_id FROM user_roles WHERE TRIM(role_code) = 'ADMIN' ORDER BY user_id")
                .stream().map(r -> (Long) r.get("USER_ID")).toList();
        assertTrue(admins.contains(admin.getId()));
        List<Map<String, Object>> savedRoles = rows("SELECT user_id, role_code FROM user_roles WHERE user_id IN ("
                + admins.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        List<Map<String, Object>> savedUsers = rows("SELECT * FROM users WHERE user_id IN ("
                + admins.stream().map(String::valueOf).collect(Collectors.joining(",")) + ") ORDER BY user_id");
        try {
            for (long id : admins)
                if (id != admin.getId()) users.updateUser(admin, id, EnumSet.of(Role.WORKER), true, null);
            assertEquals(List.of(admin.getId()), activeAdmins());

            Map<String, Object> mkral = row("SELECT * FROM users WHERE user_id = ?", admin.getId());
            Set<String> mkralRoles = roles(admin.getId());
            ValidationException e = assertThrows(ValidationException.class,                         // 7.1-7.3
                    () -> users.updateUser(admin, admin.getId(), EnumSet.of(Role.MANAGER), true, null));
            assertEquals("Systém musí mít alespoň jednoho aktivního administrátora.", e.getMessage());
            assertThrows(ValidationException.class,
                    () -> users.updateUser(admin, admin.getId(), EnumSet.of(Role.ADMIN, Role.MANAGER), false, null));
            assertEquals(mkral, row("SELECT * FROM users WHERE user_id = ?", admin.getId()));
            assertEquals(mkralRoles, roles(admin.getId()));

            User helper = users.createUser(admin, "e2e_admin", "Druhý Admin", "admin2@example.com", "heslo12345",
                    Set.of(Role.WORKER));
            createdUsers.add(helper.getId());
            users.updateUser(admin, helper.getId(), EnumSet.of(Role.ADMIN), true, null);              // 7.4
            users.updateUser(admin, admin.getId(), EnumSet.of(Role.MANAGER), true, null);             // 7.5
            assertEquals(Set.of("MANAGER"), roles(admin.getId()));
            assertEquals(List.of(helper.getId()), activeAdmins());
        } finally {
            restore(admins, savedRoles, savedUsers);
        }
        assertEquals(savedRoles.size(), count("SELECT COUNT(*) FROM user_roles WHERE user_id IN ("
                + admins.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")"));
        assertEquals(admins, activeAdmins().stream().filter(admins::contains).toList());
    }

    private static void restore(List<Long> ids, List<Map<String, Object>> savedRoles, List<Map<String, Object>> savedUsers) {
        for (long id : ids) exec("DELETE FROM user_roles WHERE user_id = ?", id);
        for (Map<String, Object> r : savedRoles)
            exec("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", r.get("USER_ID"), r.get("ROLE_CODE"));
        for (Map<String, Object> u : savedUsers)
            exec("UPDATE users SET active = ?, password_hash = ? WHERE user_id = ?",
                    u.get("ACTIVE"), u.get("PASSWORD_HASH"), u.get("USER_ID"));
    }

    private static List<Long> activeAdmins() {
        return rows("""
                SELECT u.user_id FROM users u JOIN user_roles r ON r.user_id = u.user_id
                WHERE TRIM(r.role_code) = 'ADMIN' AND u.active = 'Y' ORDER BY u.user_id""")
                .stream().map(r -> (Long) r.get("USER_ID")).toList();
    }

    private static Set<String> roles(long userId) {
        return rows("SELECT role_code FROM user_roles WHERE user_id = ?", userId).stream()
                .map(r -> (String) r.get("ROLE_CODE")).collect(Collectors.toSet());
    }

    private static String passwordHash(long userId) {
        return (String) row("SELECT password_hash FROM users WHERE user_id = ?", userId).get("PASSWORD_HASH");
    }
}
