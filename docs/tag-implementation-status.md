# Tag-Umsetzung: erster Durchstich

Stand: 25. Oktober 2026. Branch `feature/tag-registration-admin`, Draft-PR #6.

## Implementiert

- Lokales `users.isAdmin`, sicherer Standard `false` auch für bestehende Dokumente ohne Feld, nur lesbare Ausgabe in `/api/v1/me`.
- Backend prüft vor jedem Admin-Aufruf das aktuelle lokale Konto und die Berechtigung. Manuelle Vergaben bleiben bei erneutem Login erhalten; Entzug und lokale Kontosperre wirken beim nächsten Request.
- Authentifizierte Selbsterstellung mit UUID-v4, atomarer unmittelbarer Besitzerzuordnung, benutzerbezogenem Idempotenzschlüssel und privater Bezeichnung bis 80 Zeichen.
- Besitzerliste und Detail-API sowie Aktivieren/Deaktivieren. Fremde Ressourcen und Änderungen an gesperrten/stillgelegten Tags werden abgelehnt.
- `GET /api/v1/admin/tags` mit Herkunft, Status, Zuordnung und exakter öffentlicher ID als Filtern. Globale Bestandsansicht zeigt auch vorproduzierte Dokumente, erzeugt selbst noch keine Serien.
- Stabile Cursor-Pagination mit 25 Einträgen über `(createdAt, id)` für Besitzer- und Adminlisten. Bei zwischenzeitlich neu erzeugten Tags führt „Zur ersten Seite“ zum aktualisierten Anfang; keine Snapshot-Sitzung über mehrere Seiten.
- Angular-Ansichten `/tags` und `/admin/tags` auf Deutsch/Englisch, Zugriff über das Konto. Nach normalem E-Mail-Login führt ein Admin zur Gesamtübersicht; seine eigene Kontoseite bleibt erreichbar.
- Lokaler PNG-QR-Download über eine bei Bedarf geladene Bibliothek. Ziel ist die öffentliche `/f/{publicId}`-URL derselben Umgebung. Dev-Codes sind keine Produktionsdruckdaten. Fundmeldung/Kontakt bleiben ausdrücklich noch nicht verfügbar.
- Tageslimit von 100 Erstellungsversuchen pro lokalem Benutzer in festen UTC-Fenstern, atomarer Zähler in `tagCreationLimits`. Wiederholung eines bereits gespeicherten Tags benötigt keinen neuen Platz. Parallele Wiederholungen vor dem ersten Speicherungserfolg können mehrere Versuche verbrauchen; das ist ein Kostenlimit, keine Abrechnung.
- Generierter OpenAPI-Client, Offline-HTTP-/Berechtigungstests, UI-/Routingtests und explizite reale Firestore-Tests. Indexanlage erfolgt ausschliesslich über einen gesonderten Operator-Schritt.

## Noch im Draft umzusetzen

1. Admin-Produktionsserien mit idempotenter Erstellung, Freigabe nach Produktionskontrolle und autorisiertem Export/Audit.
2. Serien-/Freigabefilter, Bestandszahlen und weitere Details der Admin-Übersicht.
3. Öffentliche Tag-Auflösung und Registrierung freier Hardware-Tags einschliesslich E-Mail-Entwurf, browserübergreifender Fortsetzung und konkurrierender Zuordnung.
4. Rate Limits der noch fehlenden öffentlichen Endpunkte und Bereinigung der Registrierungsentwürfe.

Fundmeldung, Benachrichtigungen, Kontakt/Chat und formatierte Druckvorlagen folgen später. Der Support-Ablauf bei Fehlregistrierungen ist gesondert beschrieben; der erste Durchstich implementiert weder Besitzerübertragung noch Support-Korrektur. Der PR bleibt Draft und wird nicht als vollständiger Hardware-Registrierungsrelease gemergt.

## Admin manuell vergeben

1. Die Person meldet sich regulär per E-Mail an und öffnet das Konto, um das lokale `users`-Dokument anzulegen.
2. Firebase-UID prüfen und genau dieses Dokument in der richtigen Umgebung ändern, beispielsweise mit berechtigtem MongoDB-Zugriff:

```javascript
db.users.updateOne(
  { firebaseUserId: "DIE_GEPRUEFTE_FIREBASE_UID" },
  { $set: { isAdmin: true, updatedAt: new Date() } }
)
```

Kein Upsert verwenden. Genau ein existierendes Konto muss getroffen werden. Zum Entziehen `isAdmin: false` setzen. Vergabe/Entzug im Betriebsprotokoll dokumentieren. Danach Kontodaten neu laden; ein neuer Firebase-Token ist nicht erforderlich. `isAdmin` ist ein boolesches Datenbankfeld, kein JWT-Claim und kein editierbares Profilfeld.

## Dev-Indizes und Prüfung

Mit Java 25, gcloud-Anmeldung und passenden Operatorrechten:

```sh
npm run firestore:tag-indexes --workspace=@lost-key-finder/api
npm run firestore:verify --workspace=@lost-key-finder/api
```

Der Helper ist ausdrücklich auf Projekt `lost-key-finder-dev`, Datenbank `dev1`, begrenzt. Er bezieht kurzlebige Credentials, ohne sie auszugeben. `provisionTagIndexes` legt eindeutige Indizes für `publicId` und den optionalen `creationKey` (sparse), Sortier-/Listenindizes und eine TTL-Bereinigung für Erstellungszähler an. Indexaufbau kann mehrere Minuten dauern. Der Runtime-Service-Account benötigt keine Indexadministration.

Reale Integrationstests legen isolierte Probedaten mit zufälligen Referenzen an und entfernen sie im `finally`-Block. Sie prüfen parallele Wiederholungen, Filter, Cursor und Status-/Besitzregeln. Vor einem späteren Deployment in eine weitere Umgebung müssen dort die erforderlichen Indizes separat eingerichtet und geprüft werden.

## Lokale Prüfungen

Für diesen Durchstich erfolgreich ausgeführt: vollständiges `npm run verify` (Client-Generierung, Lint, Builds und Tests), 42 Backendtests einschliesslich Coverage-Grenzen, 70 Frontendtests sowie drei Konfigurationsgenerator-Tests. Zusätzlich bestanden alle drei realen Firestore-Integrationstests nach separater Dev-Indexanlage. Die anfängliche Regex-Bereinigung auf `_id` wurde wegen fehlender Firestore-Unterstützung durch eine exakte Test-ID ersetzt. Ein Testzähler des ersten fehlgeschlagenen Aufräumlaufs unterliegt der eingerichteten TTL-Bereinigung.

Echte Mobilkamera und physische Ausdrucke wurden in diesem Durchstich nicht erneut geprüft; die neue QR-Downloadaktion und ihr Ziel sind per Komponententest geprüft.

```sh
npm run generate:client
npm run lint
npm run build
npm run test
git diff --check
```

Falls der native Angular-Diskcache lokal abstürzt, kann `CI=true` für den Aufruf den lokalen Cache umgehen; das ist kein notwendiger Anwendungscode. QR-Erzeugung verwendet derzeit CommonJS-Abhängigkeiten in einem Lazy-Chunk; der Angular-Build weist auf diese Bibliotheksform hin.
