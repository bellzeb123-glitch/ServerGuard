# ServerGuard — prompt do nowego czatu

Skopiuj poniższy blok do nowej rozmowy w Cursor, gdy chcesz rozwijać **ServerGuard**.

---

```
Kontynuuję rozwój pluginu ServerGuard w ekosystemie Bell (Minecraft Purpur/Paper 1.21.x, Java 21, Maven).

## Stan projektu
- Ścieżka: F:\Projekty\ServerGuard
- Wersja: 2.0.0 (plugin.yml), main: pl.serverguard.ServerGuard
- Live na serwerze produkcyjnym — monitoring bezpieczeństwa

## Co już działa (v2.0.0)
- Logowanie komend graczy (nie adminów z serverguard.bypass)
- Logowanie skrzyń, wybranych bloków, sesji
- SQLite z buforem (500 wpisów, flush ~5s)
- Komendy (wszystkie serverguard.admin):
  - /sg, /sg reload
  - /sghistory <nick> [komendy|skrzynie|bloki] [ilość]
  - /sgsearch <fraza>
- Uprawnienia: serverguard.admin (op), serverguard.bypass (false)

## Struktura kodu
- listeners/: CommandListener, ContainerListener, BlockListener, PlayerListener
- managers/: DatabaseManager, AlertManager, LogEntry
- commands/: SGCommand, SGHistoryCommand, SGSearchCommand

## Kontekst ekosystemu
- Dokumentacja: F:\Projekty\Bell-Ecosystem\serverguard\architecture.md
- Powiązane: BellTrade, BellLands, BellMarket, BellChat, VIPDeathChest, BellGate
- ServerGuard NIE wykrywa cheata fly — tylko komendy i wybrane akcje świata
- Docelowo integracja z BellCenter (panel web) i alerty Discord (jak BellTrade Pro)

## Roadmapa (priorytet użytkownika)
1. **Admin command audit** — auto-detekcja /gamemode, /op, /give, komend Bell* admin; powiadomienie online adminów (serverguard.notify)
2. **Anti-fly / movement flags** — alert gdy gracz bez uprawnień utrzymuje flight (Y velocity, allowFlight false)
3. **Gamemode change log** — kto zmienił komu gamemode (vanilla + pluginy)
4. **Webhook Discord** — krytyczne alerty (wzór: BellTrade-Pro DiscordWebhookService)
5. **API dla innych pluginów Bell** — BellChat mute/ban → ServerGuard log

## Config docelowy (szkic)
admin-audit:
  enabled: true
  vanilla-commands: [gamemode, op, deop, ban, kick, give]
  notify-permission: serverguard.notify
  bypass-permission: serverguard.audit.bypass

movement:
  flight-alert: true
  max-air-ticks-without-elytra: ...

## Zasady kodu
- Java 21, Paper API 1.21
- Bez over-engineeringu — mały, czytelny plugin
- Uprawnienia zawsze w executorze + plugin.yml
- Dokumentacja w Bell-Ecosystem/serverguard/

## Ostatni incydent na serwerze
Nowy gracz latał bez /fly w logach ServerGuard — to normalne (cheat klienta). Potrzebujemy movement audit + zewnętrzny anticheat.

## Zadanie na tę sesję
[OPISZ TUTAJ: np. "Zaimplementuj admin-audit dla /gamemode i powiadomienia ingame"]
```

---

## Build

```powershell
cd F:\Projekty\ServerGuard
.\mvnw.cmd clean package
```

JAR: `target/ServerGuard-2.0.0.jar` → `plugins/`
