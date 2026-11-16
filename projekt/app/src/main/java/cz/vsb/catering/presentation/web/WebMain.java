package cz.vsb.catering.presentation.web;

import cz.vsb.catering.AppContext;

/** Standalone web mode of the application server (mvn exec:java); the desktop app starts the same WebServer
 *  only when web.embedded=true. */
public final class WebMain {
    private WebMain() { }

    public static void main(String[] args) {
        AppContext ctx = AppContext.load();
        WebServer server = new WebServer(ctx, ctx.webPort);
        server.start();
        new ExpiredLinkScheduler(ctx.sharing, ExpiredLinkScheduler.DAILY).start();    // UC18
        System.out.println("Webové rozhraní běží na http://localhost:" + server.port() + "/menu/{token}, manažer: /manager");
    }
}
