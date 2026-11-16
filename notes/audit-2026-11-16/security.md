# Audit: security (2026-11-16)

## Progress
Done: whole app read (web handlers/pages, services, domain, Oracle mappers + Jdbc, file storage, config, deps tree). / Next: nothing, complete.
Method: code reading + `mvn dependency:tree`; nothing run against the DB or the web server. Deployment assumed per A6 (internal desktop + public customer web behind HTTPS proxy).

## Checked and OK (no finding)
- SQL: every statement goes through `Jdbc` PreparedStatement with bound `?`; the only string concatenation is of constant fragments (`SELECT + "WHERE ..."`, `COLUMNS`). The LIKE search escapes `% _ \` and uses `ESCAPE`. No SQL injection found.
- Share token: 16 bytes from `SecureRandom` = 128 bit, hex (`ShareLink.create`); route regex `[A-Za-z0-9]{1,64}` so the token cannot inject into `Location`/HTML. A new share deactivates older active links; `isValid` checks active and `validUntil`; daily scheduler deactivates expired ones. Guessing is infeasible.
- XSS: all text output goes through `esc()` (escapes `< > & " '`) in text and attribute contexts (`value="..."`, `<title>`). Option values are numeric ids built by the server; `f.target()` is only compared, never echoed. Price/number output is server-formatted. Manager pages escape names, notes, authors, resolver names. No JS in pages.
- Comment POST CSRF: the endpoint is intentionally unauthenticated (capability link), so there is no session to ride; no CSRF token needed. Body limited (`readNBytes(64 KiB)`), text limits 100/2000 chars enforced in `Comment.create`.
- Error pages: handlers return `MenuPage.serverError()` (no stack/SQL); `printStackTrace` goes only to stderr. `DataAccessException` text with SQL message never reaches the web.
- Manager session: 256-bit `SecureRandom` token, new token on every login (no fixation), logout removes it server-side, 8 h TTL checked on use, cookie `HttpOnly; SameSite=Strict; Path=/manager`, only POST routes are login/logout (SameSite=Strict covers logout CSRF). Role MANAGER enforced at login. Same generic message for unknown user and wrong password.
- Password storage: PBKDF2WithHmacSHA256, 16 B random salt, 256-bit hash, `MessageDigest.isEqual` constant-time compare.
- FileDocumentStorage key check (`normalize` + `startsWith(root)`) is correct; keys are `p<id>/<UUID>-<sanitized name>`.
- Admin operations: `UserService` checks `isActiveAdmin()` on every method (list/create/update); last-admin rule enforced. Other services (customers, catalog, projects) have no role gate, which matches A2 (worker manages them). Archived rule is enforced in domain/service (`requireNotArchived` in Document/Sharing/Menu services), not only in the UI.
- `config/db.properties` is listed in `projekt/app/.gitignore`; no code prints the DB password or any password/token (only `PasswordHasher.main` prints a hash by design).
- Dependencies (from knowledge, cannot query a CVE database offline): javafx 21.0.5 (only controls/graphics/base, no WebView, so the WebKit CVEs do not apply; 21.0.x has later patch releases, upgrade when convenient), ojdbc11 23.5.0.24.07 (Oracle fixes JDBC issues in quarterly CPUs; I am not sure of a specific CVE for this build, but it is a 2024-07 driver, so moving to the newest 23.x is cheap), zxing core 3.5.3 (no known CVE, generation only), pdfbox 3.0.3 (the app only generates PDFs and never parses untrusted ones; PDFBox parser DoS/XXE CVEs from 2024-2025 are therefore not reachable; I am not sure of exact fixed-in versions, 3.0.4+/3.0.5 exist), commons-logging 1.3.3 (none known), junit 5.11.3 test only. Confidence on all of this: moderate.

## Findings

### SEC-1 Document name is used unsanitised as target file name on download (path traversal / overwrite)
- Severity: medium (internal desktop only; needs a worker or DB writer to plant the name)
- Evidence: `DocumentService.downloadDocuments`: `storage.retrieve(d.getStorageKey(), targetDir.resolve(d.getName()))`; `NodeCreator.createIn` only checks blank and length <= 100; `FileDocumentStorage.retrieve` copies with `REPLACE_EXISTING`. A document named `../../.bashrc` (typed in the "Nový" name prompt or already in the DB) escapes the chosen folder and overwrites a file of the downloading user. (`createDocument` uses `safe(name)` for the storage key only, not for the stored/downloaded name.)
- Why: a colleague (or any DB write) can make another user's download overwrite files outside the chosen folder. The key side is protected, the name side is not.
- Fix: in `downloadDocuments` use `Path target = targetDir.resolve(d.getName()).normalize(); if (!target.startsWith(targetDir.normalize()) || !target.getFileName().toString().equals(d.getName())) throw new ValidationException(...)`, or simply `targetDir.resolve(safe(d.getName()))`. Better also reject `/ \ ..` and control characters in `NodeCreator.createIn` (one rule for folders, documents and menus, Czech message "Název nesmí obsahovat lomítka.").
- Confidence: confirmed (code path read; not run)

