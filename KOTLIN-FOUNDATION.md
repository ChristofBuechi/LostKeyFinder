# Kotlin / Spring Boot Foundation

Seit PR #2 ersetzt das Kotlin-Backend die bisherige
NestJS-Implementierung. Die Anforderungen in `docs/` bleiben die fachliche
Referenz; dort genannte Node-Treiber und NestJS-Bausteine werden auf diesem Branch
durch JVM-Treiber und Spring-Standards ersetzt. `docs/` ist unverändert.

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

JUnit deckt aktuell 33 Foundation-/Konfigurations-/Persistenz-/Identitätsszenarien ab; der separate
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
User-Test verwendet einen lokalen Identity-Server, keine echte Supabase-Instanz.

## Owner-Identität: erster Backend-Use-Case

`users/User.kt` enthält UUID-v4-Identität, Supabase-Referenz, normalisierte E-Mail,
Zeitstempel und die Zustände `ACTIVE`, `DISABLED`, `DELETING`. `UserService`
verwendet direkt `MongoTemplate`; es gibt keine zusätzliche Repository-Abstraktion.

Spring Security prüft die konfigurierte JWT-Signatur, Issuer, Audience, `exp`,
optionales `nbf` und UUID-v4-`sub`. Neue fachliche `/api/v1/**`-Endpunkte sind
standardmäßig geschützt; Health und Version bleiben öffentlich. Auth-Fehler
verwenden den bestehenden Problem-Details-Vertrag samt Correlation-ID und
erzeugen keine Session-Cookies.

Beim ersten Zugriff auf `/me` prüft der Identity-Adapter Supabase `/auth/v1/user`:
ID und E-Mail müssen zum signierten JWT passen, `email_confirmed_at` muss gesetzt
sein. Editierbares `user_metadata.email_verified` reicht nicht. Ein Upsert mit
Unique-Index auf `supabaseUserId` hält parallele Erstzugriffe idempotent.
Bestehende Benutzer werden bei jedem Request aus Mongo gelesen; deaktivierte
oder löschende Accounts erhalten sofort `403`, auch mit einer JWT-Rollenangabe.
Die API liefert keine Supabase-ID und kein rohes Mongo-Dokument.

`identity/EmailNormalization.kt` setzt `email-normalization-v1` um. ICU4J liefert
UTS #46 statt des älteren Java-IDNA-2003; auch numerische Hosts werden passend zu
Nodes `domainToASCII` kanonisiert. Format- und Längenregeln werden anschließend
auf der normalisierten Adresse geprüft.

Für eine echte Supabase-Anbindung werden `SUPABASE_URL`,
`SUPABASE_PUBLISHABLE_KEY`, optional `SUPABASE_JWT_AUDIENCE` (`authenticated`)
und `SUPABASE_JWT_ALGORITHM` (`ES256`) gesetzt. `prod` verlangt HTTPS und einen
Publishable Key. Ohne Dev-Projekt bleibt die lokale Supabase-URL `localhost:54321`;
öffentliche Endpunkte starten ohne Identity-Aufruf, ein erfolgreicher Login ist
damit noch nicht möglich. Ein leerer Key erlaubt keine neue Benutzeranlage.

Lokale Supabase-Werte können in der ignorierten `.env.dev.local` liegen. Der
Firestore-Dev-Helfer lässt sich vom Repository-Root mit diesen Werten starten:

```bash
node --env-file=.env.dev.local apps/api/scripts/firestore-dev.mjs start
```

Das Deployment liest `SUPABASE_URL` und `SUPABASE_PUBLISHABLE_KEY` aus den
GitHub-Environment-Variablen für `dev` beziehungsweise `prod` und übergibt sie
an Cloud Run. Jedes Environment benötigt die Werte seines eigenen Projekts.

Die CI startet einen lokalen JWKS-/User-Server mit frisch generiertem ES256-Key
und signierten Test-JWTs. Sie prüft Signatur/Claims, E-Mail-Bestätigung,
Statusentzug, Providerfehler und parallele Anlage über die echte Security-Kette.
Das Angular-Frontend ergänzt PKCE-Magic-Link, Callback, geschützte Kontoseite,
automatischen Session-Refresh und Logout. Der Callback entfernt Query und Fragment
vor Konfigurationsabruf, SDK-Initialisierung und Routing; Firebase setzt dort
`Cache-Control: no-store` und `Referrer-Policy: no-referrer`. Bearer-Tokens gehen
ausschließlich an die eigene versionierte API, nicht an fremde Origins.

Das Supabase-SDK wird dynamisch geladen. `prebuild` erzeugt eine ignorierte
`apps/web/public/auth-config.json` aus den Umgebungswerten; nur URL und Publishable
Key werden ausgeliefert. Der Deployment-Workflow verlangt beide Werte.
`npm run start --workspace=@lost-key-finder/web` liest dafür lokal
`.env.dev.local`. Ohne Konfiguration bleiben öffentliche Seiten verfügbar und
die Login-Seite zeigt die fehlende Verfügbarkeit an.

### Status Phase 2: auf Dev abgenommen

Am 8. Oktober 2026 lief der vollständige Owner-Login im lokalen Browser gegen
echtes Supabase und die Firestore-Dev-Datenbank erfolgreich. Session-Refresh,
Reload, Replay-Ablehnung, Logout und `401` ohne Token wurden ebenfalls geprüft.
Die Firestore-Integrationstests sind erfolgreich;
die Login-Seite erreichte im mobilen Lighthouse-Snapshot 100 Accessibility-Punkte.

Der Konfigurationsabruf ist auf fünf Sekunden begrenzt, einschließlich des
Einlesens des Response-Bodys. Bei Timeout startet Angular mit leerer
Auth-Konfiguration; öffentliche Seiten bleiben verfügbar. Regressionstests
decken hängende Requests und Response-Bodies ab.

PR #3 wurde am 9. Oktober 2026 gemergt und erfolgreich nach Dev deployt.
Der Owner-Login über Firebase Hosting, echter SDK-Refresh, Reload, Replay-Ablehnung,
Logout und `401` ohne Token sind geprüft. Site URL, Hosting-Redirect und
Callback-Header `no-store`/`no-referrer` sind praktisch verifiziert.
Details und reproduzierbare Schritte stehen in
[Phase-2-Verifikation](docs/phase-2-verification.md).

## Weiterer Ausbau

- Fachbezogene Repository-/Provider-Interfaces erst mit den jeweiligen Use Cases.
- Phase 3 (noch nicht begonnen): Tags und öffentliche QR-Auflösung ergänzen.
- Keine zusätzlichen Reactive-, Queue- oder Native-Build-Schichten.
- Kotlin-Tests übernehmen die bisherigen Verträge aus `TESTING-PLAN.md`;
  Nest-/Jest-spezifische Beispiele dieses Plans sind historische Referenz.
- Weitere fachliche Queries/Indizes gegen echtes Firestore prüfen.

Die Foundation aus PR #2 ist nach Dev deployt: Firebase Hosting, Cloud Run und
Firestore-OIDC sind verifiziert. Die Owner-Implementierung aus PR #3 ist ebenfalls
nach Dev deployt und auf Commit `f01a8aa6797fde1a310cce314e23e712102f473b` abgenommen.

Der lokale Multi-Stage-Docker-Build und Containerstart wurden vor der Profilaufteilung verifiziert:
Liveness liefert `200`, Readiness ohne Datenbank `503`, der Version-Endpunkt
lieferte den erwarteten Vertrag. Der Container läuft als `10001:10001` ohne Root.
Mit der Profilaufteilung benötigt auch der Containerstart einen expliziten Mongo-URI.
