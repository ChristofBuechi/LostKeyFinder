# Testing- und Mocking-Plan

## Ziel

Die verpflichtende CI prüft Kotlin/Spring Boot und Angular ohne Cloud-Datenbank,
Firebase-Zugang, Mollie, SMTP, Docker oder Provider-Credentials. Die Installation
benötigt die Paketregistries oder passende Caches. Echte Provider-Abnahmen laufen
getrennt und ersetzen keine Offline-Tests; Offline-Tests ersetzen umgekehrt keine
Firestore-Index-, Transaktions-, Zustellungs- oder Firebase-Live-Abnahme.

## Grundsätze

- Featurebezogene Tests und Konstruktor-Injektion im Backend; Ports erst bei einem
  konkreten Use Case oder mehreren Implementierungen einführen.
- Handgeschriebene Fakes statt tiefer SDK-Mocks; Vendor-Typen bleiben am Adapter.
- Tests prüfen öffentliche Ergebnisse, Zustandsänderungen und relevante Aufrufzahlen.
- Kein Debug-Endpunkt, kein Testprofil und keine Testimplementierung im Produktionsartefakt.
- Kontrollierte Zeit/IDs nur injizieren, wenn deterministische Tests dies benötigen.
- Parallelität und Unique-Indizes zusätzlich gegen echtes Firestore prüfen.
- Sensible Werte gehören weder in Assertions mit Ausgabe noch in CI-Logs.

## Backend: JUnit 5, MockMvc und Spring-Testkontexte

Produktionscode liegt unter `apps/api/src/main/kotlin/ch/lostkeyfinder`, Tests unter
`apps/api/src/test/kotlin/ch/lostkeyfinder`. Tests verwenden `ci`; dessen In-Memory-
Mongo-Konfiguration bleibt im Test-Classpath. `MongoTemplate`, JVM-Treiber und Mapping
laufen echt gegen `mongo-java-server`. Der Server unterstützt keine Transaktionen;
dafür wird kein eigener Transaktions-Emulator gebaut.

HTTP-Fehlerszenarien ersetzen gezielt den Health-Contributor. Testcontroller werden
explizit in den Testkontext importiert. Produktions-Beans werden nur dort ersetzt,
wo das Szenario eine externe Grenze kontrollieren muss.

| Szenario | Erwartung |
| --- | --- |
| Liveness | `200`, unabhängig von Datenbankverfügbarkeit |
| Readiness | `200` bei verfügbarer Datenbank, sonst sicheres `503`-Problem |
| Version | Version und Environment, keine Infrastrukturwerte |
| Fehlende Route | `404`, Instance enthält keinen Query-String |
| Correlation ID | Jede Antwort enthält eine ID |
| Unerwarteter Fehler | `500`, keine internen Details, kein Stack Trace |
| CORS | Nur konfigurierte Origins erhalten Freigabe |
| DTO-Validierung | RFC-9457-Problem mit stabilen Fehlerverträgen |
| Konfiguration | Unbekannte Environments und unsichere Origins werden abgelehnt |
| Produktive Mongo-Konfiguration | TLS, Load Balancing, deaktivierte Retry Writes und GCP-OIDC erforderlich |
| Persistenz | Datum, Enum, Binärdaten, Query, bedingtes Update und Löschen korrekt |

Der OpenAPI-Export verwendet MockMvc, die echten Controller und springdoc. Er
prüft unter anderem Bearer-Security für `/me`. Der daraus erzeugte Angular-Client
wird in CI auf unveröffentlichte Änderungen geprüft.

## Firebase-Identität

Ein lokaler HTTP-Server erzeugt einen frischen RSA-Key, liefert JWKS und simuliert
`accounts:lookup`. Test-JWTs haben Firebase-Projekt-Issuer/-Audience sowie `iat`,
`auth_time`, Ablauf und UID. Keine echten Cloud-Credentials sind nötig.

- Ungültige Signatur, symmetrischer Algorithmus, fremdes Projekt, fehlende Claims,
  Ablauf, Zukunftszeit und ungültige UID ergeben `401` ohne lokale Kontoanlage.
- Firebase-UIDs müssen keine UUID sein; lokale Konto-IDs sind UUID-v4-Werte.
- Weniger als 30 Tage alte Anmeldung wird akzeptiert; ab 30 Tagen reicht auch ein
  frisch erneuertes ID-Token nicht für weiteren API-Zugriff.
- Provider-UID und bestätigte E-Mail müssen zum signierten Token passen.
- Editierbare Metadaten ersetzen keine E-Mail-Bestätigung.
- Providerfehler erzeugen kein Konto und geben keine Providerantwort oder Tokens aus.
- Wiederholte Zugriffe erhalten lokale ID und Zeitstempel; parallele erste Zugriffe
  erzeugen genau ein Konto.
- Gleiche E-Mail verbindet unterschiedliche Firebase-UIDs nicht.
- Lokaler Status `DISABLED` oder `DELETING` entzieht den nächsten Zugriff, unabhängig
  von Rollen-Claims. Provider-Widerruf allein wird derzeit nicht pro Request geprüft;
  diese Grenze wird nicht durch Offline-Tests als sofortiger Widerruf dargestellt.

## Angular: Vitest und TestBed