### SEC-2 Expired or replaced share link reveals the current valid link
- Severity: medium
- Evidence: `SharingService.openSharedMenu` returns `currentToken` for an invalid link; `MenuPage.expired(currentToken)` renders `<a href="/menu/<current>">Zobrazit aktuální nabídku`, status 410.
- Why: anyone who ever had an old link (forwarded e-mail, browser history, proxy log, a link the worker replaced on purpose) gets one click to the live link, so expiry/replacement never revokes access to the menu and its price; 14-day validity then protects nothing while a newer link exists. This follows UC11 5.2-5.3, so it is a design trade-off, not a coding bug.
- Fix: either accept and state it in the A6/risk text, or show only "Odkaz vypršel, požádejte pracovníka o nový" and drop `currentToken` (then UC11 5.3 changes; decide with John). At minimum do not show it for links whose own `validUntil` passed more than a few days ago.
- Confidence: confirmed

### SEC-3 Web server DoS: 4 fixed threads, no read timeouts
- Severity: medium (public customer link; mitigated if the proxy buffers requests)
- Evidence: `WebServer`: `Executors.newFixedThreadPool(4)` (unbounded queue), `HttpServer.create(new InetSocketAddress(port), 0)`; handlers call `readNBytes` on the body in the pool thread. The JDK HttpServer has no default request/body timeout (`sun.net.httpserver.maxReqTime` is off).
- Why: four connections that send headers and then trickle the body occupy all workers and the customer and manager pages stop answering; also the pool is shared by customer and manager routes.
- Fix: front the app with nginx/Caddy that buffers request bodies and has client timeouts (note it in A6); in code set `-Dsun.net.httpserver.maxReqTime=30 -Dsun.net.httpserver.maxRspTime=30` (or `System.setProperty` in `WebMain`) and use a bounded `ThreadPoolExecutor` queue. Do not use virtual threads or more complexity.
- Confidence: confirmed for the code; the exact timeout behaviour of the JDK is from knowledge (suspected on the numbers)

### SEC-4 /manager is served on the same public listener as the customer links
- Severity: medium
- Evidence: `WebServer(AppContext, port)` registers `/manager` on the one `InetSocketAddress(port)` (all interfaces); `WebMain` and the desktop app both start it. There is no host/IP restriction or separate port.
- Why: the proxy exposing `/menu/` to the internet also exposes the manager login (full project list with customers and prices) unless every proxy config blocks `/manager`. A forgotten rule = public admin-ish login on a page with no throttling (SEC-5).
- Fix: document in A6/deployment "proxy forwards only `/menu/`", or bind `/manager` to a second `HttpServer` on `127.0.0.1`/internal port (small change: second port in `db.properties`, `web.managerPort`). KISS option: the doc line plus a proxy snippet in the notes.
- Confidence: confirmed

### SEC-5 No login throttling on manager login; PBKDF2 cost makes it a CPU lever
- Severity: low-medium
- Evidence: `ManagerHandler.login` has no attempt counter/delay/lockout; every attempt does a DB lookup and (for existing users) a PBKDF2 run. All seed users share the password `heslo123` (`TDS/db/02_seed.sql`), including the manager.
- Why: online guessing of a manager account is unlimited; 4 worker threads can also be saturated with login attempts. Only MANAGER role can get a session, which limits the damage to read access.
- Fix: in-memory counter per username and per remote address (`ex.getRemoteAddress()`, behind the proxy use `X-Forwarded-For` only if the proxy is trusted): after 5 failures in 10 min answer 429 for 10 min (about 15 lines in `ManagerHandler`); and require changing seed passwords before deployment (document it).
- Confidence: confirmed

