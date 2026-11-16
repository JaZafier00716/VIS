

Obecné

    Není povoleno při řešení vašeho IS používat frameworky hotových řešení, Např.  MVC, Hibernate, NHibernate, MVVM, MVP a další. Vše bude programováno vaším kódem. Pro webové rozhraní je povoleno ASP.NET bez dalších nadstaveb.
    Jediné povolené prostředí, ve kterém je programován IS je C# nebo JAVA. Tento požadavek vychází z toho co máte na výběr z předmětů.
    Projekt z DAIS nelze použít při tvorbě IS. Můžete využít námět, tedy co má systém dělat, programovat budete na základě objektového paradigmatu s využitím vzorů GoF a Enterprise architektury, postupem shora-dolů.
    Každý artefakt bude součástí konečné dokumentace, věnujte pozornost pravopisu a i vzhledu dokumentu co odevzdáváte. Máme nadpisy, odstavce a další typografické nástroje, aby dokumenty byly čtivé a odpovídaly výstupům od studentů VŠ.
    Od Artefaktu 2 a dále, zasílejte pouze konkrétní dotazy, není možné zaslat Artefakt bez dotazu nebo s textem je to tak dobře? Vzhledem k počtu studentů jsem schopen odpovídat pouze na konkrétní otázku. Nezapomínejte uvést své osobní číslo a dodržujte dohodnutý formát předmětu emailu.
    V případě, že potřebujete Vizi doplnit o popis nějaké další funkcionality, je to možné, nelze, ale změnit Vizi úplně na jiný IS.
    Finální verze dokumentace a naprogramovaného IS by měla odpovídat skutečnosti.
    Ukázkový projekt z minulých let nebude dán k dispozici. Je třeba postupovat podle FAQ, prezentací ze cvičení, samostudia a případných dotazů na cvičícího.
    Nebudu posílat potvrzení, že jsem artefakty přijal. Budu brát poslední poslaný stav k termínu odevzdání jako finální.



Artefakt 1: Vize IS

    Vize popisuje systém z pohledu zákazníka, jedná se tedy o zadání toho, co má IS dělat a jaké má poskytovat funkce. Z tohoto dokumentu se bude IS tvořit. Budete podle něj vytvářet UseCase diagram a případy užití, které budete implementovat. Popište si ve vizi všechno nezbytné k tomu, aby jste splnili požadavky na IS. Otázky Kdo, Kdy, Jak, Proč …. jsou pouze pro vás, aby jste si na ně odpověděli, ve vlastní vizi - artefaktu by neměly figurovat.
    Ve vizi nezapomeňte uvést kdo pracuje se kterým UI. Tedy například zákazníci objednávají přes webové rozhraní IS a personál pracuje v desktopové aplikaci.
    Při návrhu technických požadavků budete potřebovat vědět, kolik je odhadovaný počet uživatelů, kolik jich pracuje současně, případně kolik je provozoven a jaký je plán do budoucna na rozšíření. Toto vám umožní v dalších artefaktech navrhnou a vybrat databázový server, případně vytvořit deployment na více počítačů v rámci jedné vrstvy. Nebojte se pustit uzdu fantazii. Ve starších projektech byl programován např. IS pro lékárny či nemocnice nebo banky.
    Při psaní Vize, si rozmyslete, které tři případy užití budete programovat ve vašem IS. Bylo by vhodné, aby jste tyto případy užití ve Vizi rozepsali více do podrobna. Například, pokud budete implementovat objednávku výpůjčky kola, tak můžete v rámci toho případu užití řešit, že je nedostatek kol na určeném stanovišti a co se v takovém případě bude dít - zákazník bude informován o nedostatku volných kol a bude mu nabídnuto nejbližší stanoviště s dostatkem kol. To pak použijete v UseCase description jako základní a alternativní průběh.
    Poučení: do vize nepíšeme doslova UseCase, jeho průběh, alternativní průběh, metody, atributy apod. Má to být slovní popis, jak je uvedeno v předchozí odrážce!!! Uvědomte si, sepisujete požadavky se zákazníkem, musí to být srozumitelné, lidsky vyjádřené, aby tomu každý rozuměl!!!



