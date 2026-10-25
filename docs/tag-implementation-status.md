# Tag- und Admin-Umsetzung

Stand: 25. Oktober 2026. Branch `feature/tag-registration-admin`, PR #6.

## Implementiert

- Lokales `users.isAdmin`, sicherer Standard `false` auch für bestehende Dokumente ohne Feld, nur lesbare Ausgabe in `/api/v1/me`.
- Backend prüft vor jedem Admin-Aufruf das aktuelle lokale Konto und die Berechtigung. Manuelle Vergaben bleiben bei erneutem Login erhalten; Entzug und lokale Kontosperre wirken beim nächsten Request.
- Authentifizierte Selbsterstellung mit UUID-v4, atomarer unmittelbarer Besitzerzuordnung, benutzerbezogenem Idempotenzschlüssel und privater Bezeichnung bis 80 Zeichen.
- Besitzerliste und Detail-API sowie Aktivieren/Deaktivieren. Fremde Ressourcen und Änderungen an gesperrten/stillgelegten Tags werden abgelehnt.
- Globale Admin-Tagübersicht mit Herkunft, Status, Zuordnung, öffentlicher ID, Serie und Produktionsfreigabe als Filtern; konsistente Bestandszahlen aus einer serverseitigen Aggregation und geschützte Detailansichten.
- Stabile Cursor-Pagination mit 25 Einträgen über `(createdAt, id)` für Besitzer- und Adminlisten. Bei zwischenzeitlich neu erzeugten Tags führt „Zur ersten Seite“ zum aktualisierten Anfang; keine Snapshot-Sitzung über mehrere Seiten.
- Angular-Ansichten `/tags` und `/admin/tags` auf Deutsch/Englisch, Zugriff über das Konto. Nach normalem E-Mail-Login führt ein Admin zur Gesamtübersicht; seine eigene Kontoseite bleibt erreichbar.
- Lokaler PNG-QR-Download über eine bei Bedarf geladene Bibliothek. Ziel ist die öffentliche `/f/{publicId}`-URL derselben Umgebung. Dev-Codes sind keine Produktionsdruckdaten. Fundmeldung/Kontakt bleiben ausdrücklich noch nicht verfügbar.
- Tageslimit von 100 Erstellungsversuchen pro lokalem Benutzer in festen UTC-Fenstern, atomarer Zähler direkt in `users.tagCreationLimit`. Wiederholung eines bereits gespeicherten Tags benötigt keinen neuen Platz. Parallele Wiederholungen vor dem ersten Speicherungserfolg können mehrere Versuche verbrauchen; das ist ein Kostenlimit, keine Abrechnung.
- Generierter OpenAPI-Client, Offline-HTTP-/Berechtigungstests, UI-/Routingtests und explizite reale Firestore-Tests. Indexanlage erfolgt ausschliesslich über einen gesonderten Operator-Schritt.

- Produktionsserien mit 1–100 Tags, atomarer vollständiger Anlage, benutzerbezogener Idempotenz und genau einem Anlage-Audit. Freigabe nach Produktionskontrolle verlangt eine bewusste UI-Bestätigung und setzt die gesamte Serie transaktional auf `RELEASED`; Wiederholung überschreibt keine Besitzer und erzeugt kein zweites Freigabe-Audit.
- Autorisierter CSV-Download mit vollständigen, eindeutigen Positionen, UUIDs und kanonischen URLs. Die private Serienbezeichnung wird nicht exportiert. Jeder Export wird auditiert; es gibt keine Aktivierungscodes.
- Öffentliche minimale Auflösung mit `REGISTER`, `REPORT_FOUND` oder `UNAVAILABLE`. Unbekannte IDs erzeugen keine Tags. Bereits angemeldete Besitzer können ihre Verwaltung öffnen; bei fremden registrierten Tags wird der noch fehlende Kontaktprozess ehrlich angezeigt.
- Direkte bestätigte Registrierung für angemeldete Benutzer sowie E-Mail-gestützter Registrierungsentwurf mit vier Stunden eigener Gültigkeit. Firebase verifiziert vor Abschluss nochmals die Identität. Zuordnung, Verbrauch des Entwurfs und Audit sind atomar; das E-Mail-Feld wird beim Abschluss entfernt.
- Browserwechsel: Die nicht geheime `registrationId` wird über den festen Firebase-Continue-Link übernommen, getrennt validiert und vor asynchroner Verarbeitung aus der URL entfernt. E-Mail und Auth-Codes sind keine Fortsetzungsparameter. Nach Anmeldung folgt eine geschützte explizite Bestätigung; der ursprüngliche Tab und dessen Speicher sind nicht erforderlich.
- Begrenzte öffentliche Auflösung, Entwurfserstellung und Registrierung; feste serverseitige Zeitfenster und atomare Zähler. IP-/E-Mail-Kontexte stehen nur als HMAC-Prüfwerte in Zählern. Die Zähler liegen in `rateLimits`; ihr privater Schlüssel kommt aus Secret Manager und wird zwischen Backend-Replikaten geteilt.
- TTL-Indizes bereinigen Entwürfe und Zähler automatisch. Fristprüfungen erfolgen unabhängig davon bei jedem fachlichen Zugriff; die physische TTL-Löschung ist asynchron und kein präziser Vier-Stunden-Timer.

## Vereinbarter Umfang abgeschlossen; spätere Funktionen