- Auth-SDK-Adapter durch kleine Testdoubles ersetzen; kein echtes Firebase in CI.
- `RouterTestingHarness` für Guards, Callback und Kontoseite einsetzen.
- Asynchron auf `fixture.whenStable()` warten; keine unnötigen manuellen Change-
  Detection-Aufrufe und keine Implementierungsdetails als alleinige Assertion.
- Öffentliche Seiten bleiben bei fehlender oder ungültiger Auth-Konfiguration verfügbar.
- Abruf von `auth-config.json` ist inklusive Body auf fünf Sekunden begrenzt;
  Timeout, Fehler, `null`, falsche Typen und unvollständige Werte werden geprüft.
- Build-Konfigurationsgenerator in temporären Verzeichnissen ausführen: vier öffentliche
  Werte, keine administrativen Felder; Teilkonfiguration und leere Pflichtfelder ablehnen.
- Callback entfernt Query/Fragment vor weiterer Arbeit; Replay und Providerfehler
  legen weder Linkdaten offen noch eine Sitzung an.
- Gleicher Browser verwendet lokal gespeicherte E-Mail, zweites Gerät fragt erneut.
- SDK-Refresh liefert aktuelle ID-Tokens; Logoutfehler täuschen keinen Erfolg vor.
- Bearer-Token geht nur an die eigene versionierte API, nie an fremde Origins.
- Nicht angemeldete Benutzer gelangen zur Anmeldung; `403` zeigt keine Kontodaten;
  Logout entfernt die Kontoansicht vor Navigation.
- Loginformular prüft Pflichtfeld/E-Mail, Doppelversand, Erfolg und sichere Fehlertexte.
- Home, Statusanzeige, Sprache, App-Shell, Fallbackroute und Navigation bleiben getestet.
- Deutsche/englische Kernabläufe zusätzlich manuell mit Tastatur, kleinem Viewport
  und Screenreader prüfen; automatisierte Tests allein belegen keine WCAG-Konformität.

## Prüfkommandos und Coverage

```bash
npm ci
npm run verify
npm run test:http
```

`verify` erzeugt OpenAPI/Client, führt Lint, Builds und Tests aus. `test:http` ist ein
gezielter Wiederholungslauf der HTTP-Verträge; diese laufen bereits in der Testsuite.
Frontend-`pretest` prüft den Konfigurationsgenerator mit Nodes Test-Runner.

JaCoCo erzwingt mindestens 90 % Instruction- und 70 % Branch-Coverage des produktiven
Backendcodes. `npm run test:coverage --workspace=@lost-key-finder/api` erzeugt den
Report unter `apps/api/build/reports/jacoco/test`. Frontend-Coverage ersetzt keine
fachlichen Assertions; keine sinnlosen Tests zum Erhöhen einer Quote hinzufügen.

Falls der native lokale Angular-Cache abstürzt, kann der Produktionsbuild mit
`CI=true NG_BUILD_MAX_WORKERS=2 npm run build:web` ohne lokalen Cache geprüft werden.
Das ist kein Ersatz für einen erfolgreichen normalen CI-Build.

## Explizite Provider-Verifikation

```bash
npm run firestore:verify --workspace=@lost-key-finder/api
```

Der vorhandene manuelle Provider-Workflow nutzt Workload Identity Federation und
den bestehenden Dev-Helfer. Firestore-Tests prüfen echte Multi-Dokument-Transaktion,
User-Mapping, Unique-Index, parallele Anlage und lokalen Status. Sie verwenden
zufällige Test-IDs und entfernen ausschliesslich ihre eigenen Datensätze.

Firebase-E-Mail-Versand, Linkeinlösung auf einem zweiten Gerät, echter SDK-Refresh,
Browserneustart und Logout werden gegen Dev separat abgenommen; Nachweis unter
`docs/phase-2-verification.md`. Keine Cloud-Credentials in der normalen CI, keine
echten Auth-Links in Repository oder Log. Budget-/Quota-Grenzen vor solchen Tests prüfen.

## Weitere Fachtests mit den jeweiligen Features

- Tags: Idempotenz, neutrale öffentliche Antworten und konkurrierende Änderungen.
- Einladungen: Drei-Plätze-Limit, passende bestätigte E-Mail, neue Membership-ID bei
  erneutem Beitritt und sofortiger Rechteentzug.
- Finder: getrennte Draft-/Auth-Fristen, zweite Geräte, falscher Draft und Replay;
  Statuszugang legt keine E-Mail oder Meldung offen.
- Fälle/Chats: parallele Finder erzeugen getrennte Chats und höchstens einen aktiven
  Fall; Owner sehen Inhalte erst nach einem wirksamen Grant.
- Payment: kleiner Mollie-Fake mit kontrollierten Statuswerten; echter Provider-Spike
  für verlorene Create-Antwort, doppelten Webhook, Metadaten-Zuordnung, Refund und
  atomaren Grant. Kein vollständiger Mollie-Emulator.
- Bilder: Grenzen, Bereinigung, Autorisierung und kein Base64 in Listen.
- E-Mail: kleiner Fake für Providerannahme, Fehler und unklare Antwort; kein SMTP-Emulator.
- Löschung: Sperre vor Kaskade, Wiederaufnahme nach jedem Fehler, Firebase-Admin-
  Löschung zuletzt und Anwendung des Löschledgers nach Restore.
- Support/Wartung: genaue Service-Account-Prüfung, Pflichtgrund/Ticket, Audit und
  begrenzte idempotente Batches; keine freie Datenbanksuche oder Impersonation.
