# Vereinfachtes Datenbankschema

Stand: 25. Oktober 2026. Die Anwendung verwendet sechs Collections:

| Collection | Inhalt und Grund für die eigenständige Ablage |
| --- | --- |
| `users` | Konto, manuell gesetztes `isAdmin`, persönliches Tageslimit in `tagCreationLimit: {day, count}`. |
| `tags` | Eigene und vorproduzierte Tags; vor Registrierung ohne Besitzer. `primaryOwnerUserId` verweist nach Zuordnung auf `users._id`. |
| `tagBatches` | Produktionsserien, Freigabe und Erstellerreferenz. Eine Serie umfasst viele Tags und gehört zur Produktion. |
| `tagRegistrationDrafts` | Kurzlebige Registrierungsentwürfe, die bereits vor dem Benutzerkonto entstehen können; maximal vier Stunden gültig, TTL-Bereinigung. |
| `auditLog` | Ereignisse mit Referenzen auf handelnde Benutzer und betroffene Tags/Serien. Der Verlauf bleibt von späteren Besitzerwechseln unabhängig. |
| `rateLimits` | Kurzlebige technische Quoten für anonyme und angemeldete Zugriffe. HMAC-IDs enthalten keine rohen IPs oder E-Mail-Adressen; TTL-Bereinigung. |

Benutzerbezogene Daten werden direkt im Benutzer gespeichert, wenn sie einen festen kleinen Umfang haben. Wachsende Taglisten, Produktionsserien und Ereignisse bleiben über IDs verknüpft. So lassen sich auch unzugewiesene Tags, Adminübersichten und Vorgänge vor der Anmeldung abbilden. Firebase Authentication verwaltet weiterhin die Anmeldeidentität; `users.firebaseUserId` verbindet sie mit dem lokalen Konto.

Das persönliche Limit bleibt bei 100 Erstellungsversuchen pro UTC-Tag. Die bedingte Aktualisierung im Benutzerdokument verhindert Überschreitung durch parallele Requests; sie legt keine Benutzer an. Fehlendes Fenster wird beim ersten Versuch initialisiert. Das interne Feld erscheint nicht im Konto-API.

Der HMAC-Schlüssel liegt ausschliesslich in Secret Manager (`public-rate-limit-hmac`, fest referenzierte Version `1`). Cloud Run erhält ihn als `PUBLIC_RATE_LIMIT_HMAC_KEY`. Der Runtime-Service-Account benötigt `roles/secretmanager.secretAccessor` nur auf diesem Secret. Ohne Schlüssel lehnen öffentliche Quotenprüfungen mit HTTP 503 ab; ungültige oder zu kurze Schlüssel verhindern den Start. Ein Schlüsselwechsel benötigt eine eigene Planung, da sich sonst laufende Quoten zurücksetzen.

## Übergang vom bisherigen Schema

Die bisherigen technischen Collections `tagCreationLimits`, `publicRequestLimits` und `rateLimitKeys` entfallen nach dem Rollout. Fachliche Daten und Referenzen bleiben bestehen. Keine Bereinigung beim Anwendungsstart und keine automatische Löschung beim Deployment.

Voraussetzungen: Java 25, gcloud-Anmeldung und Operatorrechte auf Dev (`lost-key-finder-dev`, Datenbank `dev1`). Der Helper gibt weder Schlüssel noch Datenbank-Credentials aus. Alle folgenden npm-Befehle laufen im Repository-Stamm.

1. Den bisherigen HMAC-Schlüssel unverändert nach Secret Manager übernehmen. Der Befehl verifiziert Version 1 und verweigert einen abweichenden vorhandenen Schlüssel. Secret Manager API muss aktiviert sein. In Dev ist dieser Schritt bereits erledigt, einschliesslich Secret-Zugriffsrecht für den Runtime-Service-Account.

   ```sh
   npm run firestore:schema --workspace=@lost-key-finder/api -- prepare-secret
   ```

2. Die erforderlichen Indizes inklusive TTL auf `rateLimits.expiresAt` bereitstellen und echte Integrationstests ausführen:

   ```sh
   npm run firestore:tag-indexes --workspace=@lost-key-finder/api
   npm run firestore:verify --workspace=@lost-key-finder/api
   ```