Artefakt 2: Funkční specifikace

    Artefakt 2 obsahuje tyto části:
    Use-Case diagram celého navrhovaného informačního systému, minimální rozsah je od 15 do 20-25 případů užití.
    Textový popis tří (ideálně pěti pro finalní implementaci dle požadavků) vybraných případů užití. Tyto případy užití tvoří netriviální funkcionalitu, není povoleno případ užití Přihlášení do systému.
    Každý případ užití obsahuje minimálně jeden alternativní scénář, s rozsahem funcionality minimálně pět kroků.
    Pro každý ze tří (ideálně pěti) vybraných scénářů užití bude vytvořen jeden diagram aktivit. Tento diagram bude obsahovat hlavní i alternativní průběh.
    Existuje norma pro UML https://www.omg.org/spec/UML/2.5.1/PDF zde najdete jak se mají diagramy kreslit společně s ukázkami. Dodržujte tuto normu při kreslení vašich diagramů. Pro kreslení lze využít mnoho volně dostupných nástrojů pro kreslení UML diagramů.
    Popis případů užití najdete ve slajdech z přednášek. Podotýkám, že tam není ukázán alternativní průběh. Takže jej nezapomeňte doplnit a to tak, že jednotlivé číslované kroky případu užití budou v alternativním průběhu číslovány jako 3.1, 3.2, 3.3.
    V Use-Case diagramu celého systému doporučuji kromě názvu případu užití, jej doplnit také číselným označením. Tedy například 01 - Vytvoření objednávky.
    Popis případů užití má stanovenou formu, tak ji dodržujte. Obsahuje Název, Textový popis, Účastníci případu užití, Podmínky před spuštěním, Hlavní průběh, Alternativní průběh a stav systému po ukončení případu užití.
    Do “Stavu systému po ukončení případu užití” popište, co se v systému změnilo a co můžeme skutečně zkontrolovat. Např. do systému byla vložena/odstraněna nová objednávka, uživatel byl přidán do seznamu obesílaných uživatelů při změně stavu objednávky, bylo zalogováno zrušení objednávky. Toto budu kontrolovat při obhajobě úkolu a budete mi ukazovat, že se skutečně stav dostal do uložiště.
    Každý krok případu užití by měl něco dělat v kódu, takže jde o volání nějaké činnosti.
    Diagram aktivit by měl obsahovat “Swimlines” tím se ukáže interaktivita mezi systémem a uživatelem, případně dalšími případy užití. V tomto diagramu tak můžete zachytit vizualně to, kdo vyvolává kterou činnost v případu užití.
    V Use-Case diagramu celého IS by se neměl vyskytovat IS v roli aktora. Pokud existuje nějaká akce, která je spouštěná systémem, často je to realizováno buď aktorem “Čas“ nebo se jedná o kandidáta na extend vazbu, ta je vyvolávána z UC na který je UC napojen.
    Operace přidání, aktualizace, odebrání a vytvoření lze umístit do jednoho UC se stereotypem CRUD.
    V Use-Case diagramu jsou UC spojeny s Aktorem v případě, že je může aktor vyvolat. Lze si to představit jako menu. Tedy uživatel přijde na stránky a může vyvolat UC přihlášení nebo UC registrace. UC vytvoření objednávky je také spojeno s aktorem, ale podmínky pro spuštění obsahují požadavek, že uživatel musí být přihlášený. Může samozřejmě existovat i situace, kdy může vyvolat objednávku i nepřihlášený uživatel, ale v rámci provádění UC se může vyvolat přihlášení do systému, pak bude UC přihlášení do systému inkludováno do UC objednávky.



