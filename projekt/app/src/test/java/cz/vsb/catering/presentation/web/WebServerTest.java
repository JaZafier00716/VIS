package cz.vsb.catering.presentation.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.User;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** Customer web against the local Oracle: own project with one shared menu, deleted at the end. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebServerTest {
    private static AppContext ctx;
    private static WebServer server;
    private static Project project;
    private static MenuRevision revision;
    private static Product product;
    private static String token;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll static void setUp() {
        ctx = AppContext.load();
        User user = ctx.auth.loginDesktop("jzamostny", "heslo123");
        project = ctx.projects.createProject("WebServerTest " + System.nanoTime(),
                ctx.projects.findAllCustomers().get(0).getId(), LocalDate.now().plusDays(30), "Ostrava", 10, user);
        Menu menu = ctx.menus.createMenu(project.getRootFolder(), "Testovací menu", user);
        MenuRevision working = ctx.menus.openLatestRevision(menu.getMenuId());
        MenuCategory category = working.addCategory("Předkrmy <b>");
        product = ctx.menus.findProducts().get(0);
        working.addItem(category, product, new BigDecimal("10"), Money.of("100"), null);
        ctx.menus.saveRevision(working, null, user);
        revision = ctx.menus.openLatestRevision(menu.getMenuId());
        token = ctx.sharing.shareRevision(revision).getToken();
        server = new WebServer(ctx.sharing, 0);
        server.start();
    }

    @AfterAll static void tearDown() throws Exception {
        if (server != null) server.stop();
        if (project == null) return;
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("config/db.properties"))) { config.load(in); }
        try (Connection c = DriverManager.getConnection(config.getProperty("db.url"),
                config.getProperty("db.user"), config.getProperty("db.password"));
             PreparedStatement ps = c.prepareStatement("DELETE FROM projects WHERE project_id = ?")) {
            ps.setLong(1, project.getId());
            ps.executeUpdate();
        }
    }

    @Test @Order(1) void validLinkShowsMenuWithPrices() throws Exception {
        HttpResponse<String> r = get("/menu/" + token);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains(MenuPage.esc(product.getName())));
        assertTrue(r.body().contains("1 000,00 Kč"), "10 × 100 Kč without VAT");
        assertTrue(r.body().contains("100,00 Kč na hosta"));
        assertTrue(r.body().contains("Předkrmy &lt;b&gt;"));

        HttpResponse<String> vat = get("/menu/" + token + "?dph=1");
        assertEquals(200, vat.statusCode());
        assertTrue(vat.body().contains("s DPH"));
        assertFalse(vat.body().contains("<b class=\"total\">1 000,00 Kč"), "total with VAT differs");
    }

    @Test @Order(2) void validCommentIsStoredAndEscaped() throws Exception {
        String target = revision.getCategories().get(0).getId() + ":" + product.getId();
        HttpResponse<String> r = post("/menu/" + token + "/comments",
                "author=Jana&target=" + target + "&body=" + enc("Ahoj <script>alert(1)</script> čšř"));
        assertEquals(303, r.statusCode());
        assertEquals("/menu/" + token + "?ok=1#komentare", r.headers().firstValue("Location").orElseThrow());

        HttpResponse<String> page = get("/menu/" + token + "?ok=1");
        assertTrue(page.body().contains("Komentář byl odeslán"));
        assertTrue(page.body().contains("Ahoj &lt;script&gt;alert(1)&lt;/script&gt; čšř"));
        assertFalse(page.body().contains("<script>"));
    }

    @Test @Order(3) void emptyNameIsRejected() throws Exception {
        int before = ctx.sharing.listComments(revision.getMenuId(), revision.getRevisionNo()).size();
        HttpResponse<String> r = post("/menu/" + token + "/comments", "author=&target=&body=" + enc("Text \"x\""));
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("Vyplňte své jméno."));
        assertTrue(r.body().contains("Text &quot;x&quot;"), "entered text is kept");
        assertEquals(before, ctx.sharing.listComments(revision.getMenuId(), revision.getRevisionNo()).size());
    }

    @Test @Order(4) void unknownTokenAndOtherPathsAre404() throws Exception {
        HttpResponse<String> r = get("/menu/00000000000000000000000000000000");
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("Odkaz není platný, kontaktujte pracovníka"));
        assertEquals(404, get("/").statusCode());
        assertEquals(404, get("/menu/abc/x").statusCode());
    }

    @Test @Order(5) void oldLinkAfterResharingOffersCurrentLink() throws Exception {
        String newToken = ctx.sharing.shareRevision(revision).getToken();
        assertNotEquals(token, newToken);

        HttpResponse<String> r = get("/menu/" + token);
        assertTrue(r.body().contains("Odkaz již není platný"));
        assertTrue(r.body().contains("href=\"/menu/" + newToken + "\""));

        HttpResponse<String> p = post("/menu/" + token + "/comments", "author=Jana&target=&body=pozde");
        assertTrue(p.body().contains("Odkaz již není platný"));
        assertTrue(ctx.sharing.listComments(revision.getMenuId(), revision.getRevisionNo()).stream()
                .noneMatch(c -> c.getBody().equals("pozde")));
        assertEquals(200, get("/menu/" + newToken).statusCode());
    }

    @Test @Order(6) void archivedProjectShowsMenuWithoutCommentForm() throws Exception {   // UC03
        String current = ctx.sharing.findValidLink(revision.getMenuId()).orElseThrow().getToken();
        ctx.projects.archiveProject(project.getId());
        HttpResponse<String> r = get("/menu/" + current);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("Projekt je archivovaný, komentáře již nelze přidávat."));
        assertFalse(r.body().contains("<form"));

        HttpResponse<String> p = post("/menu/" + current + "/comments", "author=Jana&target=&body=archiv");
        assertEquals(400, p.statusCode());
        assertTrue(p.body().contains("Projekt je archivovaný"));
        assertTrue(ctx.sharing.listComments(revision.getMenuId(), revision.getRevisionNo()).stream()
                .noneMatch(c -> c.getBody().equals("archiv")));
    }

    @Test @Order(7) void httpDetailsAndSecurityHeaders() throws Exception {
        HttpResponse<String> page = get("/menu/" + token);
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElseThrow().contains("form-action 'self'"));
        assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals("no-referrer", page.headers().firstValue("Referrer-Policy").orElseThrow());

        HttpResponse<String> badEscape = post("/menu/" + token + "/comments", "author=%zz");
        assertEquals(400, badEscape.statusCode());
        assertTrue(badEscape.body().contains("neplatné znaky"));

        HttpResponse<String> wrongGet = post("/menu/" + token, "x=1");                    // known route, wrong method
        assertEquals(405, wrongGet.statusCode());
        assertEquals("GET", wrongGet.headers().firstValue("Allow").orElseThrow());
        HttpResponse<String> wrongPost = get("/menu/" + token + "/comments");
        assertEquals(405, wrongPost.statusCode());
        assertEquals("POST", wrongPost.headers().firstValue("Allow").orElseThrow());

        HttpResponse<String> badToken = get("/menu/abc-def!");                            // cannot be a token
        assertEquals(404, badToken.statusCode());
        assertTrue(badToken.body().contains("Odkaz není platný, kontaktujte pracovníka"));

        assertEquals(413, post("/menu/" + token + "/comments", "body=" + "a".repeat(70_000)).statusCode());
    }

    @Test @Order(8) void requestTimeoutsAreConfigured() {
        assertEquals("30", System.getProperty("sun.net.httpserver.maxReqTime"));
        assertEquals("30", System.getProperty("sun.net.httpserver.maxRspTime"));
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String form) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(String path) { return URI.create("http://localhost:" + server.port() + path); }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
