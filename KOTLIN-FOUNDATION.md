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
npm run start --workspace=@lost-key-finder/api
```

`verify` generiert OpenAPI/Angular-Client, kompiliert Backend und Frontend und
führt die Offline-Tests aus. Gradle/npm benötigen beim ersten Lauf Downloadzugriff;
die Tests benötigen keine Datenbank, Docker, Credentials oder externe Provider.
Das Backend-`lint`-Kommando verwendet ktlint 1.8.0;
das Frontend verwendet weiterhin ESLint.

JUnit deckt aktuell 14 Foundation-/Konfigurationsszenarien ab; der separate
OpenAPI-Export prüft zusätzlich den öffentlichen Vertrag. JaCoCo misst als
Ausgangsbasis 93 % Instruction- und 74 % Branch-Coverage. `npm run test` erzwingt
90 % Instruction- und 70 % Branch-Coverage für den gesamten produktiven
Backendcode. `npm run test:coverage --workspace=@lost-key-finder/api` erzeugt
HTML/XML-Reports unter `apps/api/build/reports/jacoco/test`.

Spring Boot liest Umgebungsvariablen, lädt aber `.env` nicht automatisch.
Ohne Mongo-Konfiguration verwendet der lokale Start `localhost:27017/dev1`;
Liveness funktioniert, Readiness bleibt ohne Datenbank negativ. Für reine Tests
werden die Mongo-Autokonfigurationen durch das Testprofil `offline` ausgeschaltet.
Ein Fake ersetzt den Mongo-Health-Contributor, nicht den Controller.

## API-Vertrag

- `GET /api/v1/health/live`: `{ "status": "ok" }`
- `GET /api/v1/health/ready`: gleiche Response, bei fehlender Readiness `503`
- `GET /api/v1/version`: Version und Umgebung
- Spring `ProblemDetail`, queryfreie Instance und `X-Correlation-ID`
- CORS auf konfigurierte Origins beschränkt

Die Readiness verwendet die Actuator-Gruppe `readinessState,mongo`.
Nur der Health-Actuator-Endpunkt ist exponiert; Details bleiben verborgen.
Die bisherigen OpenAPI-Operation-IDs bleiben erhalten. Der Export erfolgt mit
MockMvc ohne Netzwerkport; keine statische, von Hand gepflegte OpenAPI-Datei.

## MongoDB

Spring Boot verwaltet Client, Pool, Mapping und Shutdown. Spring Data stellt
`MongoTemplate` und Repositories bereit. `MongoTransactionManager` aktiviert
Spring-Transaktionen. Automatische Indexerstellung ist ausgeschaltet.

Bei `NODE_ENV=production` wird die Verbindungszeichenfolge mit dem echten
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

Der lokale Multi-Stage-Docker-Build und Containerstart sind verifiziert:
Liveness liefert `200`, Readiness ohne Datenbank `503`, der Version-Endpunkt
liefert den erwarteten Vertrag. Der Container läuft als `10001:10001` ohne Root.
