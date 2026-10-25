# Architektur

## Stack

| Bereich | Entscheidung |
| --- | --- |
| Frontend | Angular auf Firebase Hosting |
| API | Modularer Kotlin-/Spring-Boot-Monolith auf Java 25 und Cloud Run `europe-west6` |
| Daten | Firestore Enterprise mit MongoDB-Kompatibilität in `europe-west6` |
| Identität | Firebase Authentication; Auth-Verarbeitung in den USA akzeptiert |
| Extern | Mollie Hosted Checkout und providerneutrales SMTP |

`dev` und `prod` verwenden getrennte Projekte, Daten, Identitäten, Schlüssel und Service Accounts. Microservices, Broker und Event Sourcing sind nicht vorgesehen.

## Module und Daten

Der Monolith gliedert sich in `identity`, `tags`, `losscases`, `chat`, `payments`, `notifications` und `support`. Treibertypen bleiben an der Persistenzgrenze; zusätzliche Abstraktionen entstehen nur, wenn sie mehr als eine reale Implementierung oder einen klaren Testzweck haben.

Der implementierte Stand und die Umstellung auf sechs Collections stehen im [Datenbankschema](database-schema.md). Die folgende Liste beschreibt das längerfristige Zielbild einschliesslich noch nicht implementierter Funktionen.

Die wesentlichen Collections sind `users`, `tags`, `tagBatches`, `tagRegistrationDrafts`, `invitations`, `memberships`, `finderReportDrafts`, `lossCases`, `finderChats`, `messages`, `messageImages`, `payments`, `caseHistory`, `abuseReports`, `auditLog` und `deletionLedger`. Wachsende Listen liegen nicht im Tag-Dokument. Eindeutige Indizes sichern Identitäten, öffentliche Tag-ID, Finder-Chat pro Fall/Identität und Deduplizierungsschlüssel; der erst nach Provider-Erstellung vorhandene `providerPaymentId` ist eindeutig und sparse. Das Tag-Dokument hält nur den kleinen Zähler belegter Backup-Owner-Plätze, damit parallele Einladungen dasselbe Dokument aktualisieren und das Limit von drei nicht überschreiten.

Das Tag-Modell unterstützt `SELF_CREATED` mit sofortiger Besitzerzuordnung und `PREPRODUCED` mit Serie und separater Produktionsfreigabe. Unzugewiesene Tags haben keinen Primary Owner. Erstregistrierung und Verbrauch eines Registrierungsentwurfs erfolgen gemeinsam transaktional; öffentliche Auflösung erzeugt keinen Bestand. Alle Tags nutzen denselben öffentlichen Link. QR-Downloads und spätere Druckvorlagen sind Darstellungen dieser Identität. Die Admin-Gesamtübersicht und Besitzerlisten verwenden getrennt autorisierte API-Endpunkte. Details: [Tag-Plan](tag-produktion-und-registrierung-plan.md).

Mehrdokument-Invarianten verwenden Firestore-Transaktionen über die MongoDB-Schnittstelle. Externe Aufrufe laufen nie in einer Transaktion: Die Anwendung reserviert lokal, ruft den Provider auf und schliesst idempotent ab.

## API

Die API verwendet JSON, UTC nach ISO 8601, `/api/v1`, Cursor-Pagination und RFC-9457-Problem-Details. Kotlin-DTOs mit Bean Validation und springdoc-openapi führen den Vertrag; CI erzeugt daraus den Angular-Client.

Tag-Erstellung und jeder logische Checkout verwenden einen UUID-v4-`Idempotency-Key`; HTTP-Retries verwenden denselben Schlüssel. Wiederholungen liefern dieselbe Operation oder Ressourcenidentität, dynamische Felder wie Checkout-URLs werden erneut autorisiert und dürfen fehlen.

Bilder kommen als `multipart/form-data`, werden serverseitig bereinigt und nie in Listen geladen. Eingabe und Neukodierung sind auf je 5'000'000 Bytes begrenzt und bleiben damit inklusive Metadaten unter der 16-MiB-Dokumentgrenze von Firestore mit MongoDB-Kompatibilität. Die bereinigten BSON-Binärdaten liegen in einem eigenen `messageImages`-Dokument, das während der Meldung einem Draft und danach genau einer Nachricht zugeordnet ist. Der autorisierte Bildendpoint liefert den Medientyp der gespeicherten Neukodierung und setzt `X-Content-Type-Options: nosniff`.

## Authentifizierung

Firebase Authentication verwaltet E-Mail-Links und Sitzungen; das Backend entscheidet jede fachliche Berechtigung aus Firestore. Spring Security prüft RS256-Signatur, Google-JWKS, projektbezogene `iss` und `aud`, `exp`, optionales `nbf`, `iat`, `auth_time` und eine nicht leere Firebase-UID bis 128 Zeichen als `sub`. Rollen oder E-Mail allein gewähren keinen Ressourcenzugriff.

