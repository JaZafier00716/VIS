package cz.vsb.catering.presentation.web;

import static cz.vsb.catering.presentation.web.MenuPage.esc;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Server-rendered HTML of the manager pages (UC15): login, project list, project detail, menu.
 *  Read only, no JavaScript, styles shared with the customer pages; every text goes through esc(). */
final class ManagerPage {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. M. yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d. M. yyyy H:mm");
    private static final PriceStrategy NET = PriceStrategy.WITHOUT_VAT;

    /** Latest revision of a menu and the number of unresolved comments over all its revisions. */
    record MenuSummary(MenuRevision latest, long unresolved) { }

    private ManagerPage() { }

    static String login(String username, String error) {
        StringBuilder h = new StringBuilder(hero("Přehled projektů", "Přihlášení manažera", null));
        h.append("<main><form class=\"card form\" method=\"post\" action=\"/manager/login\"><b>Přihlásit se</b>");
        if (error != null) h.append("<div class=\"banner err\">").append(esc(error)).append("</div>");
        h.append("<label class=\"field\"><span class=\"label\">Uživatelské jméno</span><input name=\"username\" maxlength=\"30\"")
         .append(" autocomplete=\"username\" required value=\"").append(esc(username)).append("\"></label>")
         .append("<label class=\"field\"><span class=\"label\">Heslo</span><input type=\"password\" name=\"password\"")
         .append(" autocomplete=\"current-password\" required></label>")
         .append("<button class=\"btn\" type=\"submit\">Přihlásit</button></form></main>");
        return page("Přihlášení manažera", h.toString());
    }

    static String list(User user, String q, List<Project> projects) {
        StringBuilder h = new StringBuilder(hero("Projekty", "Přehled jen pro čtení", user));
        h.append("<main class=\"wide\"><form class=\"search\" method=\"get\" action=\"/manager\">")
         .append("<input name=\"q\" placeholder=\"Hledat podle názvu, zákazníka nebo místa\" value=\"").append(esc(q)).append("\">")
         .append("<button class=\"btn\" type=\"submit\">Hledat</button></form>");
        h.append("<section class=\"card\"><div class=\"card-h\">Projekty <span class=\"chip\">").append(projects.size())
         .append("</span></div>");
        if (projects.isEmpty()) h.append("<div class=\"c muted\">Žádný projekt neodpovídá hledání.</div>");
        else {
            h.append("<div class=\"scroll\"><table><thead><tr><th>Název</th><th>Zákazník</th><th>Termín</th><th>Místo</th>")
             .append("<th class=\"r\">Hosté</th><th>Stav</th></tr></thead><tbody>");
            for (Project p : projects)
                h.append("<tr><td><a href=\"/manager/projects/").append(p.getId()).append("\">").append(esc(p.getName()))
                 .append("</a></td><td>").append(esc(customer(p))).append("</td><td>").append(date(p))
                 .append("</td><td>").append(esc(p.getEventPlace())).append("</td><td class=\"r\">").append(p.getGuestCount())
                 .append("</td><td><span class=\"chip\">").append(esc(p.getStatus().label())).append("</span></td></tr>");
            h.append("</tbody></table></div>");
        }
        h.append("</section></main>");
        return page("Projekty", h.toString());
    }

    static String project(User user, Project p, Folder root, Map<Long, MenuSummary> menus) {
        StringBuilder h = new StringBuilder(hero(p.getName(), p.getStatus().label(), user));
        h.append("<main class=\"wide\"><a href=\"/manager\">← Všechny projekty</a><section class=\"card\">")
         .append("<div class=\"card-h\">Údaje projektu</div><dl class=\"facts\">")
         .append("<dt>Zákazník</dt><dd>").append(esc(customer(p))).append("</dd>")
         .append("<dt>Termín akce</dt><dd>").append(date(p)).append("</dd>")
         .append("<dt>Místo</dt><dd>").append(esc(p.getEventPlace())).append("</dd>")
         .append("<dt>Počet hostů</dt><dd>").append(p.getGuestCount()).append("</dd>")
         .append("<dt>Stav</dt><dd>").append(esc(p.getStatus().label())).append("</dd>");
        if (p.getNote() != null && !p.getNote().isBlank())
            h.append("<dt>Poznámka</dt><dd>").append(esc(p.getNote())).append("</dd>");
        h.append("</dl></section><section class=\"card\"><div class=\"card-h\">Obsah projektu</div><ul class=\"tree\">");
        tree(h, root, menus, p.getGuestCount());
        h.append("</ul></section></main>");
        return page(p.getName(), h.toString());
    }

