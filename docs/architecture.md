# Architektur

## Stack

| Bereich | Entscheidung |
| --- | --- |
| Frontend | Angular auf Firebase Hosting |
| API | Modularer NestJS-Monolith mit Fastify auf Node.js LTS und Cloud Run `europe-west6` |
| Daten | Firestore Enterprise mit MongoDB-Kompatibilität in `europe-west6` |
| Identität | Supabase Auth in `eu-central-2` |
| Extern | Mollie Hosted Checkout und providerneutrales SMTP |

`dev` und `prod` verwenden getrennte Projekte, Daten, Identitäten, Schlüssel und Service Accounts. Microservices, Broker und Event Sourcing sind nicht vorgesehen.

## Module und Daten

Der Monolith gliedert sich in `identity`, `tags`, `losscases`, `chat`, `payments`, `notifications` und `support`. Treibertypen bleiben an der Persistenzgrenze; zusätzliche Abstraktionen entstehen nur, wenn sie mehr als eine reale Implementierung oder einen klaren Testzweck haben.

Die wesentlichen Collections sind `users`, `tags`, `invitations`, `memberships`, `finderReportDrafts`, `lossCases`, `finderChats`, `messages`, `messageImages`, `payments`, `caseHistory`, `abuseReports`, `auditLog` und `deletionLedger`. Wachsende Listen liegen nicht im Tag-Dokument. Eindeutige Indizes sichern Identitäten, öffentliche Tag-ID, Finder-Chat pro Fall/Identität und Deduplizierungsschlüssel; der erst nach Provider-Erstellung vorhandene `providerPaymentId` ist eindeutig und sparse. Das Tag-Dokument hält nur den kleinen Zähler belegter Backup-Owner-Plätze, damit parallele Einladungen dasselbe Dokument aktualisieren und das Limit von drei nicht überschreiten.

Mehrdokument-Invarianten verwenden Firestore-Transaktionen über die MongoDB-Schnittstelle. Externe Aufrufe laufen nie in einer Transaktion: Die Anwendung reserviert lokal, ruft den Provider auf und schliesst idempotent ab.

## API

Die API verwendet JSON, UTC nach ISO 8601, `/api/v1`, Cursor-Pagination und RFC-9457-Problem-Details. NestJS-DTOs mit `class-validator` und `@nestjs/swagger` führen den Vertrag; CI erzeugt daraus den Angular-Client.

Tag-Erstellung und jeder logische Checkout verwenden einen UUID-v4-`Idempotency-Key`; HTTP-Retries verwenden denselben Schlüssel. Wiederholungen liefern dieselbe Operation oder Ressourcenidentität, dynamische Felder wie Checkout-URLs werden erneut autorisiert und dürfen fehlen.

Bilder kommen als `multipart/form-data`, werden serverseitig bereinigt und nie in Listen geladen. Eingabe und Neukodierung sind auf je 5'000'000 Bytes begrenzt und bleiben damit inklusive Metadaten unter der 16-MiB-Dokumentgrenze von Firestore mit MongoDB-Kompatibilität. Die bereinigten BSON-Binärdaten liegen in einem eigenen `messageImages`-Dokument, das während der Meldung einem Draft und danach genau einer Nachricht zugeordnet ist. Der autorisierte Bildendpoint liefert den Medientyp der gespeicherten Neukodierung und setzt `X-Content-Type-Options: nosniff`.

## Authentifizierung

Supabase verwaltet Magic Links und Sessions; das Backend entscheidet jede fachliche Berechtigung aus Firestore. Ein NestJS-Guard prüft JWT-Signatur und `iss`, `aud`, `exp`, `nbf` sowie `sub` gegen die Projekt-JWKS. Rollen oder E-Mail allein gewähren keinen Ressourcenzugriff.

Magic Links gelten vier Stunden, Access Tokens 60 Minuten und erneuerbare Sessions 30 Tage. Ein noch nicht versandter Finder-Draft läuft nach 15 Minuten ab; nach Versand endet er spätestens mit dem ersten Link.

Finder-PKCE-Verifier werden für den geräteübergreifenden Ablauf kurzzeitig verschlüsselt im Draft gehalten. Das initiale Callback-Dokument setzt `Referrer-Policy: no-referrer`, entfernt Code und `flowId` vor asynchronen Aufrufen und tauscht den Code nur über das Backend. Die nicht cachebare Exchange-Antwort übergibt Access- und Refresh-Token einmalig im Response-Body an die Angular-Anwendung, die sie mit dem Supabase-Client als normale SPA-Session setzt; Sessions stehen nie in URLs oder Logs. Es werden keine Auth-Cookies eingeführt.

