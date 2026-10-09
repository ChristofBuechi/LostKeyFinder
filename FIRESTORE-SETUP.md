# Firestore-Konfiguration für Kotlin / Spring Boot

## Bestehende Dev-Datenbank

| Einstellung | Wert |
| --- | --- |
| Google-Cloud-Projekt | `lost-key-finder-dev` |
| Datenbank-ID | `dev1` |
| Edition | Enterprise |
| Region | `europe-west6` |
| MongoDB-kompatibler Zugriff | Aktiviert |
| Firestore Native API | Deaktiviert |
| Löschschutz | Aktiviert |

Der Zugriff unserer Anwendung erfolgt ausschließlich über Spring Data MongoDB
und den MongoDB-JVM-Treiber. Autorisierung verwendet Google Cloud IAM, nicht
Firebase Security Rules. Die Native API muss dafür nicht eingeschaltet werden.

## Lokal: Google-Konto und kurzlebiges Token

```bash
gcloud auth login
npm run firestore:verify --workspace=@lost-key-finder/api
```

Der Helfer fragt den Basis-URI und ein Access Token mit `gcloud` ab und übergibt
sie ausschließlich im Environment des Gradle-Prozesses. Er schreibt keine
Credentials-Datei und gibt den URI mit Token nicht aus. Das Google-Konto muss
Zugriff auf die Dev-Datenbank haben. Ein separates ADC-Login ist für diesen
Weg nicht erforderlich.

Der Test wurde erfolgreich gegen `dev1` ausgeführt: Ping, Spring-Data-Session-
Transaktion, Insert, Update, Readback und anschließender Cleanup. Die temporären
Probe-Dokumente liegen in `integrationProbes`.

Backend mit echter Dev-Datenbank starten:

```bash
npm run start:dev --workspace=@lost-key-finder/api
```

Danach prüfen:

```bash
curl --fail http://localhost:3000/api/v1/health/ready
```

Der reale Backendstart mit diesem Helfer wurde ebenfalls verifiziert:
Spring Boots Mongo-Health-Contributor liefert gegen `dev1` eine gesunde
Readiness (`200`, `{"status":"ok"}`).

Das lokale Token gilt üblicherweise etwa eine Stunde. Der Helfer erneuert es bei
jedem Aufruf; für längere lokale Sessions den Backendprozess neu starten.
`SPRING_PROFILES_ACTIVE` wird für diesen lokalen Start auf `dev` gesetzt; die API
meldet weiterhin die Umgebung `development`. Der GCP-
Metadatenserver-basierte Produktions-URI funktioniert lokal nicht unverändert.

Die normale CI und `npm run verify` bleiben unabhängig von diesem Helfer und
verwenden das Profil `ci` und benötigen weder Google-Login noch eine externe Datenbank.

Für die Spring-Kontexttests startet JUnit unter `ci` automatisch `mongo-java-server` mit
In-Memory-Backend auf einem dynamischen Loopback-Port. Spring Data und der
JVM-Treiber führen dort Mapping und CRUD aus. Die Library unterstützt keine
Transaktionen und ersetzt keine Firestore-Index-, Parallelitäts- oder OIDC-
Prüfung. Details stehen in `KOTLIN-FOUNDATION.md`.

## Cloud Run: Runtime-Service-Account und OIDC

Der von Google bestätigte URI für den Cloud-Run-Service lautet:

```text
mongodb://3a7fec97-15a2-4d8f-958a-1fd020d5fc9d.europe-west6.firestore.goog:443/dev1?loadBalanced=true&tls=true&retryWrites=false&authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:gcp,TOKEN_RESOURCE:FIRESTORE
```

Dieser URI enthält kein Passwort und kein Access Token. Der JVM-Treiber erhält
und erneuert die Credentials über die Cloud-Run-Service-Identität. Spring Boot
liest ihn aus `FIRESTORE_MONGODB_URI`; es ist kein eigener OIDC-Callback nötig.

Die Runtime-Identität verwendet ausschließlich Datenzugriff auf `dev1`.
Die folgenden Befehle beschreiben die dafür nötigen Rechte:

```bash
gcloud iam service-accounts create lost-key-finder-api \
  --project=lost-key-finder-dev \
  --display-name="Lost Key Finder API Runtime"

gcloud projects add-iam-policy-binding lost-key-finder-dev \
  --member="serviceAccount:lost-key-finder-api@lost-key-finder-dev.iam.gserviceaccount.com" \
  --role="roles/datastore.user" \
  --condition='expression=resource.name == "projects/lost-key-finder-dev/databases/dev1",title=firestore-dev1'
```

Den Service Account anschließend beim Cloud-Run-Deployment als Service Identity
setzen. Der Deployment-Workflow erwartet seinen Namen in der GitHub-Environment-
Variable `GCP_RUNTIME_SERVICE_ACCOUNT` und den passwortlosen OIDC-URI in
`FIRESTORE_MONGODB_URI`. Dieser URI benötigt keinen Secret Manager. Es werden
keine Service-Account-JSON-Schlüssel benötigt.

