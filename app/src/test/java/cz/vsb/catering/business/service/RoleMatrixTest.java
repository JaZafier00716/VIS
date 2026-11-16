package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeCustomerMapper;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeProjectNodeMapper;
import cz.vsb.catering.business.fake.FakeUserMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A-17: roles are additive. Desktop worker UI needs WORKER, admin mode needs ADMIN (ADMIN alone gets only that),
 *  the desktop refuses a MANAGER-only account, the manager overview shows only the projects of the manager's workers. */
class RoleMatrixTest {
    private static final Role W = Role.WORKER, A = Role.ADMIN, M = Role.MANAGER;
    private final FakeUserMapper users = new FakeUserMapper();
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final FakeCustomerMapper customers = new FakeCustomerMapper();
    private final DirectTransactionRunner tx = new DirectTransactionRunner();
    private final AuthService auth = new AuthService(users, tx);
    private final UserService userService = new UserService(users, tx);
    private final ProjectService projectService = new ProjectService(projects, customers, new FakeProjectNodeMapper(), tx, Fixtures.CLOCK);
    private final MenuService menuService = new MenuService(new FakeMenuMapper(), projects, new FakeProjectNodeMapper(),
            new cz.vsb.catering.business.fake.FakeProductMapper(), tx, Fixtures.CLOCK);

    /** The seven non-empty role sets and what each unlocks: {desktop login, worker actions, admin mode, manager web}. */
    private static final Object[][] MATRIX = {
            {Set.of(W), true, true, false, false},
            {Set.of(A), true, false, true, false},
            {Set.of(M), false, false, false, true},
            {Set.of(W, A), true, true, true, false},
            {Set.of(W, M), true, true, false, true},
            {Set.of(A, M), true, false, true, true},
            {Set.of(W, A, M), true, true, true, true}};

    @SuppressWarnings("unchecked")
    @Test void everyRoleCombinationUnlocksOnlyItsAreas() {
        customers.add(11, "Jana Nováková");
        int i = 0;
        for (Object[] row : MATRIX) {
            Set<Role> roles = (Set<Role>) row[0];
            User user = Fixtures.user("u" + i++, roles.toArray(Role[]::new));
            user.setId((long) (100 + i));
            users.add(user);                                           // the fake assigns its own id; re-read below
            User stored = users.findByUsername(user.getUsername()).orElseThrow();
            String name = roles.toString();

            if ((boolean) row[1]) assertEquals(user.getUsername(), auth.loginDesktop(user.getUsername(), "heslo123").getUsername(), name);
            else assertTrue(assertThrows(ValidationException.class, () -> auth.loginDesktop(user.getUsername(), "heslo123"))
                    .getMessage().contains("webovému přehledu"), name);

            expect((boolean) row[2], () -> projectService.createProject("P", 11L, LocalDate.of(2026, 12, 1), "Ostrava", 10, stored), name);
            expect((boolean) row[2], () -> menuService.saveRevision(Fixtures.validRevision(1, 1, cz.vsb.catering.business.domain.RevisionState.WORKING, true), null, stored), name);
            expect((boolean) row[3], () -> userService.findAllUsers(stored), name);
            expect((boolean) row[4], () -> projectService.searchManagedProjects(stored, null), name);
        }
    }

    private static void expect(boolean allowed, Runnable action, String name) {
        if (allowed) {
            try {
                action.run();
            } catch (ValidationException e) {                           // business errors other than the role refusal are fine here
                assertTrue(!e.getMessage().contains("dostupná jen"), name + ": " + e.getMessage());
            } catch (RuntimeException ignored) { /* the fake menu has no data: only the role gate matters */ }
        } else {
            assertTrue(assertThrows(ValidationException.class, action::run).getMessage().contains("dostupná jen"), name);
        }
    }

    @Test void managerSeesOnlyProjectsOfOwnWorkers() {
        customers.add(11, "Jana Nováková");
        User worker1 = Fixtures.user("w1", W), worker2 = Fixtures.user("w2", W), mgr = Fixtures.user("m1", M), other = Fixtures.user("m2", M);
        worker1.setId(5L);
        worker2.setId(6L);
        mgr.setId(2L);
        other.setId(3L);
        Project own = projectService.createProject("Moje svatba", 11L, LocalDate.of(2026, 12, 1), "Ostrava", 10, worker1);
        Project foreign = projectService.createProject("Cizí svatba", 11L, LocalDate.of(2026, 12, 2), "Ostrava", 10, worker2);
        projects.supervisorOf.put(5L, 2L);
        projects.supervisorOf.put(6L, 3L);

        assertEquals(List.of(own), projectService.searchManagedProjects(mgr, null));
        assertEquals(List.of(), projectService.searchManagedProjects(mgr, "cizí"));        // search stays inside the own set
        assertEquals(List.of(foreign), projectService.searchManagedProjects(other, "svatba"));
        assertTrue(projectService.findManagedProject(mgr, own.getId()).isPresent());
        assertTrue(projectService.findManagedProject(mgr, foreign.getId()).isEmpty());
        User nobody = Fixtures.user("m3", M);
        nobody.setId(4L);
        assertTrue(projectService.searchManagedProjects(nobody, null).isEmpty());          // nobody reports to him: empty list
    }

    @Test void deactivatedWorkerIsRefused() {
        User w = Fixtures.user("w", W);
        w.setActive(false);
        assertThrows(ValidationException.class, () -> cz.vsb.catering.business.domain.AccessPolicy.requireWorker(w));
    }
}
