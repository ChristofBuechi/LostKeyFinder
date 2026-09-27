# Testing- und Mocking-Plan

## Ziel

Die verpflichtende lokale Prüfung und GitHub-CI laufen ohne Firestore, Supabase,
Mollie, SMTP, Docker, Provider-Credentials oder andere laufende Dienste. `npm ci`
benötigt weiterhin das npm-Registry oder einen geeigneten Cache.

Echte Provider-Prüfungen bleiben notwendig, werden aber in einen getrennten,
manuell gestarteten Workflow verschoben. Offline-Tests dürfen nicht als Nachweis
für Firestore-Index- oder Transaktionssemantik, Supabase-PKCE oder Mollie-Verhalten
ausgegeben werden.

`docs/` bleibt unverändert.

## Grundsätze

- Externe Systeme liegen hinter kleinen, anwendungsdefinierten Ports.
- Vendor-SDK-Typen verlassen den jeweiligen Infrastrukturadapter nicht.
- Ports entstehen zusammen mit einem konkreten Use Case, nicht vorsorglich.
- Featurebezogene Ports werden generischen `Repository<T>`-Abstraktionen
  vorgezogen.
- NestJS bindet Ports über Injection Tokens ein.
- Tests ersetzen Provider mit `overrideProvider(...)`.
- Handgeschriebene Fakes werden tiefen Jest-Mocks vorgezogen.
- Zeit, IDs und Zufall werden nur injizierbar gemacht, wenn ein Use Case dafür
  deterministische Kontrolle benötigt.
- Produktionscode importiert niemals Testcode.
- Tests prüfen primär Ergebnisse und Zustandsänderungen. Aufrufanzahlen werden
  nur geprüft, wenn sie fachlich relevant sind.

## Zielstruktur für neue Features

Die Struktur wird erst angelegt, wenn das jeweilige Feature implementiert wird:

```text
src/modules/<feature>/
├── application/
│   ├── <use-case>.ts
│   └── ports/
│       └── <provider>.port.ts
├── domain/
├── infrastructure/
│   └── <provider>.adapter.ts
└── <feature>.module.ts

test/support/
├── fakes/
├── fixtures/
└── create-test-application.ts
```

Beispiel für einen späteren Payment-Port:

```ts
export const PAYMENT_PROVIDER = Symbol('PAYMENT_PROVIDER');

export interface PaymentProvider {
  createPayment(command: CreatePaymentCommand): Promise<ProviderPayment>;
  getPayment(providerId: string): Promise<ProviderPayment>;
  refundPayment(providerId: string): Promise<void>;
}
```

Produktive Bindung:

```ts
{
  provide: PAYMENT_PROVIDER,
  useClass: MolliePaymentAdapter,
}
```

Testbindung:

```ts
.overrideProvider(PAYMENT_PROVIDER)
.useValue(paymentProviderFake)
```

## Arten von Test-Doubles

| Typ | Verwendung |
| --- | --- |
| Fake | Zustandsbehaftete Workflows, Repositories und Provider |
| Stub | Feste Antworten und definierte Fehler |
| Spy | Fachlich relevante externe Aufrufe beobachten |
| Mock | Enge technische Adaptergrenzen |

Fakes bieten fachliche Szenariomethoden wie `setReady(false)`,
`failNextRequest()` oder `loseNextCreateResponse()`. Jeder Test setzt den Zustand
zurück. Globale, zwischen Tests geteilte Jest-Mocks werden vermieden.

In-Memory-Repositories bilden Geschäftsregeln und fachliche Eindeutigkeit ab.
Sie simulieren ausdrücklich nicht die unterstützten Firestore-Operatoren,
Indizes oder Transaktionskonflikte.

## Umsetzungsschritt 1: Firestore-Test-Double

Der vorhandene Fake unter
`apps/api/test/support/firestore-service.fake.ts` wird als wiederverwendbares,
zustandssteuerbares Test-Double beibehalten und typisiert:

```ts
export class FirestoreServiceFake
  implements Pick<FirestoreService, 'isReady'> {
  // ...
}
```

Aktuell benötigt die Anwendung nur `isReady()`. Deshalb wird noch kein eigener
Readiness-Port in den Produktionscode eingeführt. `overrideProvider(FirestoreService)`
ist für den derzeitigen Umfang ausreichend. Ein separater Port wird erst
eingeführt, wenn mehrere Abhängigkeiten aggregiert werden oder fachlicher Code
auf Persistenz zugreift.

## Umsetzungsschritt 2: API-Tests

### HTTP-E2E-Tests

`apps/api/test/app.e2e-spec.ts` deckt folgende Szenarien ab:

