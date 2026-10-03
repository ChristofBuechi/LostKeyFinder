# Kotlin / Spring Boot Foundation

Auf `feature/phase-1-kotlin-foundation` ersetzt das Kotlin-Backend die bisherige
NestJS-Implementierung. Die Anforderungen in `docs/` bleiben die fachliche
Referenz; dort genannte Node-Treiber und NestJS-Bausteine werden auf diesem Branch
durch JVM-Treiber und Spring-Standards ersetzt. `docs/` ist unverändert.

## Stack

- Java 25, offizieller Gradle Wrapper 9.5.1
- Kotlin 2.3.21, Spring Boot 4.1.1
- Spring MVC, Bean Validation, Actuator und Spring Data MongoDB
- MongoDB JVM-Treiber 5.x aus Spring Boots Dependency Management
- springdoc-openapi 3.1.1
- JUnit 5 / MockMvc und ein handgeschriebener Mongo-Health-Fake

## Lokale Kommandos

```bash
npm ci
npm run verify
npm run start:dev --workspace=@lost-key-finder/api
```

`verify` generiert OpenAPI/Angular-Client, kompiliert Backend und Frontend und
führt die Offline-Tests aus. Gradle/npm benötigen beim ersten Lauf Downloadzugriff;
die Tests benötigen keine externe Datenbank, Docker, Credentials oder externe Provider.
Das Backend-`lint`-Kommando verwendet ktlint 1.8.0;
das Frontend verwendet weiterhin ESLint.

`verify` führt die HTTP-Vertragstests bereits über `test` aus, nicht nochmals
separat. `npm run test:http` führt nur diese Tests gezielt aus; Browser-E2E-Tests
sind damit nicht gemeint. `start:dev` delegiert an den bestehenden Firestore-Helfer.

JUnit deckt aktuell 21 Foundation-/Konfigurations-/Persistenzszenarien ab; der separate
OpenAPI-Export prüft zusätzlich den öffentlichen Vertrag. JaCoCo misst als
Ausgangsbasis 93 % Instruction- und 74 % Branch-Coverage. `npm run test` erzwingt
90 % Instruction- und 70 % Branch-Coverage für den gesamten produktiven
Backendcode. `npm run test:coverage --workspace=@lost-key-finder/api` erzeugt
HTML/XML-Reports unter `apps/api/build/reports/jacoco/test`.

### Spring-Profile

| Profil | Datenbank | Verwendung |
| --- | --- | --- |
| `ci` | Automatisch gestarteter In-Memory-Server | JUnit/MockMvc, OpenAPI-Export und normale CI |
| `dev` | Firestore-Dev-Datenbank, URI aus `FIRESTORE_MONGODB_URI` | Lokaler Start oder Dev-Deployment |
| `prod` | Firestore-Produktionsdatenbank, URI aus `FIRESTORE_MONGODB_URI` | Produktionsdeployment mit GCP-OIDC |

`application.yml` enthält gemeinsame Einstellungen; `application-dev.yml` und
`application-prod.yml` liegen im Hauptcode. `application-ci.yml` und der Server
liegen ausschließlich im Test-Classpath. Die API-Umgebungswerte bleiben
`test`, `development` und `production`.

Spring Boot liest Umgebungsvariablen, lädt aber `.env` nicht automatisch.
Ohne explizites Profil ist `dev` der Standard. `dev` und `prod` benötigen einen
expliziten URI; es gibt keinen stillen Fallback auf `localhost:27017`.
`firestore:start` holt lokale Credentials und setzt `SPRING_PROFILES_ACTIVE=dev`.
Ein direkter `bootRun` benötigt den URI bereits im Environment.
`prod` benötigt zusätzlich `ALLOWED_ORIGINS`. `NODE_ENV` steuert das Backend
nicht mehr; Cloud Run setzt das Profil passend zum Deployment auf `dev`/`prod`.

### Lokale Persistenz-Integrationstests

`mongo-java-server` 1.47.0 ist ausschließlich eine Testabhängigkeit. Das Profil
`ci` verwendet einen `MemoryBackend`-Server auf `127.0.0.1` mit dynamischem
Port. Ein `DynamicPropertyRegistrar` setzt dessen URI für Spring Boots normale
Mongo-Autokonfiguration. Damit laufen der echte JVM-Treiber, `MongoTemplate` und
der Actuator-Mongo-Health-Contributor. Der Testserver wird beim Schließen des
Spring-Kontexts heruntergefahren; Dokumente werden vor/nach jedem Test entfernt.