Geplante Admin-Autorisierung: `users.isAdmin` wird ausschliesslich manuell in der Datenbank gesetzt, fehlt standardmässig bzw. ist `false`. Jedes Admin-API prüft das per Firebase-UID geladene Konto auf `ACTIVE` und `isAdmin == true`; Firebase Custom Claims sind dafür nicht nötig. Konto-Provisionierung darf die Berechtigung nicht überschreiben, Profilmutationen dürfen sie nicht setzen. `/me` liefert sie nur lesbar zur Navigation; normale Admin-Anmeldung führt zur globalen Tagübersicht, fachliche Fortsetzungen bleiben erhalten. Admins dürfen künftig über die Tag-Historie alle zugehörigen Chats lesen; normale Benutzer behalten ihre jeweiligen Chat-Berechtigungen. Siehe [Tag-Historie](tag-history.md).

Firebase-ID-Tokens gelten normalerweise eine Stunde. Die API begrenzt die maximale Sitzungsdauer unabhängig vom SDK-Refresh auf 30 Tage ab dem signierten `auth_time`. Danach ist eine neue Anmeldung erforderlich. Die Gültigkeit eines Auth-Links bestimmt Firebase; eine anwendungsspezifische Vier-Stunden-Gültigkeit wird nicht vorausgesetzt. Finder-Drafts haben eigene serverseitige Fristen, die unabhängig von der Linkgültigkeit bei jeder Completion geprüft werden.

Der Client löst den E-Mail-Link mit Firebase ein. Im selben Browser stammt die E-Mail aus lokalem Speicher; auf einem anderen Gerät wird sie erneut abgefragt. Das initiale Callback-Dokument setzt `Referrer-Policy: no-referrer`, entfernt Query und Fragment synchron und behält nur die benötigten Auth-Linkfelder bis zur Einlösung im Speicher. E-Mail-Adressen und Sessions werden nicht in Callback-URLs geschrieben. Die API erhält ausschliesslich aktuelle ID-Tokens als Bearer-Token, keine Refresh-Tokens. Es werden keine Auth-Cookies eingeführt.

Der geplante Finder-Ablauf ergänzt eine nicht geheime `flowId` zur Draft-Zuordnung im Continue-Link. Die Callback-Verarbeitung muss diese vor dem Bereinigen separat erfassen und nach erfolgreicher Firebase-Anmeldung die Completion aufrufen. Der Zugriff auf den Draft verlangt unabhängig von `flowId` die exakt passende normalisierte, bestätigte E-Mail; ein Statuszugang benötigt zusätzlich ein eigenes nicht erratbares Draft-Token. Dieser Finder-Ablauf ist noch zu implementieren und geräteübergreifend abzunehmen.

Registrierungsentwürfe ergänzen eine getrennte, fest erlaubte Fortsetzung mit eigener Vorgangs-ID und serverseitiger Frist. Der Backend-Abschluss verlangt die passende bestätigte Identität sowie eine explizite Bestätigung; Anmeldung oder Link-GET ordnen keinen Tag zu. Auth-Codes und E-Mail gelangen nicht in die Fortsetzungs-URL. Beide Entwurfstypen werden anhand ihres serverseitigen Zwecks behandelt; keine frei wählbaren Redirects oder gemeinsamen Besitzrechte aus einer `flowId` ableiten.

Beim ersten Zugriff bestätigt `FirebaseIdentity` UID und verifizierte E-Mail zusätzlich über `accounts:lookup`; ein eindeutiger Index auf `users.firebaseUserId` sichert die Anlage. Lokale Kontosperren und Löschstatus gelten bei jedem Request sofort. Eine ausschliesslich in Firebase gesetzte Sperre oder ein Token-Widerruf wird bei bestehenden lokalen Konten derzeit nicht pro Request abgefragt; ein bereits ausgestelltes ID-Token kann bis zu seinem Ablauf weiter gültig sein. Support muss daher zuerst den lokalen Status sperren; ein sofortiger providerseitiger Widerruf benötigt künftig eine zusätzliche Statusprüfung.

`email-normalization-v1` trimmt, verlangt genau ein `@`, wandelt die gesamte Adresse nach der gemeinsamen Anwendungsregel in Kleinschreibung um und verwendet `domainToASCII()` für die Domain. Der Local Part ist 1 bis 64 Zeichen aus `a-z0-9.!#$%&'*+/=?^_{}|~-`, ohne Punkt am Rand oder doppelte Punkte. Die konvertierte Domain darf nicht leer sein; jedes Label ist 1 bis 63 Zeichen lang, enthält nur `a-z`, `0-9` und `-` und beginnt oder endet nicht mit `-`. Die Domain ist höchstens 253, die Gesamtadresse höchstens 254 Zeichen lang.

