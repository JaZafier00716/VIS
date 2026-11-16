# Audit: scans (mechanické kontroly)

## Progress
Done: scan 1 (TODO/FIXME/XXX/shortcut), scan 2 (System.out/err, printStackTrace), scan 3 (SQL concatenation in data/oracle), scan 4 (unreferenced classes and service methods), scan 5 (service methods without test call), scan 6 (JaCoCo 0.8.12 under both locks, 276 tests passed).
Next: none. Final summary at the bottom.

## Výsledky skenů (přehled)

### 1. TODO / FIXME / XXX
Žádný výskyt `TODO`, `FIXME` ani `XXX` v `src/` (jediný hit je binární font `DejaVuSans.ttf`).

### 1b. Komentáře `shortcut:` (informativně)
- `business/service/CustomerService.java:63` – `// shortcut: scans all customers, fine for hundreds; add findByEmail to the mapper when it grows`. Kontrola unikátnosti e-mailu načítá všechny zákazníky. Záměrné, pro semestrální rozsah v pořádku.
- `business/service/DocumentService.java:153` – `// shortcut: an orphaned file only wastes space; a cleanup job could remove it later`. Při chybě mazání souboru se chyba tiše ignoruje (`catch (IOException ignored)`). Záměrné, ale viz SCANS-3 (ticha).
- `presentation/web/ManagerHandler.java:160` – `// shortcut: filters all projects in memory; move to ProjectService.searchProjects once it exists`. Komentář je zastaralý, viz SCANS-1.

### 2. System.out / System.err / printStackTrace v src/main
| Místo | Výpis | Hodnocení |
|---|---|---|
| `presentation/web/WebMain.java:14` | `System.out.println("Webové rozhraní běží na ...")` | Záměrný startovní výpis URL. OK. |
| `presentation/web/ExpiredLinkScheduler.java:36` | `System.out.println("Zneplatněno prošlých odkazů: " + count)` | Záměrný provozní výpis. OK. |
| `presentation/web/ExpiredLinkScheduler.java:39` | `System.err.println("Zneplatnění ... selhalo: " + e.getMessage())` | Záměrné hlášení chyby, bez stack trace. OK. |
| `presentation/desktop/Ui.java:152` | `System.out.println("Screenshot: " + file)` | Dev-only screenshoty (`-Dcatering.screenshot`), gated v `DesktopApp`. OK. |
| `presentation/desktop/Ui.java:154` | `System.err.println("Screenshot failed: ...")` | Dev-only. OK. |
| `business/domain/PasswordHasher.java:53` | `System.out.println(hash(args[0], ...))` | Pomocné CLI `main` pro generování seedu. Viz SCANS-4. |
| `presentation/desktop/Ui.java:69` | `e.printStackTrace()` | Leftover, viz SCANS-2. |
| `presentation/web/ManagerHandler.java:95` | `e.printStackTrace()` | Leftover, viz SCANS-2. |
| `presentation/web/MenuHandler.java:49` | `e.printStackTrace()` | Leftover, viz SCANS-2. |