Die fünf Tests prüfen Kotlin/BSON-Mapping (Datum, Enum, Binärdaten), gefilterte
Queries, bedingte Updates, Löschen und Readiness. Sie laufen automatisch in
`npm run test` und `npm run verify`, ohne Docker, mongod-Binary oder Credentials.

Version 1.47.0 unterstützt nur das ältere Handshake-Kommando `isMaster`.
Eine kleine, ausschließlich testseitige Backend-Erweiterung leitet `hello`
darauf um, damit Spring Boot 4 seinen unveränderten Mongo-Healthcheck ausführen
kann. Diese Anpassung ist keine Simulation von Authentifizierung, Transaktionen
oder Firestore-Semantik.

Alle Spring-Kontexttests verwenden `ci` und importieren die In-Memory-Konfiguration.
HTTP-Fehlerszenarien ersetzen gezielt den Mongo-Health-Contributor durch einen Fake;
Mongo-Client und Mapping bleiben echt. Der OpenAPI-Export verwendet den echten
Mongo-Contributor. Testcontroller werden explizit importiert und nicht automatisch
gescannt; es gibt kein zusätzliches Testprofil.
Transaktionen werden nicht gegen den In-Memory-Server
getestet, da die Library sie nicht unterstützt. Echte Firestore-Verifikation
bleibt separat erforderlich.

## API-Vertrag

- `GET /api/v1/health/live`: `{ "status": "ok" }`
- `GET /api/v1/health/ready`: gleiche Response, bei fehlender Readiness `503`
- `GET /api/v1/version`: Version und Umgebung
- Spring `ProblemDetail`, queryfreie Instance und `X-Correlation-ID`
- CORS auf konfigurierte Origins beschränkt

Die Readiness verwendet die Actuator-Gruppe `readinessState,mongo`.
Nur der Health-Actuator-Endpunkt ist exponiert; Details bleiben verborgen.
Die bisherigen OpenAPI-Operation-IDs bleiben erhalten. Der Export erfolgt mit
MockMvc ohne HTTP-Port; keine statische, von Hand gepflegte OpenAPI-Datei.

## MongoDB

Spring Boot verwaltet Client, Pool, Mapping und Shutdown. Spring Data stellt
`MongoTemplate` und Repositories bereit. `MongoTransactionManager` aktiviert
Spring-Transaktionen. Automatische Indexerstellung ist ausgeschaltet.

Beim Profil `prod` wird die Verbindungszeichenfolge mit dem echten
MongoDB-Treiber geparst und auf TLS, `loadBalanced=true`, `retryWrites=false` und
GCP-OIDC mit `TOKEN_RESOURCE:FIRESTORE` geprüft. Ein fehlender produktiver URI
führt zu einem Startfehler.

Die reale Dev-Datenbank liegt im Projekt `lost-key-finder-dev`, heißt `dev1` und
verwendet Firestore Enterprise mit MongoDB-Kompatibilität in `europe-west6`.

```bash
FIRESTORE_MONGODB_URI='...' npm run test:integration
```

Dieser explizite Test prüft eine Spring-Data-Session-Transaktion mit Readback und
Cleanup gegen Firestore. Er ist nicht Bestandteil der Offline-CI. Der manuelle
Provider-Workflow führt ihn aus. OIDC, produktive Queries, Indizes und
Parallelitätsinvarianten benötigen weiterhin echte Provider-Verifikation.

## Weiterer Ausbau

- Fachbezogene Repository-/Provider-Interfaces erst mit den jeweiligen Use Cases.
- Spring Security Resource Server für JWT/JWKS mit Identity einführen.
- Keine zusätzlichen Reactive-, Queue- oder Native-Build-Schichten.
- Kotlin-Tests übernehmen die bisherigen Verträge aus `TESTING-PLAN.md`;
  Nest-/Jest-spezifische Beispiele dieses Plans sind historische Referenz.
- Docker-Image-Build und Cloud-Run-Durchstich separat verifizieren.

Der Cloud-Run-Service ist mit diesem Branch noch nicht deployt.

Der lokale Multi-Stage-Docker-Build und Containerstart wurden vor der Profilaufteilung verifiziert:
Liveness liefert `200`, Readiness ohne Datenbank `503`, der Version-Endpunkt
lieferte den erwarteten Vertrag. Der Container läuft als `10001:10001` ohne Root.
Mit der Profilaufteilung benötigt auch der Containerstart einen expliziten Mongo-URI.
