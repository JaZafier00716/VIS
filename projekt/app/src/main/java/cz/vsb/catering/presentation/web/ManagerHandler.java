package cz.vsb.catering.presentation.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import cz.vsb.catering.business.domain.AccessPolicy;
import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.business.service.AuthService;
import cz.vsb.catering.business.service.MenuService;
import cz.vsb.catering.business.service.ProjectService;
import cz.vsb.catering.business.service.SharingService;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Routes of the manager web, UC15 Prohlížet projekty (jen pro čtení):
 *  GET/POST /manager/login, POST /manager/logout, GET /manager (project list, ?q= search),
 *  GET /manager/projects/{id} and GET /manager/menus/{menuId}. Calls only the service facades.
 *  <p>Only users with the MANAGER role may log in (A2: UC15 belongs to the manager). An administrator
 *  without MANAGER is refused on purpose: the read-only overview is a manager tool, admins work in
 *  the desktop app, and the rule stays one clear role check. A manager sees only the projects whose
 *  responsible worker reports to him (users.supervisor_id), see ProjectService.findManagedProject (A-17).
 *  <p>The session is a random token in an HttpOnly, SameSite=Strict cookie, kept in memory with an
 *  8 h expiry (lost on restart, which is fine for a read-only overview). There is no route that
 *  changes data; the only POSTs are login and logout. */
