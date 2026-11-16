package cz.vsb.catering.presentation.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeUserMapper;
import cz.vsb.catering.business.fake.Fixtures;
import cz.vsb.catering.business.service.AuthService;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC15 manager web over real HTTP against the seeded Oracle; read only, nothing to clean up.
 *  Seed: tsvoboda = MANAGER (workers avesela, jbenes: projects 6 and 9), mkral = ADMIN+MANAGER (worker jzamostny:
 *  projects 1, 4, 10), ehorakova = MANAGER (pnovak, lcerna), mdvorakova = MANAGER (kpokorny), lcerna = WORKER,
 *  kpokorny = ADMIN+WORKER. The default session is mkral; project 1 "Svatba Novákovi" with menus 1 and 2 (menu 2 has a resolved and an unresolved comment). */
class ManagerWebTest {
    private static WebServer server;
    private static final HttpClient http = HttpClient.newHttpClient();       // does not follow redirects

    @BeforeAll static void setUp() {
        server = new WebServer(AppContext.load(), 0);
        server.start();
    }

    @AfterAll static void tearDown() { if (server != null) server.stop(); }

    @Test void managerLogsInWithSecureCookie() throws Exception {
        HttpResponse<String> r = login("tsvoboda", "heslo123");
        assertEquals(303, r.statusCode());
        assertEquals("/manager", r.headers().firstValue("Location").orElseThrow());
        String cookie = r.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.matches("mgr=[0-9a-f]{64};.*"), cookie);
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict") && cookie.contains("Path=/manager"));
        assertEquals(303, login("mkral", "heslo123").statusCode());                    // admin who is also manager
    }

    @Test void wrongPasswordAndNonManagersAreRefused() throws Exception {
        HttpResponse<String> wrong = login("tsvoboda", "spatne-heslo");
        assertEquals(401, wrong.statusCode());
        assertTrue(wrong.body().contains("Neplatné uživatelské jméno nebo heslo."));
        assertTrue(wrong.headers().firstValue("Set-Cookie").isEmpty());
        for (String worker : new String[] {"lcerna", "kpokorny"}) {
            HttpResponse<String> r = login(worker, "heslo123");
            assertEquals(403, r.statusCode(), worker);
            assertTrue(r.body().contains("jen pro roli Manažer"));
            assertTrue(r.headers().firstValue("Set-Cookie").isEmpty());
        }
    }

    @Test void notLoggedInIsRedirectedToLogin() throws Exception {
        for (String path : new String[] {"/manager", "/manager/projects/1", "/manager/menus/2"}) {
            HttpResponse<String> r = get(path, null);
            assertEquals(303, r.statusCode(), path);
            assertEquals("/manager/login", r.headers().firstValue("Location").orElseThrow());
        }
        assertEquals(303, get("/manager", "mgr=forged").statusCode());
        HttpResponse<String> form = get("/manager/login", null);
        assertEquals(200, form.statusCode());
        assertTrue(form.body().contains("method=\"post\" action=\"/manager/login\""));
        assertFalse(form.body().contains("<script"));
    }

    @Test void listShowsSeedProjectsAndFilters() throws Exception {
        String cookie = session();
        String all = get("/manager", cookie).body();
        assertTrue(all.contains("Svatba Novákovi") && all.contains("Svatba Sedláčková") && all.contains("Konference VŠB 2026"));
        assertFalse(all.contains("Ples SK Poruba"), "project of a worker of another manager");
        assertTrue(get("/manager", session("mdvorakova")).body().contains("Výročí firmy Malý &amp; syn"), "seed text is escaped");
        assertTrue(all.contains("href=\"/manager/projects/1\""));

        String svatba = get("/manager?q=" + URLEncoder.encode("svatba", StandardCharsets.UTF_8), cookie).body();
        assertTrue(svatba.contains("Svatba Novákovi") && svatba.contains("Svatba Sedláčková"));
        assertFalse(svatba.contains("Ples SK Poruba"));
        String byPlace = get("/manager?q=" + URLEncoder.encode("Hradec nad", StandardCharsets.UTF_8), cookie).body();
        assertTrue(byPlace.contains("Svatba Novákovi") && !byPlace.contains("Svatba Sedláčková"));
        String xss = get("/manager?q=" + URLEncoder.encode("<script>x", StandardCharsets.UTF_8), cookie).body();
        assertTrue(xss.contains("&lt;script&gt;x") && !xss.contains("<script>"));
        assertTrue(xss.contains("Žádný projekt neodpovídá hledání."));
    }

    /** A-17: a manager sees only projects whose responsible worker reports to him; foreign ones are 404. */
    @Test void managerSeesOnlyOwnWorkersProjects() throws Exception {
        String tsvoboda = session("tsvoboda");                                         // avesela: projects 6, 9
        String list = get("/manager", tsvoboda).body();
        assertTrue(list.contains("Narozeninová oslava 50") && list.contains("Ples SK Poruba"));
        assertFalse(list.contains("Svatba Novákovi") || list.contains("Konference VŠB 2026"));
        assertEquals(200, get("/manager/projects/6", tsvoboda).statusCode());
        HttpResponse<String> foreign = get("/manager/projects/1", tsvoboda);           // jzamostny's project
        assertEquals(404, foreign.statusCode());
        assertFalse(foreign.body().contains("Zámek Hradec"));
        for (String path : new String[] {"/manager/menus/1", "/manager/menus/2"})
            assertEquals(404, get(path, tsvoboda).statusCode(), path);
        assertEquals(200, get("/manager/menus/2", session("mkral")).statusCode());     // the owner's manager
        String other = get("/manager?q=" + URLEncoder.encode("svatba", StandardCharsets.UTF_8), tsvoboda).body();
        assertFalse(other.contains("Svatba Novákovi"), "search stays inside the own projects");
    }

    @Test void projectDetailShowsTreeAndMenus() throws Exception {
        HttpResponse<String> r = get("/manager/projects/1", session());
        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("Jana Nováková") && b.contains("Zámek Hradec nad Moravicí"));
        for (String node : new String[] {"Smlouvy", "Podklady", "Fotky", "smlouva.pdf", "prostory-2.jpg"})
            assertTrue(b.contains(node), node);
        assertTrue(b.contains("href=\"/manager/menus/1\">Svatební menu") && b.contains("href=\"/manager/menus/2\">Půlnoční raut"));
        assertTrue(b.contains("Revize 3") && b.contains("Revize 4"));
        assertTrue(b.contains("bez DPH") && b.contains("na hosta") && b.contains("Kč"));
        assertTrue(b.matches("(?s).*nevyřízené komentáře: [1-9].*"), "menu 2 has an unresolved seed comment");
        assertFalse(b.contains("<script"));
    }

    @Test void menuPageShowsLatestRevisionAndAllComments() throws Exception {
        String cookie = session();
        HttpResponse<String> r = get("/manager/menus/2", cookie);
        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("Svatba Novákovi – Půlnoční raut") && b.contains("Nejnovější revize 4"));
        assertTrue(b.contains("<table>") && b.contains("Celkem bez DPH"));
        assertTrue(b.contains("Celkově se nám menu líbí"));
        assertTrue(b.contains("vyřízeno") && b.contains("nevyřízeno") && b.contains("Upraveno v revizi č. 4"));
        assertTrue(b.contains("revize 3") && b.contains("revize 4"));
        assertTrue(get("/manager/menus/2?dph=1", cookie).body().contains("Celkem s DPH"));
    }

    @Test void unknownIdsAndPathsAre404() throws Exception {
        String cookie = session();
        assertEquals(404, get("/manager/projects/999999", cookie).statusCode());
        assertEquals(404, get("/manager/menus/999999", cookie).statusCode());
        assertEquals(404, get("/manager/projects/abc", cookie).statusCode());
        assertEquals(404, get("/managerx", cookie).statusCode());
    }

    @Test void noDataChangingRoutes() throws Exception {
        String cookie = session();
        for (String path : new String[] {"/manager", "/manager/projects/1", "/manager/menus/2"}) {
            HttpResponse<String> r = send(HttpRequest.newBuilder(uri(path)).header("Cookie", cookie)
                    .POST(HttpRequest.BodyPublishers.ofString("name=x")).build());
            assertEquals(405, r.statusCode(), path);
            assertEquals("GET", r.headers().firstValue("Allow").orElseThrow());
        }
        assertEquals(405, get("/manager/logout", cookie).statusCode());                   // logout is POST only
    }

    @Test void logoutInvalidatesSession() throws Exception {
        String cookie = session();
        assertEquals(200, get("/manager", cookie).statusCode());
        HttpResponse<String> out = send(HttpRequest.newBuilder(uri("/manager/logout")).header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertEquals(303, out.statusCode());
        assertTrue(out.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        assertEquals(303, get("/manager", cookie).statusCode());
    }

    @Test void securityHeadersOnManagerPage() throws Exception {
        HttpResponse<String> r = get("/manager/login", null);
        assertTrue(r.headers().firstValue("Content-Security-Policy").orElseThrow().contains("default-src 'none'"));
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals("no-referrer", r.headers().firstValue("Referrer-Policy").orElseThrow());
    }

    @Test void sixthWrongAttemptIsThrottledAndUnknownUserLooksLikeWrongPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> r = login("nobody-throttle", "x");
            assertEquals(401, r.statusCode());
            assertTrue(r.body().contains("Neplatné uživatelské jméno nebo heslo."));   // same text as a wrong password
        }
        HttpResponse<String> r = login("Nobody-Throttle", "x");                          // key is case-insensitive
        assertEquals(429, r.statusCode());
        assertTrue(r.body().contains("Příliš mnoho neúspěšných pokusů."));
    }

    @Test void malformedEscapeIs400() throws Exception {
        HttpResponse<String> r = send(HttpRequest.newBuilder(uri("/manager/login"))
                .POST(HttpRequest.BodyPublishers.ofString("username=%zz")).build());
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("neplatné znaky"));
    }

    @Test void oversizedLoginFormIs413() throws Exception {
        HttpResponse<String> r = send(HttpRequest.newBuilder(uri("/manager/login"))
                .POST(HttpRequest.BodyPublishers.ofString("username=" + "a".repeat(9000))).build());
        assertEquals(413, r.statusCode());
    }

    // ---------------------------------------------------------------- session re-check and Secure cookie (fakes, no DB)

    @Test void deactivatedOrDemotedManagerLosesSessionOnNextRequest() throws Exception {
        FakeUserMapper users = new FakeUserMapper();
        long a = users.add(Fixtures.user("mgra", Role.MANAGER));
        long b = users.add(Fixtures.user("mgrb", Role.MANAGER));
        HttpServer fake = fakeServer(users, false);
        try {
            String ca = fakeLogin(fake, "mgra").headers().firstValue("Set-Cookie").orElseThrow();
            String cb = fakeLogin(fake, "mgrb").headers().firstValue("Set-Cookie").orElseThrow();
            users.stored(a).setActive(false);
            users.stored(b).changeRoles(java.util.Set.of(Role.WORKER));
            for (String cookie : new String[] {ca, cb}) {
                HttpResponse<String> r = send(HttpRequest.newBuilder(fakeUri(fake, "/manager"))
                        .header("Cookie", cookie.substring(0, cookie.indexOf(';'))).GET().build());
                assertEquals(303, r.statusCode());
                assertEquals("/manager/login", r.headers().firstValue("Location").orElseThrow());
            }
        } finally { fake.stop(0); }
    }

    @Test void cookieIsSecureOnlyBehindHttps() throws Exception {
        for (boolean secure : new boolean[] {false, true}) {
            FakeUserMapper users = new FakeUserMapper();
            users.add(Fixtures.user("mgr", Role.MANAGER));
            HttpServer fake = fakeServer(users, secure);
            try {
                String cookie = fakeLogin(fake, "mgr").headers().firstValue("Set-Cookie").orElseThrow();
                assertEquals(secure, cookie.contains("; Secure"), cookie);
            } finally { fake.stop(0); }
        }
    }

    private static HttpServer fakeServer(FakeUserMapper users, boolean secure) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        s.createContext("/manager", new ManagerHandler(new AuthService(users, new DirectTransactionRunner()),
                null, null, null, secure));                                  // list/detail services are not reached
        s.start();
        return s;
    }

    private static URI fakeUri(HttpServer s, String path) { return URI.create("http://localhost:" + s.getAddress().getPort() + path); }

    private static HttpResponse<String> fakeLogin(HttpServer s, String user) throws Exception {
        return send(HttpRequest.newBuilder(fakeUri(s, "/manager/login")).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + user + "&password=heslo123")).build());
    }

    // ---------------------------------------------------------------- helpers

    private static String session() throws Exception {
        return session("mkral");
    }

    private static String session(String user) throws Exception {
        String cookie = login(user, "heslo123").headers().firstValue("Set-Cookie").orElse(null);
        assertNotNull(cookie);
        return cookie.substring(0, cookie.indexOf(';'));
    }

    private static HttpResponse<String> login(String user, String password) throws Exception {
        String form = "username=" + URLEncoder.encode(user, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        return send(HttpRequest.newBuilder(uri("/manager/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
    }

    private static HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) b.header("Cookie", cookie);
        return send(b.build());
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static URI uri(String path) { return URI.create("http://localhost:" + server.port() + path); }
}
