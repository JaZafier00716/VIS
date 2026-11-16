package cz.vsb.catering.presentation.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.business.service.LinkNotValidException;
import cz.vsb.catering.business.service.SharedMenuView;
import cz.vsb.catering.business.service.SharingService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Routes of the customer web (UC11 steps 4-8, 5.1-5.6, 7.1-7.3):
 *  GET /menu/{token} (W1 or W2) and POST /menu/{token}/comments. Calls only the SharingService facade.
 *  ?dph=1 picks the PriceStrategy (Strategy pattern) used for all prices on the page. */
class MenuHandler implements HttpHandler {
    private static final Pattern ROUTE = Pattern.compile("/menu/([^/]+)(/comments)?/?");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]{1,64}");
    private static final int MAX_FORM_BYTES = 64 * 1024;

    private final SharingService sharing;

    MenuHandler(SharingService sharing) { this.sharing = sharing; }

    @Override public void handle(HttpExchange ex) throws IOException {
        try {
            Matcher m = ROUTE.matcher(ex.getRequestURI().getPath());
            String method = ex.getRequestMethod();
            if (!m.matches()) {
                send(ex, 404, MenuPage.notFound());
            } else if (!TOKEN.matcher(m.group(1)).matches()) {
                send(ex, 404, MenuPage.unknownLink());               // UC11 5.1: a link that cannot exist
            } else if (m.group(2) == null && !method.equals("GET")) {
                notAllowed(ex, "GET");
            } else if (m.group(2) != null && !method.equals("POST")) {
                notAllowed(ex, "POST");
            } else if (m.group(2) == null) {
                Map<String, String> query = parse(ex.getRequestURI().getRawQuery());
                showMenu(ex, m.group(1), strategy(query), query.containsKey("ok"));
            } else {
                addComment(ex, m.group(1));
            }
        } catch (IllegalArgumentException e) {           // malformed %-escape in query or form
            send(ex, 400, MenuPage.badRequest());
        } catch (RuntimeException e) {
            e.printStackTrace();
            send(ex, 500, MenuPage.serverError());
        } finally {
            ex.close();                                              // after the catch blocks, which still answer
        }
    }

    private void showMenu(HttpExchange ex, String token, PriceStrategy strategy, boolean sent) throws IOException {
        SharedMenuView view = sharing.openSharedMenu(token);
        if (view.status() != SharedMenuView.Status.VALID) {
            sendInvalid(ex, view);
            return;
        }
        send(ex, 200, MenuPage.menu(view, strategy, MenuPage.Form.EMPTY, sent));
    }

    private void addComment(HttpExchange ex, String token) throws IOException {
        String raw = readBody(ex, MAX_FORM_BYTES);
        if (raw == null) {
            send(ex, 413, MenuPage.tooLarge());
            return;
        }
        Map<String, String> form = parse(raw);
        PriceStrategy strategy = strategy(form);
        String author = form.getOrDefault("author", "");
        String body = form.getOrDefault("body", "");
        String target = form.getOrDefault("target", "");
        try {
            Long categoryId = null, productId = null;
            if (!target.isEmpty()) {
                String[] parts = target.split(":");
                try {
                    categoryId = Long.parseLong(parts[0]);
                    productId = Long.parseLong(parts[1]);
                } catch (RuntimeException e) {
                    throw new ValidationException("Vybraná položka v nabídce není.");
                }
            }
            sharing.addComment(token, author, body, categoryId, productId);
            String dph = strategy == PriceStrategy.WITH_VAT ? "&dph=1" : "";
            ex.getResponseHeaders().set("Location", "/menu/" + token + "?ok=1" + dph + "#komentare");
            ex.sendResponseHeaders(303, -1);
        } catch (LinkNotValidException e) {
            sendInvalid(ex, sharing.openSharedMenu(token));
        } catch (ValidationException e) {
            SharedMenuView view = sharing.openSharedMenu(token);
            if (view.status() != SharedMenuView.Status.VALID) {
                sendInvalid(ex, view);
                return;
            }
            Map<String, String> errors = new HashMap<>(e.getFieldErrors());
            if (errors.isEmpty()) errors.put("target", e.getMessage());
            send(ex, 400, MenuPage.menu(view, strategy, new MenuPage.Form(author, target, body, errors), false));
        }
    }

    /** W2 for an expired/replaced link (5.2-5.3), 404 text for a link that never existed (5.1). */
    private void sendInvalid(HttpExchange ex, SharedMenuView view) throws IOException {
        if (view.status() == SharedMenuView.Status.UNKNOWN) send(ex, 404, MenuPage.unknownLink());
        else send(ex, 410, MenuPage.expired(view.currentToken()));
    }

    private static PriceStrategy strategy(Map<String, String> params) {
        return "1".equals(params.get("dph")) ? PriceStrategy.WITH_VAT : PriceStrategy.WITHOUT_VAT;
    }

    /** application/x-www-form-urlencoded (also a query string) in UTF-8; first value of a name wins. */
    static Map<String, String> parse(String encoded) {
        Map<String, String> map = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return map;
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.putIfAbsent(name, value);
        }
        return map;
    }

    /** The request body as text, or null when it is longer than max bytes (answer 413, never truncate). */
    static String readBody(HttpExchange ex, int max) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(max + 1);
            return bytes.length > max ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static void notAllowed(HttpExchange ex, String allow) throws IOException {
        ex.getResponseHeaders().set("Allow", allow);
        send(ex, 405, MenuPage.notFound());
    }

    /** CSP allows only the inline style the pages carry; no scripts, no framing, forms only to the same site. */
    static void securityHeaders(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
    }

    static void send(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