3. Bestand prüfen und die noch gültigen Zähler übernehmen. Der Import erhält neue Zähler und addiert alte Zählerstände genau einmal. Wiederholung übernimmt nur zusätzliche alte Inkremente; Betriebsmarker merken den bereits importierten Anteil. Alte Collections bleiben bestehen.

   ```sh
   npm run firestore:schema --workspace=@lost-key-finder/api -- inspect
   npm run firestore:schema --workspace=@lost-key-finder/api -- import --apply
   ```

4. Die geprüfte neue Anwendung deployen und öffentliche Tagauflösung sowie authentifizierte Selbsterstellung prüfen. Der Workflow bindet die vorhandene Secret-Version ein. Bei gemischten alten/neuen Replikaten können die technischen Limits bis zum letzten Import vorübergehend zu niedrig gezählt werden; es handelt sich um Missbrauchs-/Kostenlimits, keine Abrechnung. Für einen vollständig geschlossenen Übergang Zugriffe während des Umschaltens pausieren.

5. Sicherstellen, dass keine alte Cloud-Run-Revision mehr Traffic erhält oder laufende Requests verarbeitet und keine alte lokale Vorschau bzw. Hintergrundinstanz mehr schreibt. Insbesondere ältere lokale Backends auf Port 3000 stoppen oder aktualisieren. Erst dann finalen Import und Löschung der drei alten technischen Collections explizit auslösen:

   ```sh
   npm run firestore:schema --workspace=@lost-key-finder/api -- cleanup --apply --legacy-writers-stopped
   npm run firestore:schema --workspace=@lost-key-finder/api -- inspect
   ```

   Cleanup prüft den identischen Schlüssel, übernimmt letzte Zählerdifferenzen, entfernt nur die drei alten technischen Collections und bereinigt Importmarker. Es ist wiederholbar, auch nach einer Unterbrechung. Das Flag bestätigt die Prüfung durch den Operator; das Werkzeug kann fremde laufende Prozesse nicht selbst erkennen. Nach Cleanup keine alte Anwendungsversion wieder starten. Ein Rollback auf die alte Version benötigt eine gesonderte Daten-/Schlüsselvorbereitung.

Die Übernahme berücksichtigt nur den aktuellen UTC-Tag und noch nicht abgelaufene öffentliche Fenster. TTL-Löschung ist asynchron; Quoten verwenden feste Fenster unabhängig vom physischen Löschzeitpunkt. `inspect` kann daher auch bereits abgelaufene Altdokumente zählen.

## Weitere Umgebungen und lokale Entwicklung

Vor dem ersten Deployment in eine weitere Umgebung Secret Manager API, Secret-Version 1, das minimale Runtime-Zugriffsrecht und Indizes dort separat bereitstellen. Bei einer neuen leeren Datenbank mindestens 32 zufällige Bytes als Base64-Schlüssel sicher erzeugen; bei bestehender Installation den bisherigen Schlüssel erhalten. Der Dev-Helper greift ausdrücklich nur auf Dev zu und erledigt keine Produktionsumstellung.

`npm run start:dev --workspace=@lost-key-finder/api` liest den Dev-Schlüssel aus Secret Manager. Bei direktem `bootRun` muss `PUBLIC_RATE_LIMIT_HMAC_KEY` selbst gesetzt sein. CI nutzt einen ausschliesslich für Tests vorgesehenen festen Schlüssel. Die Migration liegt im Operator-/Test-Classpath und wird nicht in das Runtime-JAR eingebaut.

## Prüfstand dieser Änderung

`npm run verify` ist erfolgreich: 53 Backendtests einschliesslich Coverage-Grenzen, 82 Frontendtests und drei Konfigurationsgenerator-Tests. Neue Tests prüfen atomare Tagesfenster unter Parallelzugriffen, fehlende Benutzer, Schlüsselvalidierung, Quoten ohne gespeicherten Schlüssel, wiederholte Übernahme samt späteren Inkrementen und abgesicherte, wiederholbare Bereinigung. Vier Integrationstests gegen die echte Dev-Firestore-Datenbank sind erfolgreich; der neue TTL-Index ist eingerichtet. Ein erster nicht löschender Zählerimport in Dev wurde ausgeführt. Die alte Anwendung und ihre Collections bleiben bis zum Rollout bestehen.
