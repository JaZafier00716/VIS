package cz.vsb.catering.presentation.web;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.business.service.SharedMenuView;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Server-rendered HTML of the customer pages W1 (menu with comments) and W2 (link not valid).
 *  No JavaScript; every user-supplied text goes through esc(). */
final class MenuPage {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d. M. yyyy H:mm");

    /** Values the customer typed into the comment form plus field errors (author, target, body). */
    record Form(String author, String target, String body, Map<String, String> errors) {
        static final Form EMPTY = new Form("", "", "", Map.of());
    }

    private MenuPage() { }

    // ---------------------------------------------------------------- W1

    static String menu(SharedMenuView view, PriceStrategy strategy, Form form, boolean sent) {
        MenuRevision rev = view.revision();
        String token = view.link().getToken();
        StringBuilder h = new StringBuilder();
        h.append("<div class=\"hero\"><div class=\"brand\"><span class=\"logo\">C</span>Catering · nabídka menu</div>")
         .append("<h1>").append(esc(view.title())).append("</h1><div class=\"sub\">Revize ").append(rev.getRevisionNo());
        if (view.guestCount() > 0) h.append(" · ").append(view.guestCount()).append(" hostů");
        h.append(" · platí do ").append(view.link().getValidUntil().toLocalDate().format(DateTimeFormatter.ofPattern("d. M. yyyy")))
         .append("</div></div><main>");

        // Strategy pattern visible to the customer: the same revision priced without or with VAT
        boolean vat = strategy == PriceStrategy.WITH_VAT;
        h.append("<div class=\"switch\">Ceny ").append(esc(strategy.label())).append(" · <a href=\"/menu/").append(token)
         .append(vat ? "" : "?dph=1").append("\">zobrazit ").append(vat ? "bez DPH" : "s DPH").append("</a></div>");

        h.append("<section class=\"card\">");
        for (MenuCategory c : rev.getCategories()) {
            h.append("<div class=\"cat\">").append(esc(c.getName())).append("</div>");
            for (MenuItem i : c.getItems()) {
                MenuItem one = new MenuItem(i.getProduct(), BigDecimal.ONE, i.getUnitPrice(), null);
                h.append("<div class=\"it\"><span class=\"n\">").append(esc(i.getProduct().getName()))
                 .append("</span><span class=\"p\">").append(i.price(strategy).format()).append("</span>")
                 .append("<span class=\"d\">").append(quantity(i.getQuantity())).append(' ').append(esc(i.getProduct().getUnitLabel()))
                 .append(" × ").append(strategy.lineTotal(one).format());
                if (i.getNote() != null && !i.getNote().isBlank()) h.append(" · ").append(esc(i.getNote()));
                h.append("</span><span class=\"d r\">");
                for (String a : i.getProduct().getAllergens()) h.append("<span class=\"al\">").append(esc(a)).append("</span>");
                h.append("</span></div>");
            }
        }
        h.append("<div class=\"tot\"><span><b>Celkem ").append(esc(strategy.label())).append("</b>");
        if (view.guestCount() > 0)
            h.append("<br><span class=\"muted small\">").append(rev.pricePerGuest(strategy, view.guestCount()).format())
             .append(" na hosta</span>");
        h.append("</span><b class=\"total\">").append(rev.totalPrice(strategy).format()).append("</b></div></section>");

        comments(h, rev);
        if (view.readOnly())                                  // UC03: archived project, no new comments
            h.append("<div class=\"card form muted\">Projekt je archivovaný, komentáře již nelze přidávat.</div>");
        else commentForm(h, rev, token, vat, form, sent);
        h.append("</main>");
        return page(view.title(), h.toString());
    }

    private static void comments(StringBuilder h, MenuRevision rev) {
        h.append("<section class=\"card\" id=\"komentare\"><div class=\"card-h\">Komentáře <span class=\"chip\">")
         .append(rev.getComments().size()).append("</span></div>");
        if (rev.getComments().isEmpty()) h.append("<div class=\"c muted\">Zatím bez komentářů.</div>");
        for (Comment c : rev.getComments()) {
            String about = !c.isOnItem() ? "Celé menu" : c.getProductName() != null ? c.getProductName() : "Položka menu";
            h.append("<div class=\"c\"><div class=\"row\"><b>").append(esc(c.getAuthorName())).append("</b><span class=\"faint small\">")
             .append(c.getCreatedAt().format(DATE_TIME)).append(" · ").append(esc(about)).append("</span>")
             .append(c.isResolved() ? "<span class=\"chip ok\">vyřízeno</span>" : "<span class=\"chip warn\">nevyřízeno</span>")
             .append("</div><div class=\"body\">").append(esc(c.getBody())).append("</div>");
            if (c.isResolved() && c.getResolutionNote() != null)
                h.append("<div class=\"muted small\">Odpověď: ").append(esc(c.getResolutionNote())).append("</div>");
            h.append("</div>");
        }
        h.append("</section>");
    }