    private static void tree(StringBuilder h, Folder folder, Map<Long, MenuSummary> menus, int guests) {
        h.append("<li><span class=\"ico\">▸</span><b>").append(esc(folder.getName())).append("</b>");
        if (!folder.getChildren().isEmpty()) {
            h.append("<ul>");
            for (ProjectNode n : folder.getChildren()) {
                if (n instanceof Folder f) tree(h, f, menus, guests);
                else if (n instanceof Document d)
                    h.append("<li><span class=\"ico\">▢</span>").append(esc(d.getName()))
                     .append(" <span class=\"faint small\">").append(esc(d.formattedSize())).append("</span></li>");
                else if (n instanceof Menu m) menuRow(h, m, menus.get(m.getMenuId()), guests);
            }
            h.append("</ul>");
        }
        h.append("</li>");
    }

    private static void menuRow(StringBuilder h, Menu m, MenuSummary s, int guests) {
        h.append("<li class=\"menu\"><span class=\"ico\">☰</span><a href=\"/manager/menus/").append(m.getMenuId()).append("\">")
         .append(esc(m.getName())).append("</a>");
        if (s != null) {
            MenuRevision r = s.latest();
            h.append("<div class=\"meta\">Revize ").append(r.getRevisionNo()).append(' ').append(state(r.getState()))
             .append(" · celkem <b>").append(r.totalPrice(NET).format()).append("</b> bez DPH");
            if (guests > 0) h.append(" · ").append(r.pricePerGuest(NET, guests).format()).append(" na hosta");
            h.append(" · ").append(s.unresolved() > 0
                    ? "<span class=\"chip warn\">nevyřízené komentáře: " + s.unresolved() + "</span>"
                    : "<span class=\"chip ok\">bez nevyřízených komentářů</span>").append("</div>");
        }
        h.append("</li>");
    }

    static String menu(User user, String title, int guests, MenuRevision rev, PriceStrategy strategy, List<Comment> comments) {
        boolean vat = strategy == PriceStrategy.WITH_VAT;
        StringBuilder h = new StringBuilder(hero(title, "Revize " + rev.getRevisionNo() + " · " + rev.getState().label()
                + (guests > 0 ? " · " + guests + " hostů" : ""), user));
        h.append("<main class=\"wide\"><div class=\"row\"><a href=\"/manager\">← Všechny projekty</a>")
         .append("<span class=\"switch\">Ceny ").append(esc(strategy.label())).append(" · <a href=\"?")
         .append(vat ? "" : "dph=1").append("\">zobrazit ").append(vat ? "bez DPH" : "s DPH").append("</a></span></div>");
        h.append("<section class=\"card\"><div class=\"card-h\">Nejnovější revize ").append(rev.getRevisionNo()).append(' ')
         .append(state(rev.getState())).append("</div>");
        if (rev.getCategories().isEmpty()) h.append("<div class=\"c muted\">Revize je prázdná.</div>");
        else {
            h.append("<div class=\"scroll\"><table><thead><tr><th>Položka</th><th class=\"r\">Množství</th>")
             .append("<th class=\"r\">Cena/jedn.</th><th class=\"r\">Cena</th><th>Poznámka</th></tr></thead><tbody>");
            for (MenuCategory c : rev.getCategories()) {
                h.append("<tr class=\"cat-row\"><td colspan=\"5\">").append(esc(c.getName())).append("</td></tr>");
                for (MenuItem i : c.getItems()) {
                    MenuItem one = new MenuItem(i.getProduct(), BigDecimal.ONE, i.getUnitPrice(), null);
                    h.append("<tr><td>").append(esc(i.getProduct().getName())).append("</td><td class=\"r\">")
                     .append(i.getQuantity().stripTrailingZeros().toPlainString().replace('.', ',')).append(' ')
                     .append(esc(i.getProduct().getUnitLabel())).append("</td><td class=\"r\">").append(strategy.lineTotal(one).format())
                     .append("</td><td class=\"r\"><b>").append(i.price(strategy).format()).append("</b></td><td class=\"muted\">")
                     .append(esc(i.getNote())).append("</td></tr>");
                }
            }
            h.append("</tbody></table></div>");
        }
        h.append("<div class=\"tot\"><span><b>Celkem ").append(esc(strategy.label())).append("</b>");
        if (guests > 0) h.append("<br><span class=\"muted small\">").append(rev.pricePerGuest(strategy, guests).format())
                .append(" na hosta</span>");
        h.append("</span><b class=\"total\">").append(rev.totalPrice(strategy).format()).append("</b></div></section>");

        h.append("<section class=\"card\"><div class=\"card-h\">Komentáře zákazníka <span class=\"chip\">").append(comments.size())
         .append("</span></div>");
        if (comments.isEmpty()) h.append("<div class=\"c muted\">Zatím bez komentářů.</div>");
        for (Comment c : comments) {
            String about = !c.isOnItem() ? "Celé menu" : c.getProductName() != null ? c.getProductName() : "Položka menu";
            h.append("<div class=\"c\"><div class=\"row\"><b>").append(esc(c.getAuthorName())).append("</b><span class=\"faint small\">")
             .append(c.getCreatedAt().format(DATE_TIME)).append(" · revize ").append(c.getRevisionNo()).append(" · ")
             .append(esc(about)).append("</span>")
             .append(c.isResolved() ? "<span class=\"chip ok\">vyřízeno</span>" : "<span class=\"chip warn\">nevyřízeno</span>")
             .append("</div><div class=\"body\">").append(esc(c.getBody())).append("</div>");
            if (c.isResolved()) {
                h.append("<div class=\"muted small\">Vyřídil(a) ").append(esc(c.getResolvedByName()));
                if (c.getResolvedAt() != null) h.append(", ").append(c.getResolvedAt().format(DATE_TIME));
                if (c.getResolutionNote() != null) h.append(": ").append(esc(c.getResolutionNote()));
                h.append("</div>");
            }
            h.append("</div>");
        }
        h.append("</section></main>");
        return page(title, h.toString());
    }

