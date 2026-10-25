# Firebase Authentication einrichten

Dev und Prod verwenden getrennte Firebase-Projekte. Es gibt keine zu importierenden Konten. Firebase Authentication verarbeitet Auth-Daten in den USA; dies ist akzeptiert.

## Konfiguration

In jedem Firebase-Projekt unter Authentication den Anbieter E-Mail/Passwort und
zusätzlich die Anmeldung per E-Mail-Link aktivieren. Unter Authorized domains die
Hosting-Domain und im Dev-Projekt für lokale Tests `localhost` eintragen. In der
Web-App-Konfiguration des Projekts stehen die vier öffentlichen Werte:

- `FIREBASE_PROJECT_ID` (muss zum Backend-Projekt passen)
- `FIREBASE_API_KEY`
- `FIREBASE_AUTH_DOMAIN`, z. B. `lost-key-finder-dev.firebaseapp.com`
- `FIREBASE_APP_ID`

Lokal diese Werte in `.env.dev.local` setzen. `npm start --workspace=@lost-key-finder/web`
liest die Datei, beim Build müssen die Werte als Umgebungsvariablen vorliegen.
Das Backend braucht Projekt-ID und API-Key. Der Key muss die Identity Toolkit API
für `accounts:lookup` zulassen; reine Browser-Referrer-Einschränkungen passen nicht
zum serverseitigen Aufruf. Keine Service-Account-Schlüssel an den Browser geben.

GitHub Environments `dev` und `prod`: `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`
und `FIREBASE_APP_ID` setzen. Die Projekt-ID kommt im Deployment aus dem bestehenden
`GCP_PROJECT_ID`. Die öffentlichen Werte werden in `auth-config.json` geschrieben.

## Konten und Index

Lokale Konto-IDs sind UUID-v4-Werte. Firebase-UIDs sind eigenständige Strings bis 128 Zeichen und stehen in `users.firebaseUserId`. Neue lokale Konten werden beim ersten erfolgreichen API-Zugriff angelegt; es erfolgt keine Zuordnung anhand der E-Mail. Der eindeutige Index auf `users.firebaseUserId` muss vor dem Deployment bereitstehen. Siehe `FIRESTORE-SETUP.md`.

## Sitzung und Sperren

Firebase verwaltet SDK-Persistenz und Token-Refresh. Die API akzeptiert nur eine weniger als 30 Tage alte Anmeldung anhand des signierten `auth_time`, auch wenn das ID-Token gerade erneuert wurde. Lokale Kontosperren und Löschstatus wirken sofort. Firebase-Widerruf und Firebase-Sperrung allein werden bei bestehenden lokalen Konten nicht bei jedem Request abgefragt; bereits ausgestellte ID-Tokens können bis zu ihrem Ablauf gültig bleiben. Support muss den lokalen Status zuerst sperren.

## Callback und zweites Gerät

E-Mail-Links führen auf `/auth/callback`. Die E-Mail wird im versendenden Browser lokal gespeichert und nach erfolgreicher Einlösung entfernt; auf einem anderen Gerät wird sie erneut abgefragt. Query und Fragment werden synchron entfernt. Linkdaten bleiben bis zur Einlösung im Speicher; nach einem Reload des bereinigten Callbacks ist ein neuer Link erforderlich.

Eine fehlende, ungültige oder unvollständige Runtime-Konfiguration deaktiviert die Anmeldung, lässt öffentliche Seiten aber verfügbar. Der Konfigurationsabruf ist einschliesslich Response-Body auf fünf Sekunden begrenzt.

## Abnahme und Rollout

Die normale CI nutzt einen lokalen RSA-Identity-Server und SDK-Testdoubles. Sie ersetzt keine Live-Abnahme. Der aktuelle Stand und die auszuführenden echten Tests stehen in [Phase-2-Verifikation](phase-2-verification.md). Der geräteübergreifende Finder-Draft ist ein späterer eigener Use Case; der Owner-Callback allein implementiert ihn noch nicht.

## Quellen

- [E-Mail-Link-Anmeldung](https://firebase.google.com/docs/auth/web/email-link-auth)
- [ID-Token-Prüfung](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Sitzungen und Widerruf](https://firebase.google.com/docs/auth/admin/manage-sessions)
- [Auth-Limits](https://firebase.google.com/docs/auth/limits)
- [Datenschutz und Verarbeitungsstandorte](https://firebase.google.com/support/privacy)

## Vorbereiteter Dev-Stand

Die Web-App und E-Mail-Link-Anmeldung sind im Projekt `lost-key-finder-dev` eingerichtet; die erforderlichen Werte liegen lokal und im GitHub-Environment `dev`. Der Benutzerindex ist bereit. Reale REST-Anmeldung, Backend-Verifikation, Refresh und Firestore-Provisionierung wurden am 24. Oktober 2026 erfolgreich geprüft. Mailzustellung, Browser-/Hosting-Abnahme und Deployment sind noch offen. Details stehen im Abnahmenachweis.
