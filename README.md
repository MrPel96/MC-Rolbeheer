# RolBeheer 1.8.1

Eenvoudige rollen- en permissieplugin voor Paper.

## Bouwen
**Zonder iets te installeren (GitHub):** zet deze map in een GitHub-repository. Bij elke push bouwt
GitHub Actions de plugin. Download `RolBeheer-1.8.1.jar` onder *Actions → laatste run → Artifacts*.

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

Het paneel heeft twee onderdelen, te kiezen rechtsboven:

**Rollen** — rollen maken, prefix en naamkleur instellen, permissies per plugin regelen en spelers een rol geven.

**Server** — instellingen die voorheen alleen via bestanden konden:
- *Server*: MOTD (met kleurknoppen en een live voorbeeld), maximaal aantal spelers, moeilijkheidsgraad, PvP, spelmodus, kijkafstand, spawn-bescherming en meer. Dit past `server.properties` aan. Instellingen met het label "herstart nodig" werken pas na een herstart; de rest gaat meteen in.
- *Gameregels*: spullen behouden na de dood, dag-nachtcyclus, mobs die blokken slopen, vuur dat zich verspreidt, en zo verder. Per wereld, en meteen actief.
- *Spelers en toegang*: witte lijst aan of uit en beheren, operators toekennen of afnemen, verbanningen opheffen en spelers van de server halen.

Instellingen in `bukkit.yml`, `spigot.yml` en `paper.yml` zitten er bewust niet in. Daar staan honderden technische opties die je server ook echt kapot kunnen configureren.

## Spelerscommands
De plugin levert zelf de commands die spelers verwachten, dus je hebt EssentialsX hier niet voor nodig:

| Command | Permissie | Standaard |
| --- | --- | --- |
| `/home [naam]`, `/homes` | `rolbeheer.home` | iedereen |
| `/sethome [naam]`, `/delhome` | `rolbeheer.sethome` | iedereen |
| `/spawn` | `rolbeheer.spawn` | iedereen |
| `/setspawn` | `rolbeheer.setspawn` | alleen OP |
| `/back` | `rolbeheer.back` | iedereen |
| `/warp [naam]`, `/warps` | `rolbeheer.warp` + `rolbeheer.warp.<naam>` | per warp instellen |
| `/setwarp`, `/delwarp` | `rolbeheer.setwarp` | alleen OP |
| `/kit [naam]`, `/kits` | `rolbeheer.kit` + `rolbeheer.kit.<naam>` | per kit instellen |
| `/setkit <naam> [wachttijd]`, `/delkit` | `rolbeheer.setkit` | alleen OP |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` | `rolbeheer.tpa` | iedereen |
| `/msg`, `/r` | `rolbeheer.msg` | iedereen |

- **Aantal homes**: standaard 1, in te stellen in het paneel. Wil je dat een rol er meer mag,
  geef die rol dan de permissie `rolbeheer.homes.5` (of een ander getal). Het hoogste getal telt.
- **Warps maken**: ga naar de plek en typ `/setwarp shop`. Geef daarna de rollen die er mogen komen
  de permissie `rolbeheer.warp.shop` in het paneel.
- **Kits maken**: vul je inventaris, typ `/setkit starter 86400` (dat laatste is de wachttijd in seconden)
  en geef de rol `rolbeheer.kit.starter`.
- Warps en kits verschijnen vanzelf in het paneel bij de rollen, onder de groep RolBeheer.

### Spawn
`/setspawn` zet het spawnpunt, `/spawn` brengt spelers erheen. Twee extra's staan standaard aan en zijn
in het paneel uit te zetten:

- Nieuwe spelers beginnen op het spawnpunt in plaats van ergens willekeurig in de wereld.
- Na de dood komen spelers op het spawnpunt uit, tenzij ze een bed of respawn anchor hebben.

Daarmee kan EssentialsSpawn eruit. Doe wel eerst de import hieronder, want die leest het spawnpunt uit
`plugins/Essentials/spawn.yml`.

### Spawnbescherming
Aan te zetten in het paneel. Binnen de ingestelde straal rond je spawnpunt geldt dan:

- niemand bouwt of sloopt er iets, ook geen emmers water of lava
- explosies laten de blokken daar met rust
- optioneel: geen PvP, geen mobschade, en kisten en deuren op slot

Wie de permissie `rolbeheer.spawnbescherming.bypass` heeft (standaard OP) mag alles gewoon. Geef die
permissie aan je bouwers-rol als zij er wel mogen werken.

Dit staat los van `spawn-protection` in `server.properties`. Die vanilla-variant werkt alleen rond het
wereldspawnpunt en kent alleen OP als uitzondering; deze werkt rond jouw `/setspawn` en luistert naar rollen.

### Commands uitzetten
Laat je een command liever door een andere plugin doen (bijvoorbeeld `/warp` door een warp-plugin)?
Zet het dan uit in het paneel bij Server → Commands, of in `config.yml`:

```yaml
commands:
  uitgeschakeld: [warp, warps, setwarp, delwarp]
