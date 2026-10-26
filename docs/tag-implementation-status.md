# Tag- und Admin-Umsetzung

Stand: 25. Oktober 2026. Branch `feature/tag-registration-admin`, PR #6.

## Implementiert

- Lokales `users.isAdmin`, sicherer Standard `false` auch für bestehende Dokumente ohne Feld, nur lesbare Ausgabe in `/api/v1/me`.
- Backend prüft vor jedem Admin-Aufruf das aktuelle lokale Konto und die Berechtigung. Manuelle Vergaben bleiben bei erneutem Login erhalten; Entzug und lokale Kontosperre wirken beim nächsten Request.
- Authentifizierte Selbsterstellung mit UUID-v4, atomarer unmittelbarer Besitzerzuordnung, benutzerbezogenem Idempotenzschlüssel und privater Bezeichnung bis 80 Zeichen.
- Besitzerliste und Detail-API sowie Aktivieren/Deaktivieren. Selbst erstellte Tags lassen sich nach Bestätigung aus Übersicht und Details per Soft Delete entfernen: `deletedAt` und `RETIRED`, vollständiger Datensatz und Besitzerreferenz bleiben erhalten. Besitzerlisten/-details blenden sie aus; Admin-Bestand und Admin-Details zeigen sie weiterhin. Ein altes Create-Retry kann sie nicht wiederbeleben. Besitzerliste und Admin-Tagbestand verwenden nummerierte Seiten à 25 Tags, direkte Seitenwahl, Gesamtseitenzahl und sichtbaren Bereich (z. B. 26–50 von 100); Gesamtzahlen beziehen sich auf Besitzer bzw. aktive Filter. Fremde Ressourcen und Änderungen an gesperrten/stillgelegten Tags werden abgelehnt.
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

## Historie und CSV-Reihenfolge

Die Tag-Details zeigen eine seitenweise Historie (25 Ereignisse, neueste zuerst) aus `auditLog`, einschliesslich Registrierung, Aktivierung/Deaktivierung, Besitzerfreigabe, Soft Delete und zugehöriger Serienfreigaben/-exporte. Bei früheren Tags ohne Erstellungs-Audit wird das vorhandene `createdAt` als Eckpunkt gezeigt; es gibt keinen Backfill. Bereits vorhandene Audit-Ereignisse werden angezeigt; frühere nicht protokollierte Statuswechsel können nicht rekonstruiert werden. Änderungsdetails halten alte und neue Feldwerte fest, insbesondere Besitzerzuordnungen und beim Freigeben entfernte Daten. Eigene Neuanlagen erhalten jetzt ebenfalls einen atomaren Audit-Eintrag. Statusänderung und Audit sind atomar; eine Wiederholung desselben Status erzeugt kein zusätzliches Ereignis.

Nur der aktuelle Besitzer kann seine Tag-Historie abrufen; Admins auch die Historie stillgelegter/soft-gelöschter Tags. Benutzerreferenzen der Akteure werden ausschliesslich an Admins ausgegeben. Die öffentliche Scan-API liefert keine Historie. Verbindliche Chat-Verknüpfung und bestätigter Admin-Lesezugriff für den späteren Chat-Prozess: [Tag-Historie](tag-history.md).

`position` entfällt im Tag-Dokumentmodell und in der Tag-API. CSV behält die Spalte `position` als berechnete Zeilennummer ab 1; Sortierung nach unveränderlicher Tag-ID hält wiederholte Exporte stabil. UUID-Eindeutigkeit und Seriengrösse sichern die Vollständigkeit. Bestehende Positionsfelder werden ignoriert; kein Datenreset oder Backfill.

Vor Deployment in jeder Umgebung einmal `firestore:tag-indexes` ausführen: Der explizite Operator-Schritt entfernt den bisherigen eindeutigen `(batchId, position)`-Index und legt `(batchId, _id)` sowie den Audit-Historienindex an. Der alte Sparse-Index würde neue Tags ohne Position sonst fälschlich als Duplikate behandeln. Runtime und normale CI ändern keine Indizes.

## Besitzerzuordnung freiwillig lösen

Besitzer können aktive oder inaktive Hardware-Tags nach Bestätigung freigeben. Der Tag bleibt mit UUID, Serie und Produktionsfreigabe erhalten; Besitzerreferenz, Registrierungszeitpunkt und private Bezeichnung werden entfernt und der Status wird `ACTIVE`. Danach ist der Tag wieder registrierbar. Alte Registrierungsentwürfe werden im selben Commit ungültig (Ablaufzeitpunkt setzen, E-Mail entfernen); TTL übernimmt ihre Bereinigung. `TAG_RELEASED` hält den bisherigen Besitzer im Audit fest. Soft Delete schreibt `TAG_DELETED`; beide Vorgänge verwenden dieselbe Transaktionsgrenze wie Registrierung und Serienfreigabe. Bei normalen Besitzeraktionen werden fremde, gesperrte oder stillgelegte Tags sowie die falsche Herkunft abgelehnt. Admin-Ansichten bieten keine pauschale Besitzerfreigabe an.