### SEC-6 Session cookie lacks `Secure`; session keeps stale user state
- Severity: low
- Evidence: `Set-Cookie: mgr=...; Path=/manager; HttpOnly; SameSite=Strict; Max-Age=...` (no `Secure`). `Session(User user, ...)` keeps the `User` object loaded at login for 8 h.
- Why: behind an HTTPS proxy the browser sends the cookie over HTTPS anyway, but without `Secure` it is also sent on any plain-HTTP request to the host (e.g. a downgrade or typo of the URL). Deactivating a manager or removing the MANAGER role in the desktop app does not end an open web session for up to 8 h.
- Fix: add `; Secure` when `web.baseUrl` starts with `https://` (local HTTP dev stays working); on each request re-read the user (`auth`/`UserMapper.findById`) or store the user id and check `isActive() && hasRole(MANAGER)`; or shorten the TTL to 2 h.
- Confidence: confirmed

### SEC-7 PBKDF2 iteration count is low by current guidance
- Severity: low
- Evidence: `PasswordHasher.ITERATIONS = 65_536` (HmacSHA256). OWASP 2023+ recommends 600 000 for PBKDF2-HMAC-SHA256.
- Why: weaker resistance to offline cracking if the `users` table leaks. The stored format already carries the iteration count, so raising it is backwards compatible. (Raising it also makes SEC-5 cost more per attempt: about 10x, still only tens of ms.)
- Fix: set 600_000 and rehash on successful login when `iterations < ITERATIONS` (small change in `AuthService.login` plus `UserMapper.update`); keep seed hashes working. Optional for a semester project, mention in defence.
- Confidence: confirmed (value); the OWASP figure is from my knowledge

### SEC-8 Comment endpoint has no rate limit / spam control
- Severity: low
- Evidence: `MenuHandler.addComment` -> `SharingService.addComment`: unlimited comments per token and per IP, each up to 2000 chars.
- Why: whoever has (or leaks) a link can fill the comments table and bury real comments; workers see them in the comments window. No data exposure.
- Fix: cap comments per link (e.g. 200, in `SharingService.addComment` with a `countComments` query) with the Czech message "Dosažen maximální počet komentářů.", and/or the same simple per-IP throttle as SEC-5 / proxy `limit_req`.
- Confidence: confirmed

### SEC-9 Missing security headers on the web pages
- Severity: low
- Evidence: `send()` in both handlers sets only Content-Type (and `Cache-Control: no-store` on the manager). No `X-Content-Type-Options`, `X-Frame-Options`/`frame-ancestors`, `Referrer-Policy`, CSP; the customer token is in the URL.
- Why: inline `<style>` is used, no scripts, so XSS risk is already low; headers are defence in depth and stop framing of the comment form and token leakage through `Referer` if an external link is ever added.
- Fix: one helper in the two `send()` methods (or in the proxy): `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.
- Confidence: confirmed

### SEC-10 `config/db.properties` is world-readable and holds the DB password in clear text
- Severity: low
- Evidence: `stat`: mode 664 (`-rw-rw-r--`), contains `db.password`; `.gitignore` excludes it. Note: `/home/jan/Documents/VSB/tretak/VIS` is currently not a git repository, so the ignore rule has no effect until one exists (`git status` would show it as untracked, never add it).
- Why: any local user can read the DB password on a shared machine; the secret sits next to the code folder that is zipped for submission.
- Fix: `chmod 600 config/db.properties`; for deployment allow the password from an env var/`-D` property as an override (a 2-line change in `AppContext.load`); make sure the submission ZIP excludes `config/db.properties` and `documents/`.
- Confidence: confirmed

### SEC-11 Item notes are shown to the customer
- Severity: low (design question)
- Evidence: `MenuPage.menu`: `if (i.getNote() != null ...) h.append(" · ").append(esc(i.getNote()))`.
- Why: if workers use the item note for internal remarks (cost, supplier), the customer sees it. Only a problem if that is not intended.
- Fix: confirm with John that item notes are customer-facing; if not, drop the line or hide it. Add a hint to the editor label ("viditelné zákazníkovi").
- Confidence: suspected (depends on intent)

## Seen outside my area
- `TDS/db/02_seed.sql:3` header comment still says passwords are "SHA-256 hex" while line 16 and the code use PBKDF2 (stale comment, tds-compliance/docs).
- `ManagerHandler.showProject` reloads all projects (`findAllProjects().stream().filter`) and `collectMenus` does 2+ queries per menu per request (performance, not security; the code comment already says shortcut).
- `ManagerHandler.showList` / `showProject` swallow nothing but `openLatestRevision` throws `ValidationException` for a menu without revision inside `collectMenus`, which would give a 500 on the project page (correctness-presentation could check).

## Summary
high 0, medium 4 (SEC-1, SEC-2, SEC-3, SEC-4), low 7 (SEC-5 .. SEC-11). No SQL injection, no XSS, no token weakness found.