Artefakt 3: Technická specifikace

    Všechny volby musí být zdůvodněny alespoň jednou větou.
    Tabulka s výpočtem velikostí zabraného místa pro jednotlivé entity - zkuste popřemýšlet o tom, kolik znaků v UNICODE potřebuje např. název firmy.
    Podle velikosti zabraného místa zvolte správné uložiště (ze dvou, které budete implementovat tedy např. SQL a XML).
    První model domény (konceptuální diagram tříd) vytváříte podle Vize a Artefaktu 2 a znalosti modelovaného systému. Je celkem jasné, že z půl stránky Vize nenajdete všechny hlavní doménové třídy. Je třeba se zamyslet a zkusit popřemýšlet co budete pro splnění Vašich případů užití potřebovat. Protože se jedná o konceptuální model pro zachycení vazeb je nutno zaznamenat i násobnosti a vztahy mezi těmito třídami. Pak můžete vypočíst z odhadnutého počtu operací/transakcí uživatelů i odpovídající datové velikosti.
    Není třeba počítat přesně na bajty, jde nám opět o řádové hodnoty, tedy zda je výsledná hodnota  1MB nebo 100 MB nebo třeba 100 GB.
    Zdůvodnění použití technologie je na vás, zkuste si představit a popsat důvody. Např. Zadavatel využívá pronajaté servery s více platformami, proto je zvolena technologie JAVA. Nebo naopak využívá technologie na založené Microsoft AZURE proto je zvolena technologie .NET CORE, atd. Zkuste zapojit fantazii, ale tak, aby to bylo zdůvodnění vaší volby.
    Obdobné zdůvodnění použijte i u vybraného uložiště, tedy proč MS SQL verze 2016 a vyšší, proč XML.
    U požadavků na stanice a server uvádějte i verze podporovaného OS, prohlížeče, frameworku .NET/JRE. Je to obdobné jako požadavky na sw, který si kupujete. Např Android verze 4.0 a vyšší. Pokud máte webovou aplikaci, tak musí existovat někde webový server a tedy opět jaký webový server ve které jeho verzi požadujete pro běh a opět zdůvodnění.
    Artefakt musí také obsahovat informace o použitých EA vzorech, tedy o použití doménového modelu, jaké metody přístupu k datům zvolíte (který vzor) a informaci o použití vícevrstvé architektury. Kromě toho, by jste měli zvolit i použité návrhové vzory GoF. Tedy např. použití Singletonu, připojení událostí publisher-subscriber, Factory pro vytváření tříd, Facade, případně další. Kombinace vzorů máte ve slajdech z přednášek.
    Tabulka velikostí dat, by měla obsahovat smysluplné hodnoty. Není reálné, aby byla doménová třída zákazník s velikostí 50 bajtů. Do této velikosti se nevejde ani adresa v UTF. Zaokrouhlujte tedy nahoru na stovky bajtů.
    Zkontrolujte si, že konceptuální model systému máte nakreslen jako diagram tříd s vlastnostmi, co jsou v prezentaci. Je zcela nepřípustné tento model odevzdat ve formě ER diagramu (DAIS). Je to úplně něco jiného, byť to jsou “obdélníky propojené vazbou”.