Benötigte Auth-Konfiguration: `FIREBASE_PROJECT_ID`, `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN` und `FIREBASE_APP_ID`. Backend und Browser müssen dasselbe Projekt verwenden. Administrative Kontolöschung wird bei ihrer Implementierung über einen dedizierten Firebase-Admin-Zugriff mit minimalen IAM-Rechten umgesetzt. Einrichtung und Grenzen sind in [Firebase Authentication](firebase-auth-setup.md) dokumentiert.

## Datenbank und Betrieb

Die Anwendung verwendet Spring Data MongoDB und den von Spring Boot verwalteten MongoDB-JVM-Treiber 5.x. Verwendete Operationen müssen gegen Firestore mit MongoDB-Kompatibilität geprüft werden. Produktion nutzt TLS, `loadBalanced=true`, `retryWrites=false` und eingebautes GCP-OIDC über `ENVIRONMENT:gcp,TOKEN_RESOURCE:FIRESTORE`. Der Cloud-Run-Service-Account erhält nur `roles/datastore.user`; ein eigener OIDC-Callback ist dafür nicht nötig. Indizes werden vor dem Anwendungsdeployment durch eine getrennte CI-Identität mit den nötigen administrativen Rechten angelegt, nie durch den Runtime-Service-Account.

Domainlogik wird mit Unit-Tests geprüft, jede verwendete Query, Transaktion und jeder Index zusätzlich gegen eine isolierte Firestore-Dev-Datenbank. Eine zweite MongoDB-Testumgebung ist kein fachlicher Ersatz für Firestore.

Benutzeraktionen werden vollständig durch den auslösenden HTTP-Request verarbeitet. Dazu gehören insbesondere Magic Links, Einladungen, E-Mails, Checkouts, Refunds und Account-Löschung. Es gibt keine Queue, keinen dauerhaften Worker, kein internes Event-Framework und keine periodische Outbox-Verarbeitung. Vor einem externen Aufruf wird der erforderliche lokale Zustand idempotent gesichert. Das UI zeigt danach `SENT`, `FAILED` oder `UNKNOWN` und bietet bei Bedarf eine manuelle Wiederholung an. `SENT` bedeutet nur SMTP-Annahme, nicht Zustellung; nach einem unklaren Providerergebnis kann eine manuelle Wiederholung selten doppelt zustellen.

Zeitabhängige Restarbeiten bündelt genau ein Google-Cloud-Scheduler-Aufruf täglich um 01:00 Uhr in `Europe/Zurich`. Er ruft `POST /api/v1/internal/maintenance/daily` mit einem OIDC-Token eines ausschliesslich dafür berechtigten Service Accounts auf und startet dadurch den auf null skalierten Cloud-Run-Service. Da Cloud Run keine Pfade einzeln per IAM schützt, prüft ein eigener Guard Signatur, Issuer, Audience und exakte Identität dieses Service Accounts. Der Endpunkt verarbeitet in begrenzten, idempotenten Batches nur abgelaufene Drafts, Fälle und Chat-Zugänge, unklare Mollie-Zahlungen sowie fällige Löschungen. Jede fachliche Lese- oder Schreiboperation prüft relevante Fristen zusätzlich sofort, sodass eine Ressource ab ihrer Deadline nicht bis zum nächsten Wartungslauf nutzbar bleibt. Mollie-Webhooks bleiben unmittelbare, providerseitig ausgelöste HTTP-Aufrufe.

Die minimale Supportoberfläche zeigt nur offene Inhaltsmeldungen und fehlgeschlagene Payment-/Refund-Vorgänge sowie das jeweilige Vorgangsdetail. Sie erlaubt ausschliesslich die dokumentierten Sperr-, Entsperr-, Refund- und Abschlussaktionen. Jede Einsicht und Aktion verlangt einen benannten Supportbenutzer, einen Pflichtgrund und eine Ticketreferenz und wird auditiert; freie Datenbanksuche und Benutzer-Impersonation sind ausgeschlossen. Der gesondert bestätigte Admin-Lesezugriff auf alle Chats eines Tags wird über die Tag-Historie umgesetzt.

Secrets kommen als Umgebungsvariablen aus Secret Manager. Logs enthalten keine Tokens, E-Mails, Nachrichten, Bilder oder Checkout-URLs. GitHub Actions testet beide Anwendungen, deployt `dev` automatisch und `prod` nach Freigabe.
