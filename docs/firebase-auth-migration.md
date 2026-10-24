# Firebase Authentication

Die aktive Implementierung verwendet Firebase Authentication statt Supabase. Die
älteren Supabase-/PKCE-Abnahmen in `phase-2-verification.md` sind historische
Nachweise und keine Abnahme dieser Umstellung. Angaben zu Supabase, PKCE und
Backend-Token-Exchange in älteren Planungsdokumenten sind damit überholt. Der
Finder-Flow muss vor seiner Implementierung auf Firebase-E-Mail-Links ausgelegt
und geräteübergreifend separat geprüft werden.

## Konfiguration

In jedem Firebase-Projekt unter Authentication den Anbieter E-Mail/Passwort und
zusätzlich die Anmeldung per E-Mail-Link aktivieren. Unter Authorized domains die
Hosting-Domain und im Dev-Projekt für lokale Tests `localhost` eintragen. In der
Web-App-Konfiguration des Projekts stehen die vier öffentlichen Werte:

- `FIREBASE_PROJECT_ID` (muss zum Backend-Projekt passen)
- `FIREBASE_API_KEY`
- `FIREBASE_AUTH_DOMAIN`, z. B. `lost-key-finder-dev.firebaseapp.com`
- `FIREBASE_APP_ID`

Lokal diese Werte in `.env.dev.local` setzen; vorhandene `SUPABASE_*`-Werte werden
nicht mehr verwendet und können entfernt werden. `npm start --workspace=@lost-key-finder/web`
liest die Datei, beim Build müssen die Werte als Umgebungsvariablen vorliegen.
Das Backend braucht Projekt-ID und API-Key. Der Key muss die Identity Toolkit API
für `accounts:lookup` zulassen; reine Browser-Referrer-Einschränkungen passen nicht
zum serverseitigen Aufruf. Keine Service-Account-Schlüssel an den Browser geben.

GitHub Environments `dev` und `prod`: `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`
und `FIREBASE_APP_ID` setzen. Die Projekt-ID kommt im Deployment aus dem bestehenden
`GCP_PROJECT_ID`. Die öffentlichen Werte werden in `auth-config.json` geschrieben.

## Konten und Index vor dem Deployment

Laut Projektverantwortlichem gibt es keine Supabase-Konten. Eine Kontenmigration
oder Zuordnung bestehender Identitäten ist daher nicht erforderlich. Neue Konten
werden beim ersten erfolgreichen Firebase-Login lokal angelegt.

Lokale Konto-IDs bleiben UUIDs. Firebase-UIDs sind eigenständige Strings bis 128
Zeichen und stehen in `users.firebaseUserId`.

Vor Freischaltung den eindeutigen Index auf `users.firebaseUserId` anlegen.
Falls der frühere eindeutige Index auf `supabaseUserId` bereits angelegt wurde,
muss dieser entfernt werden: neue Dokumente haben dieses Feld nicht, ein
nicht-sparse Altindex würde mehrere neue Konten blockieren. Dies ist eine
Indexanpassung und keine Kontenmigration. Die konkreten Firestore-Indexbefehle
stehen in `FIRESTORE-SETUP.md`.

## Ablauf und Abnahme

Das SDK sendet E-Mail-Links mit festem Callback `/auth/callback`. Die E-Mail bleibt
lokal gespeichert und wird nie in den Link geschrieben. Der Callback entfernt
Query und Fragment synchron, bevor Konfiguration oder SDK geladen werden. Nur die
für Firebase nötigen Linkfelder bleiben bis zum Einlösen im Speicher. Auf einem
anderen Browser/Gerät wird die E-Mail erneut abgefragt. Nach einem Reload des
bereits bereinigten Callbacks ist ein neuer Link nötig.

Firebase verwaltet persistente Sitzungen und Token-Refresh. Die API erhält ein
aktuelles ID-Token als Bearer-Token, prüft RS256, Google-JWKS, Projekt-Issuer,
Audience, Ablauf, Ausstellungs-/Anmeldezeit und UID. Beim ersten Zugriff werden
UID, bestätigte E-Mail und Übereinstimmung mit dem signierten Token zusätzlich
über Firebase `accounts:lookup` geprüft. Lokaler Kontostatus gilt bei jedem Zugriff.

Offline-Tests decken diese Verträge mit einem lokalen RSA-Identity-Server ab.
Vor dem Deployment sind echte Tests mit Firebase erforderlich: neuer und
bestehender Benutzer, Link auf zweitem Gerät, abgelaufener/wiederverwendeter Link,
Refresh, Logout über Tabs sowie `/api/v1/me` mit normalisiertem Konto und gesperrtem
Status. Diese Live-Abnahme ist durch die Codeumstellung nicht automatisch erfolgt.

Quellen: [E-Mail-Link-Anmeldung](https://firebase.google.com/docs/auth/web/email-link-auth),
[ID-Token-Prüfung](https://firebase.google.com/docs/auth/admin/verify-id-tokens),
[REST accounts:lookup](https://cloud.google.com/identity-platform/docs/reference/rest/v1/accounts/lookup).