Artefakt 4: Skica, návrh UI

    Tento artefakt představuje návrh uživatelského rozhraní pro obě požadovaná UI, tak aby bylo možné provést vaše vybrané tři případy užití.
    Nezapomeňte, že je třeba funkcionalitu prezentovat v obou UI, zvolte si které případy užití budete prezentovat ve kterém UI. Např. dva případy užití ve webovém rozhraní a jeden případ užití v desktopové aplikaci. Na tento scénář vytvořte návrhy UI. Obecně platí, že v případě dobrého návrhu systému, oddělení vrstev, nic nebrání všechny případy užití realizovat v obou UI.
    Tyto návrhy lze vytvořit i nákresem na papíru a oskenovat, nezapoměňte v UI popsat, co která část obsahuje. Návrh UI by měl obsahovat sekvenci obrazovek (přechody mezi obrazovkami), pokud váš případ užití bude zobrazovat několik kroků interakce uživatele a systému.
    Jedná se o návrh, tedy nemusí ještě stoprocentně odpovídat tomu, jak to bude ve finále. Můžete samozřejmě využít i různá kreslítka UI nejen papír a tužku, i když v praxi je to často využívaný způsob.
    Tyto návrhy UI vycházejí z dobře popsaných případů užití a samozřejmě by měly odrážet i požadavky zadavatele a reflektovat specifika dané problémovou doménou. Tedy popisky, velikosti zadávaných údajů, výběrové možnosti, zvyklosti a jiné.

Artefakt 5: Návrh doménového modelu

    Nástroje pro kreslení UML diagramů, některé jsou trial některé jsou open source:
    Enterprise Architekt https://sparxsystems.com/
    Visual Paradigm https://www.visual-paradigm.com/solution/freeumltool/
    Modelio https://www.modelio.org/
    UMLet https://www.umlet.com/
    Microsoft Visio
    PlantUML https://plantuml.com/
    Concept Draw Diagram https://www.conceptdraw.com/products/drawing-tool
    Visual studio 2019 community edition obsahuje Class designer - můžete graficky vytvořit třídu a ta se promítne do kódu a naopak, co změníte v kódu to se promítne do obrázku.
    Datová vrstva (gateway, mappery) obsahují pouze metody pro práci s uložištěm. V DAL vrstvě se nesmí nacházet logika aplikace. Například vytvoření rezervace se odehrává na úrovni tříd/objektů (obchodní/doménová logika) tedy se musí odehrávat pouze v BL. Pozor je to jiné než v DAIS.
    Při použití DataMapperů se může jedna třída ukládat do více fyzických tabulek, v případě použití DTO, tak DTO obsahuje informace o více různých třídách.  Obdobné platí o použití dědičnosti, i zde se dle implementace vzoru pro OR mapování může potomek ukládat buď včetně vlastností předka nebo se část vlastností předka uloží do samostatné tabulky a část vlastností se uloží do tabulky potomka a vytvoří se mezi nimi relace pomocí cizích klíčů.
    Sekvenční diagramy zachycují celou interakci s uživatelem, tedy i informace o tom, že systém zobrazí např výběrové okno uživateli. Pro lepší názornost lze použít mezi uživatelem a IS tak vložený UI objekt, který tak představuje vaši prezentační vrstvu. Ukázku lze najít například na odkazu
    Statický diagram tříd odpovídá vašemu naprogramovanému IS a vychází z prvního modelu domény. Tyto tři věci by měly být ve shodě, tedy obsahovat stejné třídy. Statický diagram tříd je ale implementační, takže je výrazně bohatší než model první domény.
    Statický diagram tříd bude zobrazovat pouze obchodní logiku vašeho IS. Třídy, vazby metody a vlastnosti tam dejte jen ty co potřebujete pro provedení vašich vybraných UC. Dále by měl Statický diagram tříd obsahovat i vaši datovou vrstvu.
    Doporučuji vaši DAL dát do samostatného diagramu a do něj dát ukázku jak přistupujete k datům, tedy například jeden Maper do DB, jedno DTO a další Mapper do XML. Případně TableDataGateway a samozřejmě třídy pro připojení k datům. Není třeba do diagramu dávat stejné mappery pro další třídy. Stačí mi jen ukázka jak jste to realizovali ve skutečnosti.
    Již odevzdané artefakty lze měnit aby odpovídalý skutečnosti tak jak jste váš IS programovali.
    Pokud ve vašich artefaktech píšete, že používáte určité návrhové vzory, tak počítejte s tím, že je budu chtít vidět i v kódu.
    Pozor na to, kde máte umístěny DataMappery a kde TableDataGateway. Pořádně si to rozmyslete, viz. diagramy z přednášek. Mají na to vliv závislosti, vstupní a výstupní objekty.
    Pokud používáte diagramy tříd z Visual Studia, přepněte zobrazení tak, aby byly vidět asociace jako čáry, kreslíte diagram tříd, takže by tam měly být vidět i asociace, dědičnost, rozhraní. Pro korektní zobrazení kompozice/agregace by bylo potřeba dokreslit (plný/prázdný) kosočtverec do diagramu tam kam patří.
    Dávejte do dokumentace obrázky bez barevného (hlavně černého) pozadí.
    Nezapomeňte na krátký popis použitých vzorů.

