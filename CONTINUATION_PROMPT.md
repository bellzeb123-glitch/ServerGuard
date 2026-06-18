# ServerGuard — prompt do nowego czatu

Skopiuj poniższy blok do nowej rozmowy w Cursor, gdy chcesz rozwijać **ServerGuard**.

---

```
Kontynuuję rozwój pluginu ServerGuard w ekosystemie Bell (Minecraft Purpur/Paper 1.21.x, Java 21, Maven).

## Stan projektu
- Ścieżka: F:\Projekty\ServerGuard
- Wersja: 2.1.1 (plugin.yml), main: pl.serverguard.ServerGuard
- Live na serwerze produkcyjnym — monitoring bezpieczeństwa

## Co już działa (v2.1.1)
- Audyt komend admina (vanilla + Bell) — alert + blokada bez uprawnień
- Logowanie komend, skrzyń, bloków, sesji
- Faza 1: filtr TP, gamemode/kick log, konsola, cooldown alertów, retencja DB
- Panel GUI `/sg` / `/sg gui` — gracze, monitoring, reguły audytu, watch, PL/EN (LangManager)
- SQLite z buforem (500 wpisów, flush ~5s)
- Komendy (serverguard.admin): /sg, /sg reload, /sghistory, /sgsearch
- Uprawnienia: serverguard.admin, serverguard.notify, serverguard.bypass, serverguard.audit.bypass

## Struktura kodu
- listeners/: CommandListener, ContainerListener, BlockListener, PlayerListener, ModerationListener, ConsoleCommandListener, GuiListener, AdminChatListener
- managers/: DatabaseManager, AlertManager, AdminAuditManager, AlertCooldown, WatchManager, ConfigListManager, LogEntry
- gui/: AdminGuiService, GuiHolder, GuiState
- config/: LangManager
- commands/: SGCommand, SGHistoryCommand, SGSearchCommand

## Kontekst ekosystemu
- Dokumentacja użytkowa: F:\Projekty\Bell-Ecosystem\docs\serverguard\
- Dokumentacja techniczna: F:\Projekty\Bell-Ecosystem\serverguard\architecture.md
- Konwencje (w tym struktura docs/): F:\Projekty\Bell-Ecosystem\shared\conventions.md
- Powiązane: BellTrade, BellLands, BellMarket, BellChat, VIPDeathChest, BellGate

## Roadmapa (kolejność)
1. ~~Admin command audit~~ ✅
2. ~~Faza 1 (TP, gamemode, kick, cooldown, retencja)~~ ✅
3. ~~Panel GUI + PL/EN~~ ✅
4. Webhook Discord — krytyczne alerty
5. API BellChat → log mute/ban
6. Anti-fly / movement flags (lekki alert)

## Zasady kodu
- Java 21, Paper API 1.21
- Bez over-engineeringu — mały, czytelny plugin
- Uprawnienia zawsze w executorze + plugin.yml
- Instrukcje/promo → Bell-Ecosystem/docs/serverguard/ · architektura → serverguard/

## Zadanie na tę sesję
[OPISZ TUTAJ]
```

---

## Build (lokalnie — przed wgraniem na serwer)

```powershell
cd F:\Projekty\ServerGuard
.\build.ps1
```

JAR: `target/ServerGuardV2-2.1.1.jar` → `plugins/`

Wymaga JDK 21+ (`JAVA_HOME` lub auto-wykrycie w `build.ps1`). Maven wrapper: `mvnw.cmd`.

Po push na GitHub — dodatkowo artefakt z Actions (backup).