| Szenario | Erwartung |
| --- | --- |
| Liveness | `200`, unabhängig vom Firestore-Fake |
| Readiness erfolgreich | `200` und `{ "status": "ok" }` |
| Readiness fehlgeschlagen | `503` als RFC-9457-Problem |
| Version | Version und Umgebung ohne Infrastrukturwerte |
| Unversionierte Route | `404` |
| Fehlende Route | Query-String fehlt in `instance` |
| Correlation ID | Jede Antwort enthält eine ID |
| Unerwarteter Fehler | `500` ohne interne Fehlermeldung oder Stack |
| Erlaubte CORS-Origin | Freigabe-Header ist vorhanden |
| Unbekannte CORS-Origin | Kein Freigabe-Header |

Für Validierungs- und 500er-Szenarien wird ein Controller ausschließlich im
NestJS Testing Module registriert. Es entsteht kein Debug-Endpunkt im
Produktionscode.

Der 500er-Test prüft insbesondere:

```ts
expect(response.body).not.toHaveProperty('stack');
expect(response.body).not.toHaveProperty('detail');
expect(JSON.stringify(response.body)).not.toContain('secret');
```

### Problem Details

Der Exception-Filter wird für folgende Fälle geprüft:

- String-Fehler eines 4xx-Status erscheint als `detail`.
- Validierungsfehler werden als generisches `detail` plus `errors` ausgegeben.
- 5xx-Details werden unterdrückt.
- Query-Parameter erscheinen nicht in `instance`.
- Der Content-Type ist `application/problem+json`.
- Logs enthalten weder Query-Werte noch Request-Body noch Fehlermeldung.

### Konfigurationsvalidierung

`apps/api/src/config/validation.spec.ts` erhält Tests für:

- ungültiges `NODE_ENV`;
- Ports außerhalb `1..65535`;
- ungültige Origins;
- Origins mit Pfad, Query oder abschließendem Slash;
- mehrere gültige Origins;
- ungültige MongoDB-Schemas;
- fehlende Produktionskonfiguration;
- fehlende OIDC-Parameter;
- vollständige gültige Produktionskonfiguration.

### Firestore-Adapter

Eine neue Datei
`apps/api/src/infrastructure/firestore/firestore.service.spec.ts` testet nur die
Verantwortung des Adapters. `MongoClient` wird an dieser technischen Grenze mit
Jest ersetzt; dafür wird keine zusätzliche Produktionsabstraktion eingeführt.

Testfälle:

- Ohne URI wird kein Client erzeugt.
- Ohne URI ist Readiness `false`.
- Initialisierung verbindet genau einmal.
- Ein erfolgreicher Ping ergibt `true`.
- Ein fehlgeschlagener Ping ergibt `false`.
- Shutdown schließt einen initialisierten Client.
- Development erzwingt keine Produktionsoptionen.
- Production verwendet `tls`, `loadBalanced` und `retryWrites: false`.

## Umsetzungsschritt 3: Angular-Tests

### Home-Komponente

Die neue Datei `apps/web/src/app/home/home.spec.ts` verwendet ausschließlich
Angular-Testmittel:

```ts
provideHttpClient()
provideHttpClientTesting()
HttpTestingController
```

Der generierte `HealthService` wird nicht gemockt. Dadurch testet der Request den
echten generierten Client bis zum Angular HTTP-Testbackend.

Testfälle:

1. Initial wird der Status `checking` angezeigt.
2. Genau ein Request geht an `/api/v1/health/ready`.
3. Eine erfolgreiche Antwort zeigt `available`.
4. Eine `503`-Antwort zeigt `unavailable`.
5. Ein HTTP-Fehler erzeugt keine unbehandelte Exception.
6. Der Statusbereich verwendet `aria-live="polite"`.
7. `HttpTestingController.verify()` findet keine offenen Requests.

### Sprache

`LanguageService` akzeptiert aus `localStorage` nur `de` und `en`. Andere Werte
fallen auf `de` zurück.

Tests prüfen:

- Wiederherstellung von `de` und `en`;
- Fallback bei ungültigen Werten;
- Aktualisierung von `<html lang>`;
- Persistenz nach dem Umschalten.

### App-Shell und Routing

`apps/web/src/app/app.spec.ts` prüft:

- Skip-Link auf `#main-content`;
- vorhandenes `<main id="main-content">`;
- zugänglichen Namen des Sprachschalters;
- sichtbaren Sprachwechsel;
- Aktualisierung der Dokumentensprache.

Mit `RouterTestingHarness` werden `/`, die Fehlerseite und der Rückweg zur
Startseite getestet. Playwright, Cypress und ein Browser-Server werden dafür
nicht eingeführt.

## Umsetzungsschritt 4: Angular-Scaffolding

Die pauschalen `skipTests: true`-Einträge werden aus
`apps/web/angular.json` entfernt. Neue Komponenten, Services, Guards, Pipes und
Interceptors erhalten damit standardmäßig Tests. Die SCSS-Vorgabe bleibt
erhalten.

