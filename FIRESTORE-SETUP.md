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
npm run firestore:start --workspace=@lost-key-finder/api
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
`NODE_ENV` wird für diesen lokalen Start auf `development` gesetzt. Der GCP-
Metadatenserver-basierte Produktions-URI funktioniert lokal nicht unverändert.

Die normale CI und `npm run verify` bleiben unabhängig von diesem Helfer und
benötigen weiterhin weder Google-Login noch Datenbankzugriff.

## Cloud Run: Runtime-Service-Account und OIDC

Der von Google bestätigte URI für den Cloud-Run-Service lautet:

```text
mongodb://3a7fec97-15a2-4d8f-958a-1fd020d5fc9d.europe-west6.firestore.goog:443/dev1?loadBalanced=true&tls=true&retryWrites=false&authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT:gcp,TOKEN_RESOURCE:FIRESTORE
```

Dieser URI enthält kein Passwort und kein Access Token. Der JVM-Treiber erhält
und erneuert die Credentials über die Cloud-Run-Service-Identität. Spring Boot
liest ihn aus `FIRESTORE_MONGODB_URI`; es ist kein eigener OIDC-Callback nötig.

Für Cloud Run fehlt aktuell noch ein dedizierter Runtime-Service-Account. Die
folgenden Provisionierungsbefehle sind eine Anleitung und wurden hier nicht
ausgeführt:

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
setzen. Der bestehende Deployment-Workflow erwartet seinen Namen in
`GCP_RUNTIME_SERVICE_ACCOUNT` und den URI über einen Secret-Manager-Eintrag,
dessen Name in `FIRESTORE_MONGODB_URI_SECRET` hinterlegt wird. Die Runtime benötigt
zusätzlich Secret-Zugriff auf genau diesen Eintrag. Es werden keine Service-
Account-JSON-Schlüssel benötigt.

`NODE_ENV=production` aktiviert die vorhandene Prüfung auf TLS,
`loadBalanced=true`, `retryWrites=false` und GCP-OIDC. Dies gilt auch für das
Dev-Deployment auf Cloud Run.

## Indizes und Transaktionen

- Automatische Indexerstellung durch Spring Data bleibt ausgeschaltet.
- Fachliche Indizes werden mit der jeweiligen Repository-Implementierung geplant
  und durch eine separate administrative Identität angelegt.
- Der erfolgreiche Smoke-Test ersetzt keine Parallelitäts- oder Indexprüfung.
- Die Cloud-Run-OIDC-Verbindung muss nach Deployment separat verifiziert werden.
- Ein kurzlebiges lokales Token gehört nicht in GitHub Secrets. Für den manuellen
  Provider-Workflow ist eine separate CI-Authentifizierung einzurichten.