    private static void commentForm(StringBuilder h, MenuRevision rev, String token, boolean vat, Form f, boolean sent) {
        h.append("<form class=\"card form\" method=\"post\" action=\"/menu/").append(token).append("/comments\">")
         .append("<b>Přidat komentář</b>");
        if (sent) h.append("<div class=\"banner ok\">Komentář byl odeslán.</div>");
        if (vat) h.append("<input type=\"hidden\" name=\"dph\" value=\"1\">");

        h.append("<label class=\"field\"><span class=\"label\">Vaše jméno *</span><input name=\"author\" maxlength=\"100\" value=\"")
         .append(esc(f.author())).append('"').append(errClass(f, "author")).append('>').append(error(f, "author")).append("</label>");

        h.append("<label class=\"field\"><span class=\"label\">Týká se</span><select name=\"target\"").append(errClass(f, "target"))
         .append("><option value=\"\">Celé menu</option>");
        for (MenuCategory c : rev.getCategories())
            for (MenuItem i : c.getItems()) {
                String value = c.getId() + ":" + i.getProduct().getId();
                h.append("<option value=\"").append(value).append('"').append(value.equals(f.target()) ? " selected" : "")
                 .append('>').append(esc(c.getName() + " – " + i.getProduct().getName())).append("</option>");
            }
        h.append("</select>").append(error(f, "target")).append("</label>");

        h.append("<label class=\"field\"><span class=\"label\">Komentář *</span><textarea name=\"body\" rows=\"4\" maxlength=\"")
         .append(Comment.MAX_BODY).append('"').append(errClass(f, "body")).append('>').append(esc(f.body())).append("</textarea>")
         .append(error(f, "body")).append("<span class=\"help\">Nejvýše ").append(Comment.MAX_BODY).append(" znaků.</span></label>")
         .append("<button class=\"btn\" type=\"submit\">Odeslat komentář</button></form>");
    }

    // ---------------------------------------------------------------- W2 and errors

    static String expired(String currentToken) {
        String action = currentToken != null
                ? "<div class=\"card form\"><span class=\"muted\">Pro tuto akci existuje aktuální nabídka.</span>"
                  + "<a class=\"btn\" href=\"/menu/" + esc(currentToken) + "\">Zobrazit aktuální nabídku</a></div>"
                : "<p class=\"muted\">Aktuální nabídka zatím není k dispozici. Kontaktujte prosím svého pracovníka cateringu.</p>";
        return message("Odkaz již není platný", "Tento odkaz vypršel nebo byl nahrazen novější nabídkou.", action);
    }

    static String unknownLink() {
        return message("Odkaz není platný", "Odkaz není platný, kontaktujte pracovníka.", "");
    }

    static String notFound() { return message("Stránka nenalezena", "Požadovaná stránka neexistuje.", ""); }

    static String badRequest() { return message("Neplatný požadavek", "Požadavek obsahuje neplatné znaky.", ""); }

    static String tooLarge() { return message("Požadavek je příliš velký", "Odeslaná data jsou příliš dlouhá.", ""); }

    static String serverError() { return message("Chyba serveru", "Zkuste to prosím později.", ""); }

    private static String message(String title, String text, String extra) {
        return page(title, "<main class=\"msg\"><div class=\"icon\">!</div><h1>" + esc(title) + "</h1><p class=\"muted\">"
                + esc(text) + "</p>" + extra
                + "<p class=\"faint small\">Potřebujete pomoc? Kontaktujte svého pracovníka cateringu.</p></main>");
    }

    // ---------------------------------------------------------------- helpers