## Umsetzungsschritt 5: CI-Trennung

### Verpflichtende Offline-CI

`.github/workflows/ci.yml` führt aus:

```text
npm ci
npm run generate:client
Generated-Client-Drift prüfen
npm run lint
npm run build
npm run test
npm run test:e2e
git diff --check
```

Aus der normalen CI werden entfernt:

- Docker-Build;
- Firestore-Job;
- Provider-Secrets;
- GitHub-Environment `dev`.

Der Docker-Build bleibt im Deployment-Workflow. Damit prüft CI den Quellcode und
das Verhalten, während Deployment das tatsächliche Image baut.

### Manuelle Provider-Verifikation

Eine neue Datei `.github/workflows/provider-verification.yml` erhält:

- Trigger ausschließlich über `workflow_dispatch`;
- geschütztes Environment `dev`;
- `FIRESTORE_MONGODB_URI` aus Secrets;
- einen begrenzten Timeout;
- Ausführung von `npm run test:integration`.

Der Workflow wird als Firestore-Smoke-Test benannt. Der aktuelle Test beweist
Verbindung und eine einfache Transaktion, aber noch keine vollständige Index-,
Parallelitäts- oder Cloud-Run-OIDC-Semantik.

`deploy.yml` bleibt zunächst unverändert. Erfolgreiche Offline-CI auf `main`
löst das Dev-Deployment aus; Produktion bleibt manuell freigabepflichtig.

## Umsetzungsschritt 6: Lokaler Prüfpfad

Das Root-`package.json` erhält:

```json
{
  "verify": "npm run generate:client && npm run lint && npm run build && npm run test && npm run test:e2e"
}
```

Der lokale Ablauf lautet danach:

```bash
npm ci
npm run verify
git diff --check
```

Dafür werden keine `.env`, Provider-Credentials, Datenbank oder laufenden
Container benötigt.

## Coverage

Coverage wird zunächst erfasst, aber nicht mit einer willkürlichen globalen
Prozentzahl bewertet.

1. API-Coverage wird mit dem vorhandenen Jest erfasst.
2. Angular-Coverage wird nur mit einer zur installierten Vitest-Version passenden
   Integration ergänzt.
3. Generierter Code wird von Prozentgrenzen ausgeschlossen.
4. Nach den neuen Szenariotests wird die Ausgangsbasis bestimmt.
5. Schwellen werden knapp unter dieser Basis gesetzt und anschließend nur erhöht.

Vollständige Szenarien für Berechtigungen, Normalisierung, Zustandsübergänge,
Idempotenz, Zahlungsfreigaben und Löschung sind wichtiger als eine hohe globale
Prozentzahl.

## Spätere Ports und Fakes

| Phase | Produktions-Port | Test-Double |
| --- | --- | --- |
| Identity | `IdentityProvider` | `IdentityProviderFake` |
| Persistenz | featurebezogene Repositories | In-Memory-Repositories |
| Benachrichtigungen | `MailSender` | `MailSenderFake` |
| Payment | `PaymentProvider` | `PaymentProviderFake` |
| Zeitabhängigkeit | `Clock` | `FixedClock` |
| IDs und Idempotenz | `IdGenerator` | `SequenceIdGenerator` |
| Bilder | `ImageProcessor` | `ImageProcessorFake` |

Diese Ports werden jeweils zusammen mit dem ersten echten Use Case erstellt.
Es werden keine vollständigen Supabase-, Mollie- oder SMTP-Emulatoren gebaut.

## Implementierungsreihenfolge

1. Vorhandenen Firestore-Fake festigen.
2. API-Infrastrukturtests erweitern.
3. Angular-Tests und LanguageService-Korrektur ergänzen.
4. Angular-Scaffolding korrigieren.
5. Offline-CI und Provider-Verifikation trennen.
6. Lokales `verify`-Script ergänzen.
7. Gesamten Prüfpfad ausführen.
8. Coverage-Basis erfassen und spätere Schwellen festlegen.

## Abnahmekriterien

- `npm run verify` läuft ohne externe Anwendungssysteme erfolgreich.
- Die verpflichtende GitHub-CI benötigt keine Provider-Secrets.
- Kein Offline-Test sendet Requests an Firestore, Supabase oder Mollie.
- Health-Erfolg und Health-Ausfall werden deterministisch geprüft.
- 5xx-Antworten enthalten keine internen Details.
- Der Angular-Test verwendet den generierten Client bis zum HTTP-Testbackend.
- Sprache, Routing und grundlegende Accessibility sind getestet.
- Neue Angular-Artefakte erhalten standardmäßig Tests.
- Die echte Firestore-Verifikation läuft ausschließlich manuell.
- Build, Lint, Unit-, E2E- und Client-Drift-Prüfung sind erfolgreich.
- `docs/` ist unverändert.
