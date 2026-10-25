# Kotlin / Spring Boot Foundation

Das Backend verwendet Kotlin/Spring Boot. Fachliche Anforderungen und Firebase-Architektur stehen in `docs/`; Implementierung und Pläne verwenden dieselben JVM-Standards.

## Stack

- Java 25, offizieller Gradle Wrapper 9.5.1
- Kotlin 2.3.21, Spring Boot 4.1.1
- Spring MVC, Bean Validation, Actuator und Spring Data MongoDB
- Spring Security OAuth2 Resource Server und Spring RestClient
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

JUnit deckt Foundation-, Konfigurations-, Persistenz- und Identitätsszenarien ab; der separate
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
- `GET /api/v1/me`: lokaler Benutzer, nur mit gültigem Bearer-JWT
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

Diese expliziten Tests prüfen eine Spring-Data-Session-Transaktion sowie die
User-Query, parallele Erst-Anlage, den Unique-Index und lokalen Benutzerstatus
mit Cleanup gegen Firestore. Sie sind nicht Bestandteil der Offline-CI. Der
User-Test verwendet einen lokalen Identity-Server, keine echte Firebase-Auth-Instanz.

## Owner-Identität

`users/User.kt` enthält lokale UUID-v4-ID, Firebase-UID, normalisierte E-Mail, Zeitstempel und die Zustände `ACTIVE`, `DISABLED`, `DELETING`. `UserService` verwendet direkt `MongoTemplate`. E-Mail-Adressen verbinden keine Konten.

Spring Security prüft RS256, Google-JWKS, Firebase-Projekt-Issuer/-Audience, `exp`, optionales `nbf`, `iat`, `auth_time` und nicht leere Firebase-UID bis 128 Zeichen. Die API erzwingt maximal 30 Tage seit `auth_time`; SDK-Refresh verlängert diese Frist nicht. `/api/v1/**` ist standardmässig geschützt, Health und Version sind öffentlich. Auth-Fehler verwenden sichere Problem Details ohne Auth-Cookies.

Bei der ersten Anlage prüft `FirebaseIdentity` über `accounts:lookup` UID und bestätigte E-Mail gegen das signierte Token. Upsert und Unique-Index auf `firebaseUserId` sichern parallele Anlage. Bestehende Benutzer werden bei jedem Request aus Mongo gelesen; `DISABLED` und `DELETING` ergeben sofort `403`. Firebase-Widerruf wird für bestehende lokale Konten nicht pro Request abgefragt; sofortiger fachlicher Zugriffsentzug erfolgt über den lokalen Status. Die öffentliche API liefert weder Firebase-UID noch rohe Mongo-Dokumente.

`identity/EmailNormalization.kt` implementiert die gemeinsame Normalisierung mit ICU4J/UTS #46, numerischen Hosts und Format-/Längenprüfungen.

Backend-Konfiguration: `FIREBASE_PROJECT_ID` und `FIREBASE_API_KEY`; das Frontend benötigt zusätzlich `FIREBASE_AUTH_DOMAIN` und `FIREBASE_APP_ID`. `prod` verlangt die offiziellen Google-Verifikationsendpunkte und einen Key. Ein fehlender Dev-Key erlaubt keine neue Kontoanlage.

Der Angular-Client lädt das Firebase-SDK dynamisch und erzeugt öffentliche Runtime-Konfiguration im Prebuild. Der lokale Web-Start liest `.env.dev.local`; der Backend-Helfer kann mit `node --env-file=.env.dev.local apps/api/scripts/firestore-dev.mjs start` gestartet werden. Die CI verwendet einen lokalen JWKS-/User-Server mit frisch erzeugtem RSA-Key und Firebase-artigen Claims.

Details: [Firebase-Einrichtung](docs/firebase-auth-setup.md), [Abnahmestand](docs/phase-2-verification.md). Ältere Provider-Abnahmen sind kein Nachweis einer erfolgreichen Firebase-Live-Anmeldung.

## Weiterer Ausbau

- Fachbezogene Repository-/Provider-Interfaces erst mit den jeweiligen Use Cases.
- Phase 3 (noch nicht begonnen): Tags und öffentliche QR-Auflösung ergänzen.
- Keine zusätzlichen Reactive-, Queue- oder Native-Build-Schichten.
- Kotlin-Tests übernehmen die bisherigen Verträge aus `TESTING-PLAN.md`;
  JUnit/MockMvc und Angular/Vitest prüfen die jeweiligen Verträge.
- Weitere fachliche Queries/Indizes gegen echtes Firestore prüfen.

Die Foundation aus PR #2 ist nach Dev deployt: Firebase Hosting, Cloud Run und
Firestore-OIDC sind verifiziert. Die Owner-Implementierung aus PR #3 ist ebenfalls
nach Dev deployt und auf Commit `f01a8aa6797fde1a310cce314e23e712102f473b` abgenommen.

Der lokale Multi-Stage-Docker-Build und Containerstart wurden vor der Profilaufteilung verifiziert:
Liveness liefert `200`, Readiness ohne Datenbank `503`, der Version-Endpunkt
lieferte den erwarteten Vertrag. Der Container läuft als `10001:10001` ohne Root.
Mit der Profilaufteilung benötigt auch der Containerstart einen expliziten Mongo-URI.