Keine neue Collection, kein Backfill und keine Wiederherstellungsfunktion. Fehlendes `deletedAt` bedeutet weiterhin nicht gelöscht. Physisches Löschen von selbst erstellten Tags ist keine Benutzeraktion.

## Admin-Soft-Delete und Detailansicht

Admins können über den Tag-Bestand und die Admin-Tag-Details jeden noch nicht soft-gelöschten Tag stilllegen, unabhängig von Herkunft, Besitzerzuordnung oder bisherigem Status. Ein separater Admin-DELETE-Endpunkt prüft das aktuelle aktive lokale Konto und `isAdmin`. Die Aktion nutzt dieselbe transaktionale Soft-Delete-Logik und protokolliert den tatsächlich handelnden Admin samt Vorher-/Nachher-Werten. Tag-Dokument, Besitzerreferenz und Produktionsdaten bleiben bestehen; der öffentliche QR-Link ist danach nicht verfügbar. Bereits gelöschte Tags bleiben mit Kennzeichnung und Löschzeitpunkt im Admin-Bestand sichtbar und haben keinen weiteren Löschbutton. Normale Besitzerrechte bleiben unverändert.

Der CSV-Export überspringt soft-gelöschte Tags und nummeriert die verbleibenden Zeilen lückenlos. Sind alle Tags gelöscht, enthält die Datei nur die Kopfzeile. Die Vollständigkeitsprüfung berücksichtigt weiterhin sämtliche erhaltenen Tag-Dokumente der Serie. Auch reduzierte und leere Exporte werden als erfolgreiche Exportereignisse protokolliert.

Die Bezeichnung lässt sich in den Besitzer- und Admin-Tag-Details per Klick inline bearbeiten. Enter speichert das native Formular; Escape oder „Abbrechen“ verwirft den Entwurf. Leere Bezeichnungen sind erlaubt, maximal 80 Zeichen. Besitzer dürfen nur eigene, Admins alle nicht gelöschten Tags umbenennen. PATCH-Endpunkte für die Bezeichnung prüfen diese Rechte; Änderung und `TAG_LABEL_CHANGED` mit Vorher-/Nachher-Werten werden gemeinsam transaktional gespeichert. Unveränderte Werte erzeugen kein zusätzliches Ereignis. Nach erfolgreichem Speichern aktualisiert sich die Historie; bei Fehlern bleibt der Entwurf erhalten.

Die Detailansicht gliedert Tag-Informationen, Aktionsleiste und Historie. „Zur Übersicht“ und Löschen/Freigeben verwenden gemeinsame Abmessungen und Button-Stile, Navigation neutral und Löschen rot umrandet; mobil stehen sie untereinander. Nach einer Besitzeraktion erfolgt die Rückkehr zur eigenen Liste. Nach Admin-Löschen bleiben die Admin-Details geöffnet und laden Daten/Historie neu; die Admin-Liste aktualisiert Bestand und Bestandszahlen.

## Vereinbarter Umfang abgeschlossen; spätere Funktionen

Fundmeldung, Benachrichtigungen, Kontakt/Chat und formatierte Druckvorlagen folgen später. Der Support-Ablauf bei Fehlregistrierungen ist gesondert beschrieben; direkte Übertragung auf ein bestimmtes anderes Konto und Support-Korrektur bleiben ausgenommen. Der separate Foto-/Rückfrageprozess bleibt eine manuelle Support-Konzeption und verspricht keine automatische Korrekturmöglichkeit.

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

Die anschliessende [Schema-Vereinfachung](database-schema.md) bettet das persönliche Tageslimit in `users` ein und startet ohne Migrationslogik mit einer leeren Dev-Datenbank.

## Dev-Indizes und Prüfung

Mit Java 25, gcloud-Anmeldung und passenden Operatorrechten:

```sh
npm run firestore:tag-indexes --workspace=@lost-key-finder/api
npm run firestore:verify --workspace=@lost-key-finder/api
```

Der Helper ist ausdrücklich auf Projekt `lost-key-finder-dev`, Datenbank `dev1`, begrenzt. Er bezieht kurzlebige Credentials, ohne sie auszugeben. `provisionTagIndexes` legt eindeutige Indizes für `publicId`, den optionalen `creationKey` (sparse), Serien-Idempotenz, `(batchId, _id)` für stabil sortierte Exporte, Audit-Historien nach Ressource/Zeit sowie Sortier-/Listenindizes sowie TTL-Indizes für Entwürfe und Zähler an. Unabhängige Indexanlagen laufen parallel. Indexaufbau kann mehrere Minuten dauern. Der Runtime-Service-Account benötigt keine Indexadministration.

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
