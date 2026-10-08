# Phase 2: Owner-Identität – Verifikation

## Stand am 8. Oktober 2026

Die Implementierung ist vollständig im Owner-Identity-Branch vorhanden. Der
lokale End-to-End-Ablauf wurde mit echtem Supabase und echtem Firestore geprüft.
Die Abnahme auf dem deployten Dev-Host bleibt bis Merge und Deployment offen.

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
Backend-Tests mit Coverage-Gate und 24 Frontend-Tests. Die Owner-Frontend-Tests
decken URL-Bereinigung, Replay-Ablehnung, Sessionwechsel, Logoutfehler,
Token-Origin-Begrenzung, Formularvalidierung, Sprache, Route Guard und
serverseitige Zugriffsverweigerung ab.

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

## Noch offene Dev-Abnahme

- [ ] PR mergen und automatisches Dev-Deployment erfolgreich abschließen.
- [ ] Site URL und Hosting-Redirect im Supabase-Dashboard bestätigen.
- [ ] Auf `https://lost-key-finder-dev.web.app/login` anmelden, Link im selben Browser
  öffnen und Kontoseite, Refresh/Reload, Replay-Ablehnung und Logout prüfen.
- [ ] Callback-Header `Cache-Control: no-store` und `Referrer-Policy: no-referrer`
  auf dem echten Hosting-Host verifizieren.

Erst nach diesen Punkten ist Phase 2 auf dem deployten Dev-System vollständig abgenommen.