Artefakt 6: Popis architektury systému

    Nezapomeňte, že i diagramy v tomto artefaktu jsou UML a tedy musí být zakresleny podle normy UML 2.0.
    Diagramy komponent - nejjednodušší dělení je po vrstvách tedy jedna komponeneta PL, další BL a poslední DAL, dále může být komponenta samotný databázový server. Doporučuji, ale tento jednoduchý model ještě doplnit o vnitřní členění. Tedy PL bude obsahovat komponentu WinForm App a komponenty Web App. BL bude obsahovat například Správa uživatelů, Evidence volných pokojů, Evidence rezervací …. , Komponenta DAL pak bude obsahovat TDG pro MSSQL server, TDG pro XML soubor a Komponentu pro DB připojení.
    Diagram nasazení - podle něj by technik měl být schopen Vaši aplikaci nainstalovat a musí poskytnou informaci, kde se nachází které části Vašeho systému.
    Jsou dvě možné varianty diagramu:
    Podle skutečnosti jak to budete prezentovat - tedy všechno je na jednom počítači. V tom případě nezapomeňte, že aby běžela Vaše aplikace, tak tam musí být OS/ nainstalovaný SQL server/ webový server a pak také vaše komponenty z předchozího diagramu. I v případě, že to budete ukazovat na jednom počítači, tak stále existuje možnost se připojit na váš počítač externím počítačem s webovým klientem. Takže tam bude i jeden klientský počítač pro webovou aplikaci.
    Tak, jak by to skutečně běželo v reálném nasazení. Samostatné počítače jeden s DesktopApp, další s Webovým prohlížečem, počítač (Aplikační server) kde leží BL a DAL komponenty vašeho systému společně s webovým serverem a další počítač, kde je nainstalován databázový server, případně uložiště souborů.
    Kterou verzi diagramu si zvolíte je na vás, nicméně by měla odpovídat velikosti vašeho systému.

Artefakt 7: Implementace vybrané části IS

    V IS lze používat technologie .NET jako je LINQ to SQL, ADO.NET, DataTable, DataSet. Nelze používat EntityFramework (Jedná se o ORM systém) a projekty typu MVC.
    Ukázkový projekt a části kódu jsou dostupné na GIThubu pro C# a VS2019 community edition. Je to pouze ukázka jak to může vypadat nikoliv celý projekt. Repozitář je zde https://github.com/MartinRaSt/VIS_course je nastaven jako template, takže si ho můžete stáhnout a podívat se jak to je řešeno.
    Nezbytná funkcionalita k prezentaci:
    Ukázka funkcionality podle vašich UC včetně alternativního průběhu.
    Ukázka funkcionality IS ve dvou různých UI.
    Podpora dvou různých datových uložišť. Musí být provedeno pomocí vzorů pro přístup k datům. Nikoliv tedy uložení konfiguračního souboru.
    Plně dopracovaná dokumentace odpovídající naprogramovanému IS.
    Použití znalostí z předmětu VIS. Tedy vzory pro podnikové aplikace, použití vzorů GoF. Objektový návrh informačního systému.
    Odevzdává se pdf s celou aktuální dokumentací a zip s naprogramovaným IS bez zakázaných příloh.