Fundmeldung, Benachrichtigungen, Kontakt/Chat und formatierte Druckvorlagen folgen später. Der Support-Ablauf bei Fehlregistrierungen ist gesondert beschrieben; dieser Umfang implementiert weder allgemeine Besitzerübertragung noch Support-Korrektur. Der separate Foto-/Rückfrageprozess bleibt eine manuelle Support-Konzeption und verspricht keine automatische Korrekturmöglichkeit.

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

Die anschliessende [Schema-Vereinfachung](database-schema.md) bettet das persönliche Tageslimit in `users` ein und beschreibt die kontrollierte Entfernung der alten technischen Collections.

## Dev-Indizes und Prüfung

Mit Java 25, gcloud-Anmeldung und passenden Operatorrechten:

```sh
npm run firestore:tag-indexes --workspace=@lost-key-finder/api
npm run firestore:verify --workspace=@lost-key-finder/api
```

Der Helper ist ausdrücklich auf Projekt `lost-key-finder-dev`, Datenbank `dev1`, begrenzt. Er bezieht kurzlebige Credentials, ohne sie auszugeben. `provisionTagIndexes` legt eindeutige Indizes für `publicId`, den optionalen `creationKey` (sparse), Serien-Idempotenz und `(batchId, position)` (sparse), Sortier-/Listenindizes sowie TTL-Indizes für Entwürfe und Zähler an. Unabhängige Indexanlagen laufen parallel. Indexaufbau kann mehrere Minuten dauern. Der Runtime-Service-Account benötigt keine Indexadministration.

Reale Integrationstests legen isolierte Probedaten mit zufälligen Referenzen an und entfernen sie im `finally`-Block. Sie prüfen parallele Wiederholungen, vollständige 100er-Serien, konkurrierende Registrierungen, Entwurfsverbrauch, Filter, Cursor, Bestandsaggregation und transaktionalen Rollback einer fehlgeschlagenen Freigabe. Vor einem späteren Deployment in eine weitere Umgebung müssen dort die erforderlichen Indizes separat eingerichtet und geprüft werden.

## Lokale Prüfungen

Abschlussstand: vollständiges `npm run verify` erfolgreich, 49 Backendtests mit den bestehenden Coverage-Grenzen, 82 Frontendtests und drei Konfigurationsgenerator-Tests. Zusätzlich bestanden alle vier realen Firestore-Integrationstests nach separater Indexanlage. Deployment-YAML und lokale Markdown-Links wurden geprüft.

Die vollständige Repository-Prüfung umfasst OpenAPI-Client-Generierung, Lint, Produktionsbuilds und Tests einschliesslich Backend-Coverage-Grenzen. Hinzu kommen explizite Integrationstests gegen die echte Firestore-Dev-Datenbank. Offline-HTTP-Tests verwenden eine klar getrennte synchronisierte Testimplementierung der Transaktionsgrenze; sie behaupten keinen Datenbank-Rollback. Dessen Nachweis erfolgt mit dem echten Dienst.

Im Browser wurde die neutrale Auflösung einer unbekannten UUID gegen das neue lokale Backend geprüft. Die ursprüngliche lokale Vorschau auf Port 4201 verwendete noch einen Proxy zum alten Dev-Backend; eine separate Vorschau mit dem Repository-Proxy auf Port 4202 prüfte das neue Backend auf Port 3000. Öffentliche Auflösung verlangt dabei keine Anmeldung. Kamera und physische Ausdrucke wurden nicht erneut abgenommen. Eine neue echte Anmelde-E-Mail wurde nicht versandt; Versand ist weiterhin der bereits verifizierte Firebase-Flow, ergänzt um getestete Fortsetzungsdaten.

Ein Testzähler aus einem früheren fehlgeschlagenen Aufräumversuch unterliegt TTL; weitere isolierte Integrationsdaten werden exakt bereinigt.

```sh
npm run generate:client
npm run lint
npm run build
npm run test
git diff --check
```

Falls der native Angular-Diskcache lokal abstürzt, kann `CI=true` für den Aufruf den lokalen Cache umgehen; das ist kein notwendiger Anwendungscode. QR-Erzeugung verwendet derzeit CommonJS-Abhängigkeiten in einem Lazy-Chunk; der Angular-Build weist auf diese Bibliotheksform hin.

## Vor Herstellung und Verkauf

- GitHub-Environment-Variable `TAG_PUBLIC_BASE_URL` auf die endgültige HTTPS-Origin setzen (der Deployment-Workflow übergibt sie an Cloud Run) und vor Druck prüfen. Ohne Konfiguration ist der CSV-Export deaktiviert; das Dev-Profil verwendet standardmässig `https://lost-key-finder-dev.web.app`.
- Produktionsformat und sichere Übergabe mit dem Hersteller festlegen; Dev-URLs niemals auf Verkaufstags drucken.
- Admin-Konten je Umgebung manuell vergeben. Supportkanal und den gesonderten Foto-/Rückfrageprozess vor Verkauf organisatorisch festlegen.
- Runtime und Browser verwenden dasselbe Firebase-Projekt; die Callback-Origin muss in Firebase autorisiert sein. Die separate Firebase-API selbst besitzt eigene Versandquoten; App-Limits ersetzen diese nicht.
- Peer-Limits nutzen die tatsächliche Servlet-Gegenstelle, keine frei vom Client setzbaren Forwarded-Header. Bei Hosting-Proxys kann dies eine geteilte Quote sein; zusätzlich begrenzen unabhängige Tag-, E-Mail- und Benutzerquoten Missbrauch. Eine spätere feinere Proxy-Auswertung verlangt eine ausdrücklich vertrauenswürdige Proxy-Konfiguration.