    /** HTML escaping of text and attribute values (customer input, product and menu names). */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }

    private static String quantity(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String errClass(Form f, String field) {
        return f.errors().containsKey(field) ? " class=\"error\"" : "";
    }

    private static String error(Form f, String field) {
        String msg = f.errors().get(field);
        return msg == null ? "" : "<span class=\"errtext\">" + esc(msg) + "</span>";
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"cs\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + esc(title)
                + "</title><style>" + CSS + "</style></head><body>" + body + "</body></html>";
    }

    // tokens and rules follow the mockups (diagrams/ui-src/theme.css + web.css)
    static final String CSS = """
        :root{--bg:#F4F6F8;--border:#DDE3E8;--border-strong:#C5CDD5;--text:#1B2430;--muted:#5F6B7A;--faint:#8A95A3;
        --accent:#0F766E;--accent-soft:#E6F4F2;--ok:#15803D;--ok-soft:#E8F5EC;--warn:#B45309;--warn-soft:#FEF4E6;
        --err:#B91C1C;--err-soft:#FDECEC}
        *{box-sizing:border-box;margin:0;padding:0}
        body{background:var(--bg);font-family:Inter,"DejaVu Sans",system-ui,sans-serif;font-size:15px;color:var(--text);line-height:1.4}
        main{max-width:640px;margin:0 auto;padding:14px;display:flex;flex-direction:column;gap:14px}
        .hero{background:var(--accent);color:#fff;padding:18px 18px 20px}
        .hero>*{max-width:612px;margin-left:auto;margin-right:auto}
        .brand{display:flex;gap:8px;align-items:center;font-size:13px;opacity:.9}
        .logo{background:#fff;color:var(--accent);width:22px;height:22px;border-radius:6px;display:grid;place-items:center;font-weight:700;font-size:12px}
        .hero h1{font-size:22px;line-height:1.25;margin-top:6px}.hero .sub{opacity:.85;font-size:13px;margin-top:4px}
        .switch{font-size:13px;color:var(--muted);text-align:right}a{color:var(--accent);font-weight:600}
        .card{background:#fff;border:1px solid var(--border);border-radius:8px}
        .card-h{padding:12px 14px;border-bottom:1px solid var(--border);display:flex;gap:8px;align-items:center;font-weight:600}
        .cat{font-size:12px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);font-weight:700;padding:12px 14px 4px}
        .it{padding:8px 14px 10px;display:grid;grid-template-columns:1fr auto;gap:2px 8px;border-bottom:1px solid #EDF0F3}
        .it .n{font-weight:600}.it .p{text-align:right;font-weight:600;font-variant-numeric:tabular-nums;white-space:nowrap}
        .d{font-size:13px;color:var(--muted)}.r{text-align:right}
        .al{display:inline-block;min-width:18px;padding:0 4px;height:18px;line-height:18px;text-align:center;border-radius:4px;background:#EEF1F4;color:var(--muted);font-size:11px;font-weight:700;margin-left:2px}
        .tot{display:flex;justify-content:space-between;align-items:baseline;padding:14px;background:#F8FAFB;border-radius:0 0 8px 8px}
        .total{font-size:20px;white-space:nowrap}
        .c{padding:10px 14px;border-bottom:1px solid #EDF0F3;display:flex;flex-direction:column;gap:3px}
        .c:last-child{border:0}.c .body{white-space:pre-wrap;overflow-wrap:anywhere}
        .row{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.row .chip{margin-left:auto}
        .chip{display:inline-flex;align-items:center;height:22px;padding:0 8px;border-radius:11px;font-size:12px;font-weight:600;background:#EEF1F4;color:var(--muted)}
        .chip.ok{background:var(--ok-soft);color:var(--ok)}.chip.warn{background:var(--warn-soft);color:var(--warn)}
        .form{padding:14px;display:flex;flex-direction:column;gap:12px}
        .field{display:flex;flex-direction:column;gap:5px}.label{font-size:13px;font-weight:600}
        input,select,textarea{font:inherit;border:1px solid var(--border-strong);border-radius:6px;padding:8px 10px;background:#fff;color:var(--text);width:100%}
        input:focus,select:focus,textarea:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}
        .error{border-color:var(--err);box-shadow:0 0 0 3px var(--err-soft)}
        .errtext{font-size:13px;color:var(--err);font-weight:500}.help{font-size:12px;color:var(--muted)}
        .btn{display:flex;justify-content:center;align-items:center;height:44px;border-radius:6px;border:1px solid var(--accent);background:var(--accent);color:#fff;font:inherit;font-weight:600;text-decoration:none;cursor:pointer}
        .banner{padding:10px 14px;border-radius:8px;border:1px solid #BFE3CA;background:var(--ok-soft);color:#14532D}
        .muted{color:var(--muted)}.faint{color:var(--faint)}.small{font-size:12px}
        .msg{padding:44px 22px 40px;align-items:center;text-align:center;gap:16px}.msg .form{width:100%}
        .msg h1{font-size:22px}
        .icon{width:64px;height:64px;border-radius:50%;background:var(--warn-soft);color:var(--warn);display:grid;place-items:center;font-size:30px;font-weight:700}
        """;
}
