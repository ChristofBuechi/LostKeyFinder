# Phase 2: Owner-Identität – Verifikation

## Stand am 9. Oktober 2026

PR #3 ist gemergt und nach Dev deployt. Der End-to-End-Ablauf wurde lokal und
über Firebase Hosting mit echtem Supabase und echtem Firestore geprüft.
Phase 2 ist auf dem Dev-System abgenommen.

## Implementiert

- Supabase-Owner-Magic-Link mit PKCE und Registrierung neuer Owner.
- Callback `/auth/callback`: Query und Fragment werden synchron vor dem ersten
  asynchronen Aufruf entfernt; der Code wird nur einmal ausgetauscht.
- Kein automatisches SDK-Auslesen der URL, keine Auth-Cookies, keine Anzeige
  oder Anwendungslogs mit Providerfehlerdetails oder Sessiondaten.
- Geschützte Route `/account` mit generiertem Client für `GET /api/v1/me`.
- Tokens nur für die eigene versionierte API; keine Weitergabe an fremde Origins.
- Session-Persistenz, SDK-Auto-Refresh, Logout und Reaktion auf Sessionende.
- Deutsche und englische Texte, zugeordnete Formlabels, sichtbare Validierung,
  Statusregionen und Fokus auf den Hauptinhalt nach Navigation.
- Runtime-Auth-Konfiguration pro Environment; nur Publishable Key und Projekt-URL
  gelangen in den Browser. Callback und Konfiguration werden nicht gecacht.

## Automatisierte Prüfungen

```bash
npm run verify
git diff --check
npm run firestore:verify --workspace=@lost-key-finder/api
```

`verify` prüft OpenAPI/Client-Generierung, Lint, Backend-/Frontend-Build,
Backend-Tests mit Coverage-Gate und Frontend-Tests. Die Owner-Frontend-Tests
decken URL-Bereinigung, Replay-Ablehnung, Sessionwechsel, Logoutfehler,
Token-Origin-Begrenzung, Formularvalidierung, Sprache, Route Guard und
serverseitige Zugriffsverweigerung ab.

Am 9. Oktober wurde das GitHub-Review-Feedback zum Anwendungsstart umgesetzt:
Der Abruf von `auth-config.json` hat ein Fünf-Sekunden-Limit, das bis zum Ende
des Response-Bodys aktiv bleibt. Zusätzliche Regressionstests prüfen
Erfolg, hängende Anfrage, hängenden Body und Fehler-Fallback. Die Zeitbegrenzung
verwendet die native API `AbortSignal.timeout(5000)` statt eigener Timerverwaltung.

Der getrennte Firestore-Lauf prüft echte Transaktionen, User-Mapping, Unique-Index,
parallele Anlage und lokalen Statusentzug. Sein Identity-Server ist ein Testdouble;
er ersetzt nicht den nachfolgenden Supabase-Browsertest.

## Erfolgreicher lokaler Provider-/Browsertest

Supabase-Projekt: `dsxefugwxpkfgmdmysev`; Firestore: `lost-key-finder-dev/dev1`.
Der vom Benutzer bereitgestellte frische Magic Link wurde im Browser geöffnet,
der den PKCE-Verifier beim Versand gespeichert hatte.

- Versandanfrage von Supabase mit `200` akzeptiert.
- Frischer Link erzeugt echte ES256-Session; Backend verifiziert Supabase-Identität
  und bestätigte E-Mail und legt den lokalen Benutzer in Firestore an.
- Kontoseite zeigt den erfolgreichen `/me`-Abruf.
- Erzwungener SDK-Refresh und anschließender `/me`-Abruf erfolgreich.
- Browser-Reload stellt die Sitzung und Kontoseite wieder her.
- Wiederverwendung desselben Links wird abgelehnt; Fehlerparameter werden entfernt.
- Ein zuvor ungültiger/abgelaufener Link wird ebenfalls sicher abgelehnt.
- Logout entfernt die gespeicherte Session und führt zur Login-Seite.
- `/me` ohne Token liefert `401` und `Cache-Control: no-cache, no-store, ...`.
- Keine Auth-Cookies; keine Tokens, E-Mail oder Callback-Codes in den inspizierten
  lokalen API-Logs. Mongo protokolliert lediglich den technischen Benutzernamen
  `access_token` mit verborgenem Passwort.
