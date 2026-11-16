package cz.vsb.catering.presentation.web;

import com.sun.net.httpserver.HttpServer;
import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.service.SharingService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Web UI on the JDK HttpServer: customer pages (UC11, UC12) and the read-only manager overview (UC15).
 *  Port 0 binds a free port, port() tells which. A small thread pool serves several users at once
 *  (A3: up to 3 managers concurrently); each request gets its own DB transaction on its thread. */
public class WebServer {
    private final HttpServer server;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    /** Customer pages only. */
    public WebServer(SharingService sharing, int port) {
        // The JDK default is no limit, so one slow client could hold one of the 4 threads forever.
        System.setProperty("sun.net.httpserver.maxReqTime", System.getProperty("sun.net.httpserver.maxReqTime", "30"));
        System.setProperty("sun.net.httpserver.maxRspTime", System.getProperty("sun.net.httpserver.maxRspTime", "30"));
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Nelze otevřít webový port " + port, e);
        }
        server.createContext("/", new MenuHandler(sharing));
        server.setExecutor(pool);
    }

    /** Customer pages plus the manager overview under /manager. */
    public WebServer(AppContext ctx, int port) {
        this(ctx.sharing, port);
        server.createContext("/manager", new ManagerHandler(ctx.auth, ctx.projects, ctx.menus, ctx.sharing,
                ctx.baseUrl.startsWith("https://")));
    }

    public void start() { server.start(); }

    public void stop() {
        server.stop(0);
        pool.shutdown();
    }

    public int port() { return server.getAddress().getPort(); }
}