```

Na een herstart geeft RolBeheer die commands door aan de plugin die ze ook levert, dus /warp blijft
gewoon werken via je warp-plugin.

### Homes overnemen uit EssentialsX
Staat er al data in `plugins/Essentials/userdata`, dan verschijnt in het paneel bij Server → Commands
de knop **Overnemen**. Die leest alle homes van al je spelers in, en het spawnpunt uit `Essentials/spawn.yml`
als je er nog geen hebt. Bestaande homes in RolBeheer blijven staan. Essentials mag daarna weg.

Gebruik je liever EssentialsX voor deze commands? Zet dan in `config.yml` `commands.ingeschakeld: false`
en herstart, anders claimen twee plugins dezelfde commands.

## Updaten
Vanaf versie 1.3.0 gaat updaten via het paneel: tabblad **Server → Updates**. Daar staat welke versie
je draait, of er een nieuwere op GitHub staat, en een knop om die klaar te zetten. Bij de eerstvolgende
herstart wisselt de plugin zichzelf om en verwijdert hij de oude jar.

Eenmalig instellen in `config.yml`:

```yaml
updates:
  repo: "jouwnaam/rolbeheer"
  token: ""
```

Staat je repository op privé, dan hoort daar een token bij. Die maak je zo:

1. Op GitHub: klik op je profielfoto, **Settings**.
2. Helemaal onderaan links: **Developer settings**.
3. **Personal access tokens → Fine-grained tokens → Generate new token**.
4. Bij *Repository access*: **Only select repositories**, en kies je rolbeheer-repo.
5. Bij *Permissions → Repository permissions*: zet **Contents** op **Read-only**.
6. Genereer de token, kopieer hem en plak hem tussen de aanhalingstekens bij `token`.

De token geeft alleen leesrechten op dat ene project. Deel `config.yml` verder met niemand.

De workflow maakt bij elke push automatisch een release met de jar erin, en daar kijkt de plugin naar.

## Rollen en OP
Een operator (OP) mag standaard alles. Staat een permissie in een rol op "Niet ingesteld", dan geldt
de standaard van de plugin, en die is voor een OP meestal "toegestaan". Wil je iets dichtzetten dat
een OP ook niet mag, zet het dan op **Verboden**: een expliciet verbod wint van OP-rechten.

Handiger is om jezelf de rol `admin` te geven (die heeft `*`) en je OP-status weg te halen met
`/deop <naam>`. Dan test je onder dezelfde regels als je spelers.

## Bestanden
- `config.yml`: standaardrol, chatformaat, tab/naamlabel aan/uit
- `roles.yml`: rollen (handmatig bewerken mag, daarna `/rol herlaad`)
- `players.yml`: welke speler welke rollen heeft

## Let op
- Staat er een andere chatplugin (bijv. EssentialsChat)? Zet dan `chat.ingeschakeld: false`.
- Naamlabels gebruiken het hoofdscoreboard. Plugins die elke speler een eigen scoreboard geven, kunnen ze verbergen.