    // ---------------------------------------------------------------- helpers

    private static String hero(String title, String sub, User user) {
        StringBuilder h = new StringBuilder("<div class=\"hero\"><div class=\"brand\"><span class=\"logo\">C</span>Catering · přehled pro manažera");
        if (user != null)
            h.append("<form class=\"out\" method=\"post\" action=\"/manager/logout\"><span>").append(esc(user.getFullName()))
             .append("</span><button type=\"submit\">Odhlásit</button></form>");
        return h.append("</div><h1>").append(esc(title)).append("</h1><div class=\"sub\">").append(esc(sub)).append("</div></div>")
                .toString();
    }

    private static String state(RevisionState s) {
        return s == RevisionState.SHARED ? "<span class=\"chip ok\">" + esc(s.label()) + "</span>"
                : "<span class=\"chip\">" + esc(s.label()) + "</span>";
    }

    private static String customer(Project p) { return p.getCustomer() == null ? "" : p.getCustomer().displayName(); }

    private static String date(Project p) { return p.getEventDate() == null ? "" : p.getEventDate().format(DATE); }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"cs\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + esc(title)
                + "</title><style>" + MenuPage.CSS + CSS + "</style></head><body>" + body + "</body></html>";
    }

    private static final String CSS = """
        main.wide{max-width:960px}.hero>*{max-width:932px}
        .brand .out{margin-left:auto;display:flex;gap:8px;align-items:center}
        .out button{font:inherit;font-size:12px;font-weight:600;color:#fff;background:transparent;border:1px solid rgba(255,255,255,.6);border-radius:6px;padding:3px 10px;cursor:pointer}
        .search{display:flex;gap:8px}.search .btn{height:auto;padding:0 18px}
        .scroll{overflow-x:auto}
        table{width:100%;border-collapse:collapse;font-size:14px}
        th{text-align:left;font-size:12px;color:var(--muted);font-weight:700;padding:8px 14px;border-bottom:1px solid var(--border);white-space:nowrap}
        td{padding:8px 14px;border-bottom:1px solid #EDF0F3;vertical-align:top}td.r,th.r{text-align:right;white-space:nowrap}
        .cat-row td{font-size:12px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);font-weight:700;background:#F8FAFB}
        .facts{display:grid;grid-template-columns:max-content 1fr;gap:6px 16px;padding:12px 14px}.facts dt{color:var(--muted)}
        .tree,.tree ul{list-style:none}.tree{padding:10px 14px}.tree ul{padding-left:20px;border-left:1px dashed var(--border-strong);margin-left:6px}
        .tree li{padding:4px 0}.ico{display:inline-block;width:18px;color:var(--muted)}
        .tree .meta{font-size:13px;color:var(--muted);margin:2px 0 0 18px;display:flex;flex-wrap:wrap;gap:4px 6px;align-items:center}
        .banner.err{border-color:#F3C1C1;background:var(--err-soft);color:var(--err)}
        """;
}