class ManagerHandler implements HttpHandler {
    static final String COOKIE = "mgr";
    static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final Pattern PROJECT = Pattern.compile("/manager/projects/(\\d{1,18})/?");
    private static final Pattern MENU = Pattern.compile("/manager/menus/(\\d{1,18})/?");
    private static final int MAX_FORM_BYTES = 8 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);

    /** The session keeps only the user id: the user is re-read on every request (A-06). */
    private record Session(long userId, Instant expires) { }

    private record Attempts(int failures, Instant first) { }

    private final AuthService auth;
    private final ProjectService projects;
    private final MenuService menus;
    private final SharingService sharing;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    // shortcut: per process, resets on restart; move to the proxy or DB if needed
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final boolean secureCookie;

    /** secureCookie: add the Secure attribute (the site is served over https). */
    ManagerHandler(AuthService auth, ProjectService projects, MenuService menus, SharingService sharing,
                   boolean secureCookie) {
        this.secureCookie = secureCookie;
        this.auth = auth;
        this.projects = projects;
        this.menus = menus;
        this.sharing = sharing;
    }

    @Override public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            Matcher project = PROJECT.matcher(path), menu = MENU.matcher(path);
            boolean list = path.equals("/manager") || path.equals("/manager/");
            if (path.equals("/manager/login")) {
                if (method.equals("GET")) send(ex, 200, ManagerPage.login("", null));
                else if (method.equals("POST")) login(ex);
                else notAllowed(ex, "GET, POST");
            } else if (path.equals("/manager/logout")) {
                if (method.equals("POST")) logout(ex);
                else notAllowed(ex, "POST");
            } else if (!list && !project.matches() && !menu.matches()) {
                send(ex, 404, MenuPage.notFound());
            } else if (!method.equals("GET")) {
                notAllowed(ex, "GET");                               // read only: no data-changing routes
            } else {
                User user = currentUser(ex);
                if (user == null) redirect(ex, "/manager/login");
                else if (list) showList(ex, user);
                else if (project.matches()) showProject(ex, user, Long.parseLong(project.group(1)));
                else showMenu(ex, user, Long.parseLong(menu.group(1)));
            }
        } catch (IllegalArgumentException e) {                       // malformed %-escape in query or form
            send(ex, 400, MenuPage.badRequest());
        } catch (RuntimeException e) {
            e.printStackTrace();
            send(ex, 500, MenuPage.serverError());
        } finally {
            ex.close();                                              // after the catch blocks, which still answer
        }
    }

    // ---------------------------------------------------------------- login / logout

    private void login(HttpExchange ex) throws IOException {
        String raw = MenuHandler.readBody(ex, MAX_FORM_BYTES);
        if (raw == null) {
            send(ex, 413, MenuPage.tooLarge());
            return;
        }
        Map<String, String> form = MenuHandler.parse(raw);
        String username = form.getOrDefault("username", "");
        String key = username.trim().toLowerCase(Locale.ROOT);
        Attempts a = attempts.get(key);
        if (a != null && a.failures() >= MAX_FAILURES && a.first().plus(FAILURE_WINDOW).isAfter(Instant.now())) {
            send(ex, 429, ManagerPage.login(username, "Příliš mnoho neúspěšných pokusů. Zkuste to za 10 minut."));
            return;
        }
        User user;
        try {
            user = auth.login(username, form.getOrDefault("password", ""));
        } catch (ValidationException e) {
            recordFailure(key);
            send(ex, 401, ManagerPage.login(username, e.getMessage()));
            return;
        }
        attempts.remove(key);
        if (!AccessPolicy.canUseManagerWeb(user)) {
            send(ex, 403, ManagerPage.login(username, "Přehled projektů je určen jen pro roli Manažer."));
            return;
        }
        sessions.values().removeIf(s -> s.expires().isBefore(Instant.now()));   // housekeeping on each login
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        sessions.put(token, new Session(user.getId(), Instant.now().plus(SESSION_TTL)));
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + token + "; Path=/manager; HttpOnly; SameSite=Strict"
                + (secureCookie ? "; Secure" : "") + "; Max-Age=" + SESSION_TTL.toSeconds());
        redirect(ex, "/manager");
    }

    private void recordFailure(String key) {
        Instant now = Instant.now();
        attempts.values().removeIf(x -> x.first().plus(FAILURE_WINDOW).isBefore(now));      // housekeeping
        attempts.merge(key, new Attempts(1, now), (old, n) -> new Attempts(old.failures() + 1, old.first()));
    }

    private void logout(HttpExchange ex) throws IOException {
        String token = cookie(ex);
        if (token != null) sessions.remove(token);
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/manager; HttpOnly; SameSite=Strict"
                + (secureCookie ? "; Secure" : "") + "; Max-Age=0");
        redirect(ex, "/manager/login");
    }

    private User currentUser(HttpExchange ex) {
        String token = cookie(ex);
        Session s = token == null ? null : sessions.get(token);
        if (s == null) return null;
        if (s.expires().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        User user = auth.findActiveUser(s.userId()).filter(AccessPolicy::canUseManagerWeb).orElse(null);
        if (user == null) sessions.remove(token);                    // deactivated, deleted or no longer a manager
        return user;
    }

    private static String cookie(HttpExchange ex) {
        for (String header : ex.getRequestHeaders().getOrDefault("Cookie", List.of()))
            for (String part : header.split(";")) {
                String p = part.trim();
                if (p.startsWith(COOKIE + "=")) return p.substring(COOKIE.length() + 1);
            }
        return null;
    }

    // ---------------------------------------------------------------- pages

    private void showList(HttpExchange ex, User user) throws IOException {
        String q = MenuHandler.parse(ex.getRequestURI().getRawQuery()).getOrDefault("q", "").trim();
        List<Project> found = projects.searchManagedProjects(user, q);       // only own workers' projects, archived too
        send(ex, 200, ManagerPage.list(user, q, found));
    }

    private void showProject(HttpExchange ex, User user, long id) throws IOException {
        Project project = projects.findManagedProject(user, id).orElse(null);       // A-17: foreign project = 404
        if (project == null) {
            send(ex, 404, MenuPage.notFound());
            return;
        }
        Folder root = projects.loadTree(id);
        Map<Long, ManagerPage.MenuSummary> summaries = new LinkedHashMap<>();
        collectMenus(root, summaries);
        send(ex, 200, ManagerPage.project(user, project, root, summaries));
    }

    private void collectMenus(Folder folder, Map<Long, ManagerPage.MenuSummary> out) {
        for (ProjectNode n : folder.getChildren()) {
            if (n instanceof Folder f) collectMenus(f, out);
            else if (n instanceof Menu m && m.getMenuId() != null) {
                MenuRevision latest = menus.openLatestRevision(m.getMenuId());
                long unresolved = allComments(m.getMenuId()).stream().filter(c -> !c.isResolved()).count();
                out.put(m.getMenuId(), new ManagerPage.MenuSummary(latest, unresolved));
            }
        }
    }

    private void showMenu(HttpExchange ex, User user, long menuId) throws IOException {
        MenuRevision latest;
        try {
            long projectId = menus.projectIdOf(menuId);
            if (projects.findManagedProject(user, projectId).isEmpty()) {      // A-17: menu of a foreign project = 404
                send(ex, 404, MenuPage.notFound());
                return;
            }
            latest = menus.openLatestRevision(menuId);
        } catch (ValidationException e) {                            // unknown menu id
            send(ex, 404, MenuPage.notFound());
            return;
        }
        boolean vat = "1".equals(MenuHandler.parse(ex.getRequestURI().getRawQuery()).get("dph"));
        send(ex, 200, ManagerPage.menu(user, menus.title(menuId), menus.guestCount(menuId), latest,
                vat ? PriceStrategy.WITH_VAT : PriceStrategy.WITHOUT_VAT, allComments(menuId)));
    }

    /** Comments of all revisions of the menu (customers comment on shared revisions, the latest is often a working copy). */
    private List<Comment> allComments(long menuId) {
        List<Comment> all = new ArrayList<>();
        for (MenuRevision r : menus.listRevisions(menuId)) all.addAll(sharing.listComments(menuId, r.getRevisionNo()));
        return all;
    }

    // ---------------------------------------------------------------- helpers

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
    }

    private static void notAllowed(HttpExchange ex, String allow) throws IOException {
        ex.getResponseHeaders().set("Allow", allow);
        send(ex, 405, MenuPage.notFound());
    }

    private static void send(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        MenuHandler.securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
