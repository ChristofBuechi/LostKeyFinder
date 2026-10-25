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

## Neustart der Dev-Datenbank

**Ponytail:** Es gibt noch keine zu erhaltenden Daten; deshalb keine Schema-Migration, Importmarker oder Kompatibilität mit dem alten Schema.

Beim Rollout darf der komplette Inhalt der Dev-Datenbank `dev1` im Projekt `lost-key-finder-dev` gelöscht werden:

1. Alte Cloud-Run-Revisionen und lokale Backends stoppen bzw. während der Umstellung keine Zugriffe zulassen.
2. Die Collections der Dev-Datenbank löschen. Firebase Authentication, Projekt und Secret Manager bleiben bestehen.
3. Mit Java 25 und gcloud-Operatorrechten die Indizes neu anlegen:

   ```sh
   npm run firestore:tag-indexes --workspace=@lost-key-finder/api
   ```

   Der vorhandene Provisionierungsbefehl legt auch den eindeutigen Benutzerindex auf `users.firebaseUserId` an.
4. Neue Anwendung deployen und prüfen. Das erste Login legt ein neues lokales Konto mit `isAdmin: false` an; Adminrechte danach bei Bedarf manuell vergeben.

Kein Reset beim Anwendungsstart und kein eigener Resetbefehl im Repository. Die Datenbankverwaltung ist ein einmaliger Operator-Schritt. Der aktuell laufende Dev-Dienst verwendet noch das alte Schema; der Reset wurde mit diesem PR noch nicht ausgeführt.

## Konfiguration und Prüfung

In Dev sind Secret-Version 1, das minimale Runtime-Zugriffsrecht und der TTL-Index auf `rateLimits.expiresAt` bereits vorhanden. Eine neue Umgebung benötigt dieselben Voraussetzungen und mindestens 32 zufällige Bytes als Base64-Schlüssel in Secret Manager.

`npm run start:dev --workspace=@lost-key-finder/api` liest den Dev-Schlüssel aus Secret Manager. Bei direktem `bootRun` muss `PUBLIC_RATE_LIMIT_HMAC_KEY` gesetzt sein. CI verwendet einen festen Testschlüssel. Für echte Datenbanktests:

```sh
npm run firestore:verify --workspace=@lost-key-finder/api
```

Bestehende Tests prüfen Quoten unter Parallelzugriffen, Schlüsselvalidierung, Benutzer-/Adminrechte, Transaktionen und Registrierung. Migrationstests entfallen zusammen mit der Migration.