`email-normalization-v1` trimmt, verlangt genau ein `@`, wandelt die gesamte Adresse passend zu Supabase in Kleinschreibung um und verwendet `domainToASCII()` für die Domain. Der Local Part ist 1 bis 64 Zeichen aus `a-z0-9.!#$%&'*+/=?^_{}|~-`, ohne Punkt am Rand oder doppelte Punkte. Die konvertierte Domain darf nicht leer sein; jedes Label ist 1 bis 63 Zeichen lang, enthält nur `a-z`, `0-9` und `-` und beginnt oder endet nicht mit `-`. Die Domain ist höchstens 253, die Gesamtadresse höchstens 254 Zeichen lang.

Benötigte Auth-Konfiguration: `SUPABASE_URL`, Publishable/Anon Key, JWT-Audience und -Algorithmus sowie ein nur dem Identity-Adapter zugängliches Secret für Account-Löschung. Standard-Issuer und JWKS-URL werden aus `SUPABASE_URL` abgeleitet.

## Datenbank und Betrieb

Die Anwendung verwendet `mongodb` 6.x, mindestens 6.7; ein anderer Major wird erst nach Freigabe durch Firestore übernommen. Produktion nutzt TLS, `loadBalanced=true`, `retryWrites=false` und eingebautes GCP-OIDC über `ENVIRONMENT:gcp,TOKEN_RESOURCE:FIRESTORE`. Der Cloud-Run-Service-Account erhält nur `roles/datastore.user`; ein eigener OIDC-Callback ist dafür nicht nötig. Indizes werden vor dem Anwendungsdeployment durch eine getrennte CI-Identität mit den nötigen administrativen Rechten angelegt, nie durch den Runtime-Service-Account.

Domainlogik wird mit Unit-Tests geprüft, jede verwendete Query, Transaktion und jeder Index zusätzlich gegen eine isolierte Firestore-Dev-Datenbank. Eine zweite MongoDB-Testumgebung ist kein fachlicher Ersatz für Firestore.

Benutzeraktionen werden vollständig durch den auslösenden HTTP-Request verarbeitet. Dazu gehören insbesondere Magic Links, Einladungen, E-Mails, Checkouts, Refunds und Account-Löschung. Es gibt keine Queue, keinen dauerhaften Worker, kein internes Event-Framework und keine periodische Outbox-Verarbeitung. Vor einem externen Aufruf wird der erforderliche lokale Zustand idempotent gesichert. Das UI zeigt danach `SENT`, `FAILED` oder `UNKNOWN` und bietet bei Bedarf eine manuelle Wiederholung an. `SENT` bedeutet nur SMTP-Annahme, nicht Zustellung; nach einem unklaren Providerergebnis kann eine manuelle Wiederholung selten doppelt zustellen.

Zeitabhängige Restarbeiten bündelt genau ein Google-Cloud-Scheduler-Aufruf täglich um 01:00 Uhr in `Europe/Zurich`. Er ruft `POST /api/v1/internal/maintenance/daily` mit einem OIDC-Token eines ausschliesslich dafür berechtigten Service Accounts auf und startet dadurch den auf null skalierten Cloud-Run-Service. Da Cloud Run keine Pfade einzeln per IAM schützt, prüft ein eigener Guard Signatur, Issuer, Audience und exakte Identität dieses Service Accounts. Der Endpunkt verarbeitet in begrenzten, idempotenten Batches nur abgelaufene Drafts, Fälle und Chat-Zugänge, unklare Mollie-Zahlungen sowie fällige Löschungen. Jede fachliche Lese- oder Schreiboperation prüft relevante Fristen zusätzlich sofort, sodass eine Ressource ab ihrer Deadline nicht bis zum nächsten Wartungslauf nutzbar bleibt. Mollie-Webhooks bleiben unmittelbare, providerseitig ausgelöste HTTP-Aufrufe.

Die minimale Supportoberfläche zeigt nur offene Inhaltsmeldungen und fehlgeschlagene Payment-/Refund-Vorgänge sowie das jeweilige Vorgangsdetail. Sie erlaubt ausschliesslich die dokumentierten Sperr-, Entsperr-, Refund- und Abschlussaktionen. Jede Einsicht und Aktion verlangt einen benannten Supportbenutzer, einen Pflichtgrund und eine Ticketreferenz und wird auditiert; freie Datenbanksuche, Benutzer-Impersonation und allgemeines Lesen von Chats sind ausgeschlossen.

Secrets kommen als Umgebungsvariablen aus Secret Manager. Logs enthalten keine Tokens, E-Mails, Nachrichten, Bilder oder Checkout-URLs. GitHub Actions testet beide Anwendungen, deployt `dev` automatisch und `prod` nach Freigabe.