### 3. SQL skládané konkatenací / String.format (data/oracle)
Žádné `String.format` nad SQL. Všechny případy `+` u SQL jsou konstanty (názvy sloupců, tabulek, text bloky), uživatelský vstup je vždy vázán přes `?`:
- `OracleProductMapper.java:25` `"SELECT " + COLUMNS + " FROM products ..."`: konstanta. OK.
- `OracleProductMapper.java:75` `"... WHERE product_id = ? " + "ORDER BY ..."`: konstanta. OK.
- `OracleMenuMapper.java:28` `CONTENT = """...""" + OracleProductMapper.COLUMNS + """..."""`: konstanta. OK.
- `OracleMenuMapper.java:55, 60, 66, 123, 131` (`HEADER + "AND revision_no = ?"`, `COMMENTS + "WHERE ..."`): konstanty, hodnoty vázané přes `?`. OK.
- `OracleMenuMapper.java:101-105` `sql + " AND created_at = ?"`: konstanta, hodnoty vázané přes `?`. OK.
- `OracleShareLinkMapper.java:17, 22, 28`, `OracleUserMapper.java:25, 30, 35`, `OracleCustomerMapper.java:17, 22`, `OracleProjectMapper.java:23, 28`: konstanty + `?`. OK.
- `OracleProjectMapper.java:50-64` `search(...)`: `like` se skládá z uživatelského textu, ale je jen escapován (`\`, `%`, `_`) a předán jako vázaný parametr. Dynamické SQL nevzniká. OK. Podmínky jsou pevné, jen `?`.
Závěr: žádná SQL injection cesta. Nic k reportu jako medium.

### 4. Nepoužité třídy a metody
Třídy v `src/main` bez odkazu z jiné třídy v `src/main`:
- `data/file/InMemoryDocumentStorage.java` – používá se jen v testech (`DocumentServiceTest`, `MenuExportServiceTest`, `ArchivedProjectServiceTest`). Viz SCANS-5.
- `presentation/web/WebMain.java` – vstupní bod (`main`), záměrně bez odkazu.

Veřejné metody `business/service`, které `src/main` nevolá (jen testy):
- `SharingService.findValidLink(long)` – `main=0`, volaná jen z testů. Viz SCANS-6.

Metody, které `src/main` volá jen přes konstruktor/presentaci, byly ověřeny, nic dalšího nenalezeno. U obecných jmen (`create`, `update`, `delete`, `search`, `title`) je grep jen podle slova, takže počet referencí je orientační.

### 5. Veřejné metody services bez testu
Skript prověřil 49 veřejných metod ve 12 třídách `business/service`. Kromě konstruktorů a `fileName` každá má alespoň jedno volání `.metoda(` nebo `::metoda` v `src/test`.
- `MenuExportService.fileName(Menu,int)` – `public static`, volá ji jen `MenuExportService.exportPdf` (interní) a `presentation/desktop/MenuPdfExport.java:19`. Žádný přímý test. Viz SCANS-7.

Pozor: u generických jmen (`create`, `update`, `delete`, `findAll`, `search`, `title`) skript kontroluje jen název, ne třídu volající metody. Vysoká pokrytost těchto metod v JaCoCo (viz 6) ale potvrzuje, že se volají.

### 6. JaCoCo 0.8.12 (bez úpravy pom.xml)
- Příkaz: `flock db.lock flock mvn.lock env DISPLAY=:1 JAVA_HOME=~/.jdks/openjdk-27.0.1.0.3 mvn -B org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`.
- První pokus selhal kvůli nedostupné DB (ORA-12514 / ORA-01109, kontejner `vis-oracle` se právě startoval). Po otevření DB (ověřeno read-only SELECTem) běh prošel. Kontejner nebyl restartován ani změněn.
- Výsledek testů: **Tests run: 276, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS.**
- JaCoCo 0.8.12 na JDK 27 hlásí `Unsupported class file major version 71` jen při instrumentaci JDK tříd (`java/sql/*`). Jde o varování, reporty se vygenerují a třídy projektu (release 21) jsou v pořádku. Pro JaCoCo 0.8.13 jsem zkoušet nemusel.
- Celkem (`target/site/jacoco/jacoco.csv`, 112 tříd): **řádky 88,8 % (3173/3575), větve 79,4 % (1172/1476).**

Pokrytí po balíčcích (řádky / větve):
| Balíček | Řádky | Větve |
|---|---|---|
| business.command | 100,0 % (62/62) | 100,0 % (2/2) |
| business.domain | 98,4 % (600/610) | 91,8 % (270/294) |
| business.port | 100,0 % (2/2) | – |
| business.pricing | 100,0 % (9/9) | – |
| business.service | 99,0 % (384/388) | 94,7 % (143/151) |
| data | 100,0 % (17/17) | 50,0 % (2/4) |
| data.file | 98,3 % (116/118) | 84,2 % (32/38) |
| data.oracle | 97,3 % (285/293) | 92,0 % (80/87) |
| cz.vsb.catering (WebMain/AppContext) | 91,7 % (22/24) | – |
| presentation.web | 93,8 % (441/470) | 79,6 % (191/240) |
| presentation.desktop | 78,1 % (1235/1582) | 68,5 % (452/660) |

Deset tříd s nejnižším pokrytím řádků:
1. `presentation.desktop.Prompter` 0,0 % (0/1)
2. `presentation.desktop.DeleteDialog` 0,0 % (0/20)
3. `presentation.desktop.LoginDialog` 0,0 % (0/24)
4. `presentation.desktop.MenuItemDialog` 0,0 % (0/51)
5. `presentation.web.WebMain` 0,0 % (0/6)
6. `presentation.desktop.DesktopApp` 0,0 % (0/72)
7. `presentation.desktop.Prompter$Dialogs` 9,1 % (3/33)
8. `presentation.desktop.UploadConflictDialog` 33,3 % (7/21)
9. `business.domain.Allergen` 50,0 % (1/2)
10. `presentation.desktop.MenuPdfExport` 62,5 % (5/8)

Všechny čtyři desktopové dialogy s 0 % (`DeleteDialog`, `LoginDialog`, `MenuItemDialog`, `Prompter`) jsou UI bez testu. Logika je v business vrstvě a ta je pokrytá téměř celá.

---

## Findings

### SCANS-1 Zastaralý komentář a duplicitní filtrování projektů ve webovém manažeru
- Severity: low
- Evidence: `presentation/web/ManagerHandler.java:160` `// shortcut: filters all projects in memory; move to ProjectService.searchProjects once it exists`; řádky 161–163 filtrují `projects.findAllProjects()` v paměti. `business/service/ProjectService.java:76` už `searchProjects(String, LocalDate, LocalDate, boolean)` existuje a desktop ho používá (`presentation/desktop/ProjectSearchDialog.java:89`).
- Why it is a problem: Komentář tvrdí, že metoda neexistuje, a tím zavádí čtenáře. Stejná logika (hledání) je navíc implementována dvakrát, s možnou odlišnou sémantikou (např. archivované projekty, které v manažerském hledání mohou či nemusí být vidět). Desktop a web tak mohou ukazovat jiné výsledky.
- Fix: Smazat zastaralý komentář a v `showList` použít `ProjectService.searchProjects(q, null, null, <včetně archivních podle zadání>)`. Před změnou ověřit, že `includeArchived` odpovídá požadavku pro MANAGER web. Jinak zůstane jen smazaný komentář.
- Confidence: confirmed (ověřeno grepem; sémantiku archivních projektů jsem nečetl do hloubky).

### SCANS-2 printStackTrace místo logování
- Severity: low
- Evidence: `presentation/desktop/Ui.java:69` `e.printStackTrace();`; `presentation/web/ManagerHandler.java:95` `e.printStackTrace();`; `presentation/web/MenuHandler.java:49` `e.printStackTrace();`.
- Why it is a problem: Stack trace jde na stderr, ne do odpovědi klientovi (klient dostane `MenuPage.serverError()` / obecnou chybovou hlášku), takže neleakuje do HTTP ani do UI. Je to ale nekonzistentní výstup (jinde `System.err.println`) bez úrovně a bez časového razítka. V projektu není logovací framework, takže je to spíš kosmetika.
- Fix: Sjednotit na jeden malý helper, např. `System.err.println("Chyba: " + e)` plus stack trace, nebo `java.util.logging.Logger` s jedním voláním. Nemění chování pro uživatele.
- Confidence: confirmed.

### SCANS-3 Tiché ignorování chyby při mazání souboru
- Severity: low
- Evidence: `business/service/DocumentService.java:150-155`: `try { storage.delete(key); } catch (IOException ignored) { // shortcut: an orphaned file only wastes space ... }`.
- Why it is a problem: Chyba při mazání dokumentu se zahodí, takže zůstane osiřelý soubor bez záznamu. Záměr je zdokumentovaný, ale nikde nezůstane stopa, která by to ukázala správci. Dopad je malý (zabírá místo).
- Fix: Aspoň zalogovat na stderr (stejný helper jako v SCANS-2). Shortcut komentář ponechat.
- Confidence: confirmed.

### SCANS-4 CLI `main` v doménové třídě vypisuje hash zadaného hesla
- Severity: low
- Evidence: `business/domain/PasswordHasher.java:52-54`: `public static void main(String[] args) { System.out.println(hash(args[0], HexFormat.of().parseHex(args[1]), ITERATIONS)); }`.
- Why it is a problem: Heslo jde jako argument příkazové řádky, takže je vidět v historii shellu a v `ps` pro ostatní uživatele systému. Je to pomocný nástroj pro seed, ne provozní kód. Výpis je záměrný.
- Fix: Buď přesunout `main` do `src/test` nebo do skriptu v `TDS`, nebo číst heslo ze `System.console().readPassword()`. Pro KISS stačí přesunout do testů a v dokumentaci uvést, jak se seed hash generuje.
- Confidence: confirmed.

### SCANS-5 Testovací dvojník v produkčním kódu
- Severity: low
- Evidence: `data/file/InMemoryDocumentStorage.java:13` `public class InMemoryDocumentStorage implements DocumentStorage`; v `src/main` bez odkazu z jiné třídy. Používají ho `src/test/.../business/service/DocumentServiceTest.java:14,29,97`, `MenuExportServiceTest.java:18,29`, `ArchivedProjectServiceTest.java:26,46`.
- Why it is a problem: Test double je součástí produkčního JARu a mate hranici data vrstvy a testů. Nepoužívá ho aplikace.
- Fix: Přesunout soubor do `src/test/java/cz/vsb/catering/data/file/` (balíček zůstane stejný, takže importy v testech se nemění).
- Confidence: confirmed.

### SCANS-6 Nepoužitá veřejná metoda `SharingService.findValidLink`
- Severity: low
- Evidence: `business/service/SharingService.java:72` `public Optional<ShareLink> findValidLink(long menuId)`; v `src/main` žádné volání, jen v testech (`Uc11ShareCommentTest.java:36,117`, `SharingServiceTest.java:63`, `DesktopFlowTest.java:265,300,445`, `WebServerTest.java:139`).
- Why it is a problem: Metoda je v produkci mrtvá. Testy na ni sice spoléhají, ale kód se nevyužívá v UI. Zároveň může chybět funkce (např. zobrazení aktuálního odkazu v `ShareDialog`), pokud byla zamýšlena.
- Fix: Buď ji použít v `ShareDialog` (zobrazit platný odkaz místo vytváření nového), nebo ji zjednodušit a testy přepsat na `shareRevision`. Záleží na zadání, ne na technice.
- Confidence: confirmed (že je mrtvá v produkci), suspected (úmysl).

### SCANS-7 Veřejná statická metoda `MenuExportService.fileName` bez přímého testu
- Severity: low
- Evidence: `business/service/MenuExportService.java:31` `public static String fileName(Menu menu, int revisionNo)`; volá ji `presentation/desktop/MenuPdfExport.java:19` a `MenuExportService.java:36`. Žádný test ji nevolá přímo.
- Why it is a problem: Generování názvu souboru (včetně české diakritiky a případných nepovolených znaků) je netriviální logika, která je otestovaná jen nepřímo, pokud vůbec.
- Fix: Přidat jeden asertní test v `MenuExportServiceTest` s názvem s diakritikou a mezerou.
- Confidence: confirmed (žádný přímý test), suspected (pokrytí nepřímo).

### SCANS-8 Desktopové dialogy bez testu (JaCoCo)
- Severity: low
- Evidence: JaCoCo `jacoco.csv`: `presentation.desktop.DeleteDialog` 0/20 řádků, `LoginDialog` 0/24, `MenuItemDialog` 0/51, `Prompter` 0/1, `DesktopApp` 0/72, `UploadConflictDialog` 7/21; `WebMain` 0/6.
- Why it is a problem: Jsou to UI třídy s validací vstupu (např. `MenuItemDialog`, `LoginDialog`). Business logika pod nimi je pokrytá, takže riziko je nízké, ale validace v dialozích není ověřena.
- Fix: Nepřidávat testy pro všechno (KISS). Stačí pokrýt validaci u `MenuItemDialog` a `LoginDialog` v `DesktopFlowTest`, případně to přiznat v dokumentaci testovací strategie.
- Confidence: confirmed (čísla z JaCoCo).

---

## Info (nic k opravě, pro přehled)
- `shortcut:` komentáře: `CustomerService.java:63` (kontrola e-mailu přes celé `findAll`) a `DocumentService.java:153` (osiřelé soubory). Záměrné.
- Výpisy `System.out/err`: `WebMain:14`, `ExpiredLinkScheduler:36,39`, `Ui:152,154` jsou záměrné (startovní URL, provozní hláška, dev screenshoty za `-Dcatering.screenshot`).
- SQL: žádná konkatenace uživatelského vstupu (viz 3).

## Seen outside my area
- `presentation/web/ManagerHandler.java:161-163` filtruje projekty v paměti a může vracet jiné výsledky než `ProjectService.searchProjects` (correctness-presentation / correctness-business mají zkontrolovat sémantiku archivních projektů).

## Summary
- high: 0
- medium: 0
- low: 8 (SCANS-1 až SCANS-8)
- Build: `Tests run: 276, Failures: 0, Errors: 0, Skipped: 0`. JaCoCo 0.8.12: řádky 88,8 %, větve 79,4 %.
- Soubor: `/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/scans.md`