- Deutsch/Englisch im Browser geprüft; auf 390px Breite kein horizontaler Overflow.
- Mobile Lighthouse-Snapshot-Prüfung der Login-Seite: Accessibility 100,
  Best Practices 100, SEO 100. Das ersetzt keinen manuellen Screenreader-Test.

Es werden keine Magic Links, Codes oder Session-Tokens in diesem Dokument gespeichert.

## Lokal starten

Supabase-Werte in der ignorierten `.env.dev.local` hinterlegen, dann in zwei Terminals:

```bash
node --env-file=.env.dev.local apps/api/scripts/firestore-dev.mjs start
npm run start --workspace=@lost-key-finder/web
```

In Supabase unter Authentication → URL Configuration:

- Site URL: `https://lost-key-finder-dev.web.app`
- Redirect URLs: `http://localhost:4200/auth/callback` und
  `https://lost-key-finder-dev.web.app/auth/callback`

Bei eingebautem Supabase-Mailversand eine Team-E-Mail verwenden. Der Owner-PKCE-Flow
verlangt denselben Browser und dieselbe Origin; der spätere geräteübergreifende
Finder-Flow ist Teil von Phase 5.

## Erfolgreiche Dev-Abnahme am 9. Oktober 2026

- [x] PR mergen und automatisches Dev-Deployment erfolgreich abschließen.
- [x] Site URL und erlaubten Hosting-Redirect praktisch verifizieren.
- [x] Auf `https://lost-key-finder-dev.web.app/login` anmelden, Link im selben Browser
  öffnen und Kontoseite, Refresh/Reload, Replay-Ablehnung und Logout prüfen.
- [x] Callback-Header `Cache-Control: no-store` und `Referrer-Policy: no-referrer`
  auf dem echten Hosting-Host verifizieren.

- Deployter Commit: `f01a8aa6797fde1a310cce314e23e712102f473b`.
- [CI erfolgreich](https://github.com/ChristofBuechi/LostKeyFinder/actions/runs/37888966146).
- [Deployment erfolgreich](https://github.com/ChristofBuechi/LostKeyFinder/actions/runs/37889145446).
- Version meldet den erwarteten Commit und `development`; Readiness liefert `200`.
- Öffentliche Auth-Konfiguration enthält die erwartete Supabase-URL und den
  Publishable Key und wird mit `no-store` ausgeliefert.
- Echter Magic Link führt über den Hosting-Callback zur Kontoseite; `/me` liefert `200`.
- SDK-Refresh wurde durch abgelaufene lokale Session-Metadaten erzwungen:
  danach neues Access Token, zukünftige Ablaufzeit und derselbe lokale Benutzer
  bei erfolgreichem `/me`-Abruf nach Reload. Keine Tokenwerte wurden aufgezeichnet.
- Wiederverwendung des Links zeigt den vorgesehenen Fehlerzustand; die URL bleibt sauber.
- Logout entfernt die gespeicherte Session, führt zur Anmeldung und hinterlässt
  keine Auth-Cookies. `/me` ohne Token liefert `401`.
- Ein Aufruf des verbrauchten Links ohne expliziten `redirect_to` bestätigt den
  Supabase-Default-Redirect auf `https://lost-key-finder-dev.web.app/`; der
  explizite Callback-Redirect ist durch den erfolgreichen Login bestätigt.

Phase 2 ist abgeschlossen; es verbleiben keine offenen Punkte dieser Dev-Abnahme.
Phase 3 (Tags und öffentliche QR-Auflösung) ist noch nicht begonnen.