Der Deployment-Workflow setzt `SPRING_PROFILES_ACTIVE=dev` für Dev und `prod`
für Produktion. Beide Profile benötigen `FIRESTORE_MONGODB_URI`; `prod` benötigt
zusätzlich `ALLOWED_ORIGINS` und erzwingt TLS, `loadBalanced=true`,
`retryWrites=false` und GCP-OIDC. Auch das Dev-Deployment verwendet den
Cloud-Run-OIDC-URI; lokal erlaubt `dev` das kurzlebige PLAIN-Access-Token.
`NODE_ENV` wählt kein Backendprofil und beeinflusst diese Prüfung nicht.

## GitHub Actions für Dev

Im Projekt `lost-key-finder-dev` sind eingerichtet:

| Ressource | Zweck |
| --- | --- |
| Artifact Registry `europe-west6/lost-key-finder` | API-Containerimages |
| `lost-key-finder-api@lost-key-finder-dev.iam.gserviceaccount.com` | Cloud-Run-Runtime, Datenzugriff nur auf `dev1` |
| `github-provider@lost-key-finder-dev.iam.gserviceaccount.com` | Firestore-Verifikation, Datenzugriff nur auf `dev1` |
| `github-deploy@lost-key-finder-dev.iam.gserviceaccount.com` | Images schreiben, Cloud Run und Hosting deployen, Runtime-Service-Account zuweisen |
| WIF-Pool `github`, Provider `lost-key-finder` | Kurzlebige GitHub-OIDC-Authentifizierung |

WIF akzeptiert ausschließlich die numerischen IDs dieses Repositorys/Owners,
das Environment `dev`, den Branch `main` und die beiden konkreten Workflows
`deploy.yml`/`provider-verification.yml`. Die Workflows können jeweils nur ihre
eigene Deployment-/Provider-Identität verwenden. Die Runtime-Identität kann
nicht direkt von GitHub impersoniert werden.

GitHub verwendet den unveränderlichen Subject-Prefix
`repo:ChristofBuechi@2494089/LostKeyFinder@1398628425`. Die WIF-Bedingung prüft
den vollständigen Subject mit `:environment:dev`; ein Vergleich mit dem älteren
Subject ohne IDs würde beide Workflows ablehnen.

Das GitHub-Environment `dev` erlaubt nur `main`. Seine Variablen sind
`GCP_PROJECT_ID`, `GCP_REGION`, `ARTIFACT_REPOSITORY`,
`GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOY_SERVICE_ACCOUNT`,
`GCP_PROVIDER_SERVICE_ACCOUNT`, `GCP_RUNTIME_SERVICE_ACCOUNT`,
`FIRESTORE_MONGODB_URI` und `ALLOWED_ORIGINS`. Keine davon enthält ein Passwort
oder Access Token. Die temporäre Auth-Datei der GitHub-Action wird weder
versioniert noch in den Docker-Build-Kontext übernommen.

Nach Merge und erfolgreicher `main`-Push-CI deployt der vorhandene Workflow
automatisch nach Dev und prüft Readiness und Version über Firebase Hosting.
Der Cloud-Run-Service wird beim ersten Deployment erstellt (maximal zwei
Instanzen). Der manuelle Provider-Workflow ist nach Merge über `main` ausführbar:

```bash
gh workflow run provider-verification.yml --ref main
```

PR #2 ist gemergt. Deployment und Provider-Verifikation waren nach der
Subject-Korrektur erfolgreich:

- Deployment: https://github.com/ChristofBuechi/LostKeyFinder/actions/runs/37116036775
- Provider-Test: https://github.com/ChristofBuechi/LostKeyFinder/actions/runs/37147868578
- Hosting/API: https://lost-key-finder-dev.web.app

Readiness und Version sind über Hosting verifiziert, damit auch die
Cloud-Run-OIDC-Verbindung. Ein Prod-Environment und produktive Cloud-Ressourcen
werden separat eingerichtet.

### User-Index

Vor dem ersten Owner-Deployment ist `users.supabaseUserId` eindeutig zu indizieren.
Der Index ist in `dev1` angelegt; Runtime und Provider-Test erstellen keine Indizes.
Die administrative Identität verwendet dafür:

```bash
gcloud firestore indexes composite create \
  --project=lost-key-finder-dev --database=dev1 \
  --collection-group=users --query-scope=collection-group \
  --api-scope=mongodb-compatible-api --density=dense --unique \
  --field-config=field-path=supabaseUserId,order=ascending
```

`FirestoreUserIntegrationTest` prüft Query/Mapping, parallele Erstzugriffe,
abgelehnte Duplikate und Statusentzug. Er löscht ausschließlich seine zufällig
erzeugten Test-Subjects. JWTs und `/user`-Antworten stammen dabei vom lokalen
Testserver; die echte Supabase-Anbindung ist dadurch nicht abgenommen.

## Indizes und Transaktionen

- Automatische Indexerstellung durch Spring Data bleibt ausgeschaltet.
- Fachliche Indizes werden mit der jeweiligen Repository-Implementierung geplant
  und durch eine separate administrative Identität angelegt.
- Der erfolgreiche Smoke-Test ersetzt keine Parallelitäts- oder Indexprüfung.
- Die Cloud-Run-OIDC-Verbindung muss nach Deployment separat verifiziert werden.
- Ein kurzlebiges lokales Token gehört nicht in GitHub Secrets. Der manuelle
  Provider-Workflow authentifiziert sich über WIF und verwendet denselben
  `firestore:verify`-Helfer wie lokal.
