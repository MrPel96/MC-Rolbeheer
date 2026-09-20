# RolBeheer 1.1.0

Eenvoudige rollen- en permissieplugin voor Paper.

## Bouwen
**Zonder iets te installeren (GitHub):** zet deze map in een GitHub-repository. Bij elke push bouwt
GitHub Actions de plugin. Download `RolBeheer-1.1.0.jar` onder *Actions → laatste run → Artifacts*.

**Lokaal:** installeer JDK 21 en Maven, en voer `mvn package` uit. De jar staat in `target/`.
Of open de map in IntelliJ IDEA en voer Maven → Lifecycle → package uit.

Serverversie anders dan 1.21.4? Pas `paper.version` aan in `pom.xml`.

## Installeren
1. Verwijder je oude rollenplugins (LuckPerms, GroupManager, enz.). Twee rollenplugins tegelijk werken niet goed.
2. Zet de jar in `plugins/` en herstart de server.
3. Typ `/rol` in de game (je moet OP zijn).

## Hoe het werkt
- Iedere speler heeft automatisch de **standaardrol** (`speler`).
- Extra rollen geef je via het menu of met `/rol speler <naam> geef <rol>`.
- De rol met de **hoogste prioriteit** bepaalt de prefix. Bij tegenstrijdige permissies wint die rol ook.
- Per permissie: **Toegestaan**, **Verboden** of **Niet ingesteld**. Niet ingesteld betekent de standaard van de plugin, en dat is meestal alleen voor OP.
- `*` = alles, `essentials.*` = alles van die plugin, `-essentials.fly` = verboden.
- Commands zonder eigen permissie krijgen automatisch `rolbeheer.command.<plugin>.<command>`. Iedereen mag ze standaard gebruiken; zet ze op Verboden om ze te blokkeren. Ze verdwijnen dan ook uit tab-aanvulling.
- Nieuwe plugins worden automatisch herkend bij het laden. Handmatig kan met `/rol herlaad`.

## Webpaneel
Typ `/rol web` in de game of console. Je krijgt een link die één keer werkt en 5 minuten geldig is.
Daarna blijf je 12 uur ingelogd in die browser.

- **Server op je eigen computer:** werkt direct, via `http://localhost:8765`.
- **Server bij een host:** zet in `config.yml` `web.adres: 0.0.0.0` en `web.publiek-adres` op het
  IP-adres of domein van je server. De poort (standaard 8765) moet open staan bij je host.
  Het paneel gebruikt gewone http. Deel de inloglink daarom niet en gebruik dit liever niet via openbare wifi.

Elke wijziging via het paneel komt in de console, met `[Web]` ervoor.

## Bestanden
- `config.yml`: standaardrol, chatformaat, tab/naamlabel aan/uit
- `roles.yml`: rollen (handmatig bewerken mag, daarna `/rol herlaad`)
- `players.yml`: welke speler welke rollen heeft

## Let op
- Staat er een andere chatplugin (bijv. EssentialsChat)? Zet dan `chat.ingeschakeld: false`.
- Naamlabels gebruiken het hoofdscoreboard. Plugins die elke speler een eigen scoreboard geven, kunnen ze verbergen.
