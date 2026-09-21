# Implementierungsplan Phase 1

## Ziel und Leitplanken

Dieser Plan setzt die in den übrigen Dokumenten beschriebene Phase 1 als modularen NestJS-Monolithen und Angular-Anwendung um. Der bestehende HTML-/LocalStorage-Prototyp ist keine technische Grundlage und wird nicht migriert.

- Ein npm-Workspaces-Monorepo enthält Frontend, API und den aus OpenAPI erzeugten Client.
- Benutzeraktionen werden vollständig im auslösenden HTTP-Request verarbeitet.
- Es gibt keine Queue, keinen Worker, keinen Broker und kein internes Event-Framework.
- Nur `POST /api/v1/internal/maintenance/daily` wird täglich um 01:00 Uhr `Europe/Zurich` durch Google Cloud Scheduler aufgerufen.
- Firestore mit MongoDB-Kompatibilität speichert alle Phase-1-Daten. Bilder liegen als BSON-Binärdaten in separaten `messageImages`-Dokumenten, nie in `messages` oder Listenantworten. Das vermeidet einen zusätzlichen Speicherdienst.
- Jede Phase liefert eine sichtbare oder automatisiert prüfbare Fähigkeit. Kein Platzhalter-Code für spätere Phasen.
- Sicherheits-, Datenschutz-, i18n- und WCAG-Anforderungen werden in den jeweiligen Phasen umgesetzt, nicht nachträglich gesammelt.

## Voraussetzungen

Vor Beginn müssen vorhanden sein:

- getrennte Google-Cloud-Projekte für `dev` und `prod`;
- je Projekt eine Firestore-Enterprise-Datenbank mit MongoDB-Kompatibilität in `europe-west6`;
- getrennte Supabase-Projekte in `eu-central-2`;
- ein Mollie-Testprofil für `dev`;
- eine Domain für `dev` und die erlaubten Supabase-Redirect-URLs;
- ein benannter erster Supportbenutzer und dessen Supabase-Identität.

Die Go-live-Entscheidungen aus [Offene Go-live-Entscheidungen](open-decisions.md) blockieren nicht die ersten Phasen. Sie müssen vor den jeweils betroffenen Produktionsphasen abgeschlossen sein.

## Repository und Konventionen

```text
lost-key-finder/
  apps/
    web/                 Angular-Anwendung
    api/                 NestJS mit Fastify
  packages/
    api-client/          aus OpenAPI erzeugter Angular-Client
  docs/
  .github/workflows/
  package.json
  package-lock.json
  tsconfig.base.json
```

- Node.js: aktuelle LTS, in `.nvmrc` und `package.json#engines` festgehalten.
- Paketmanager: npm Workspaces; kein Nx, Turborepo oder weiterer Build-Orchestrator.
- API: JSON, UTC-ISO-8601, `/api/v1`, RFC-9457-Problem-Details, Cursor-Pagination.
- Fehlercodes: stabile maschinenlesbare `type`-URLs und keine sensitiven Detailtexte.
- IDs: interne IDs UUID-v4; öffentliche Tag-ID UUID-v4; MongoDB `_id` wird nicht nach aussen exponiert.
- Frontend: Angular Standalone-Komponenten, Signals und Signal Forms; `HttpClient` für Mutationen, `httpResource` für einfache Leseansichten.
- Formulare: echte `label`-Zuordnung, serverseitige Validierung als Wahrheit, sichtbare Fehler und Live-Regionen nur für relevante Statusänderungen.
- Sprache: UI-Texte ab der ersten UI-Phase in Deutsch und Englisch. Die gewählte Sprache steht in URL oder persistentem lokalen Setting; keine automatische Sprachentscheidung aus E-Mail oder IP.
- Tests: Unit-Tests für Fachlogik, API-Tests für HTTP/Autorisierung und Integrations-Tests gegen eine isolierte echte Firestore-Dev-Datenbank für jede produktiv verwendete Query, Transaktion und jeden Index.

## Datenmodell-Grundsätze

Die finalen Felder entstehen jeweils in der passenden Phase. Alle Dokumente enthalten mindestens `id`, `createdAt` und `updatedAt`, sofern sie nicht unveränderbare Audit-Einträge sind. Zeitwerte sind UTC.

| Collection | Zweck | Wichtige Regeln |
| --- | --- | --- |
| `users` | lokale Identität zu Supabase-`sub` | kein Passwort, kein Zugriff allein aufgrund einer JWT-E-Mail |
| `tags` | öffentliche UUID, Primary Owner und Status | genau ein Primary Owner; keine wachsenden Listen |
| `memberships` | aktive und historische Backup-Owner-Mitgliedschaften | jede angenommene Einladung erzeugt neue `membershipId` |
| `invitations` | offene Backup-Owner-Einladungen | E-Mail nach Annahme entfernen |
| `finderReportDrafts` | unbestätigte Fundmeldung | Ablauf und verschlüsselter PKCE-Verifier |
| `lossCases` | Verlustfall und Statusmaschine | höchstens ein aktiver Fall pro Tag |
| `finderChats` | privater Chat eines Finders pro Fall | eindeutige Fall-/Finder-Identität |
| `messages` | Textnachrichten und Verweis auf Bild | keine Bilddaten im Dokument |
| `messageImages` | bereinigte BSON-Bilddaten und Metadaten | während der Meldung einem Draft, danach genau einer Nachricht zugeordnet; nie listen |
| `payments` | logischer Checkout, Providerstatus und Refunds | stabile Idempotenz, sparse eindeutige Provider-ID |
| `caseHistory` | unveränderbare fachliche Verlaufseinträge | keine Nachrichtentexte |
| `abuseReports` | gemeldete Nachricht und Supportstatus | Support sieht nur die gemeldete Nachricht |
| `auditLog` | unveränderbares Support-Audit | Akteur, Aktion, Grund, Ticketreferenz |
| `deletionLedger` | wiederaufnehmbare Löschung und Restore-Nacharbeit | pseudonyme Referenzen, keine E-Mail-Kopien |

Anzulegende Indizes werden vor dem ersten verwendenden Endpunkt durch eine getrennte CI-Identität mit administrativen Rechten erstellt und gegen Firestore geprüft. Der Runtime-Service-Account verwaltet keine Indizes:

- `users.supabaseUserId` eindeutig;
- `tags.publicId` eindeutig;
- `tags.creationKey` eindeutig;
- `tags.primaryOwnerUserId` plus `status` für Owner-Listen;
- `memberships.tagId` plus `userId` plus `status`;
- `invitations.tagId` plus `status`;
- `lossCases.tagId` plus `activeKey` eindeutig, wobei nur aktive Fälle denselben stabilen Wert erhalten;
- `finderChats.lossCaseId` plus `finderIdentityId` eindeutig;
- `messages.finderChatId` plus `createdAt` plus `id` für Cursor-Pagination;
- `payments.providerPaymentId` eindeutig und sparse;
- `payments.logicalCheckoutKey` eindeutig;
- `abuseReports.messageId` plus `reporterUserId` eindeutig;
- `payments.lossCaseId` plus `status`;
- alle Wartungs- und Supportlisten nur nach ihren tatsächlich verwendeten Status-/Zeitfeldern.

## Phase 0: Technische Risiken beweisen

**Ziel:** Die drei kritischsten Fremdintegrationen und die zentrale Firestore-Transaktion funktionieren gegen ihre echten Zielsysteme, bevor Produktcode darauf aufgebaut wird.

### Arbeitsschritte

1. Eine minimale NestJS-Anwendung verbindet sich mit der Firestore-Dev-Datenbank über `mongodb` 6.x, `loadBalanced=true`, TLS, `retryWrites=false` und Cloud-Run-OIDC.
2. Ein Integrationstest verwendet vorab angelegte Indizes, führt eine Multi-Dokument-Transaktion aus und testet Parallelität: zwei gleichzeitige Aufrufe erzeugen genau einen `lossCases`-Datensatz und zwei unterschiedliche `finderChats`.
3. Ein isolierter Supabase-Test implementiert den Finder-Magic-Link-Ablauf: Draft mit verschlüsseltem PKCE-Verifier, Link auf einem zweiten Browserprofil, Backend-Exchange, einmalige Tokenübergabe an den Angular-Supabase-Client, exakte normalisierte und verifizierte E-Mail-Prüfung, Refresh und Logout.
4. Ein Mollie-Test prüft Checkout-Erstellung mit Idempotency-Key, verlorene Create-Antwort, Wiederauffinden über Metadata und Zeitfenster, doppelten Webhook, Statusabfrage und atomaren Access Grant.

### Abnahme

- Alle drei Spikes laufen in CI gegen `dev` oder eine dafür reservierte Firestore-Dev-Datenbank.
- Firestore-Transaktionen, Indexsemantik und OIDC-Verbindung sind mit dem echten Dienst bewiesen.
- Der Auth-Flow enthält weder Code noch Session in Log, History oder Referrer.
- Sessionübergabe, Refresh, Logout und Replay-Ablehnung funktionieren ohne Auth-Cookies.
- Doppelter Mollie-Webhook und parallele Statusabfrage erzeugen genau einen Grant.

### Ergebnis

Die Spike-Implementierungen werden nur behalten, wenn sie direkt in die folgenden Module übergehen. Andernfalls bleiben sie kleine Integrationstests; keine zweite Architektur entsteht daraus.

## Phase 1: Monorepo und deploybarer Durchstich

**Ziel:** Eine deployte Angular-Seite ruft eine versionierte NestJS-API auf, die Firestore erreicht.

### Arbeitsschritte

1. npm-Workspaces mit `apps/web`, `apps/api` und `packages/api-client` erzeugen.
2. Angular mit Routing, i18n-Grundgerüst, globalen Styles, Skip-Link, Fehlerseite und einem öffentlichen Startscreen anlegen.
3. NestJS mit Fastify, globaler DTO-Validierung, RFC-9457-Exception-Filter, Request-Correlation-ID, Health- und Readiness-Endpunkt anlegen.
4. OpenAPI aus NestJS erzeugen und daraus den Angular-Client im Build generieren. CI schlägt bei nicht generiertem Client fehl.
5. Konfiguration strikt validieren: lokale `.env.example`, `dev`-/`prod`-Umgebungsvariablen, keine Secrets im Browser-Build.
6. Firebase Hosting liefert Angular aus und rewritet `/api/**` an Cloud Run oder nutzt eine klar konfigurierte API-URL. CORS wird auf die bekannten Hosting-Domains begrenzt.
7. GitHub Actions baut, testet und deployt nach `dev`; `prod` benötigt eine Freigabe.

### Endpunkte

- `GET /api/v1/health/live`
- `GET /api/v1/health/ready`
- `GET /api/v1/version`

### Abnahme

- Der Browser erreicht API und Firestore im `dev`-Deployment.
- Fehler liefern Problem Details ohne Stack Trace, Tokens oder Request-Inhalte.
- `dev` kann keine `prod`-Daten, -Secrets oder -Service-Accounts verwenden.
- Build, Lint, Unit-Tests, OpenAPI-Generierung und Firestore-Integrationstests laufen in CI.

## Phase 2: Owner-Identität und lokaler Zugriffsschutz

**Ziel:** Ein Owner meldet sich per Magic Link an und kann eine geschützte leere Kontoseite öffnen.

### Arbeitsschritte

1. Supabase-Magic-Link für Owner im Angular-Client integrieren; Passwortfelder des Prototyps werden nicht übernommen.
2. Einen NestJS-JWT-Guard implementieren: JWKS, Signatur, `iss`, `aud`, `exp`, `nbf` und `sub` prüfen.
3. Beim ersten erfolgreichen Zugriff ein lokales `users`-Dokument mit `supabaseUserId`, normalisierter verifizierter E-Mail und Status anlegen.
4. `email-normalization-v1` als pure gemeinsame API-Funktion implementieren und Grenzfälle testen.
5. Vor jeder geschützten fachlichen Aktion `users.status` und die Ressourcenberechtigung aus Firestore prüfen.
6. Callback-Routen nicht cachebar ausliefern; Token und Code vor jeder Navigation aus URL und Browser-History entfernen.

### Endpunkte

- `GET /api/v1/me`

### Abnahme

- Ungültige, abgelaufene oder fremdprojektige JWTs werden abgelehnt.
- Ein JWT mit beliebiger E-Mail oder erfundener Rolle gewährt keinen Zugriff.
- Deaktivierte oder löschende lokale Benutzer verlieren beim nächsten Request Zugriff.
- Die E-Mail-Normalisierung deckt lokale Teilregeln, IDN-Domain und Längengrenzen mit Unit-Tests ab.
- Session-Refresh und Logout funktionieren.

## Phase 3: Tags und öffentliche QR-Auflösung

**Ziel:** Ein Owner erzeugt einen kostenlosen Tag, erhält seine QR-URL und ein Finder kann den öffentlichen Link auflösen, ohne Status oder Owner preiszugeben.

### Arbeitsschritte

1. `tags` mit `publicId`, `primaryOwnerUserId`, `status`, `label`, `creationKey` und `membershipSlotCount` modellieren.
2. Tag-Erstellung mit verpflichtendem UUID-v4-`Idempotency-Key` und dedupliziertem Ergebnis implementieren.
3. Eine QR-URL als `https://<domain>/f/<publicId>` erzeugen. Die Angular-Seite rendert daraus einen herunterladbaren QR-Code; keine externe QR-API verwenden.
4. Eine öffentliche Auflösung implementieren, die für unbekannte, inaktive, gesperrte und stillgelegte Tags dieselbe neutrale Antwort liefert.
5. Owner-Tagliste sowie Aktivieren/Deaktivieren implementieren. `RETIRED` bleibt endgültig.
6. Einfache Rate Limits in Firestore nach gehashtem IP-/Tag-Kontext für öffentliche Auflösung und Tag-Erzeugung einführen. Keine zusätzliche Cache- oder Rate-Limit-Infrastruktur.

### Endpunkte

- `POST /api/v1/tags`
- `GET /api/v1/tags`
- `GET /api/v1/tags/{tagId}`
- `PATCH /api/v1/tags/{tagId}/status`
- `GET /api/v1/public/tags/{publicId}`

### Abnahme

- Wiederholung derselben Erstellung liefert denselben Tag; anderer Key erzeugt einen neuen.
- Fremde Tag-IDs sind nicht lesbar oder veränderbar.
- Öffentliche Antworten unterscheiden ungültige Tagzustände nicht.
- Die QR-Nutzstrecke funktioniert mit Kamera oder kopierter URL auf Desktop und Mobilgerät.

## Phase 4: Backup Owner und direkte Einladungs-E-Mails

**Ziel:** Ein Primary Owner lädt bis zu drei Backup Owner ein und entfernt sie wieder.

### Arbeitsschritte

1. `invitations` und `memberships` modellieren; eine aktive Einladung oder Mitgliedschaft zählt gegen das gemeinsame Limit von drei. `tags.membershipSlotCount` hält nur diese Anzahl.
2. Einladung und Zähleränderung werden in einer Transaktion geschrieben, sodass alle parallelen Änderungen am selben Tag-Dokument konkurrieren. Danach SMTP im selben HTTP-Request aufrufen und den technischen Status am Einladungseintrag speichern.
3. Bei SMTP-Fehler: UI zeigt fehlgeschlagen, Primary Owner kann die konkrete Einladung manuell erneut senden. Bei Timeout: `UNKNOWN` und bewusster manueller Wiederholungsdialog.
4. Annahme prüft authentifizierten Benutzer, exakte normalisierte und verifizierte E-Mail, Ablauf und Einladungsstatus. Danach neue `membershipId` anlegen und E-Mail/Normalisierungsversion aus der Einladung entfernen.
5. Entfernen oder endgültiges Ablaufen gibt den Platz in derselben Transaktion wieder frei. Eine erneute Einladung erzeugt beim Akzeptieren eine neue `membershipId`.
6. Berechtigungshelfer als kleine Funktionen je Aktion schreiben, nicht als generisches RBAC-System.

### Endpunkte

- `POST /api/v1/tags/{tagId}/invitations`
- `POST /api/v1/invitations/{invitationId}/resend`
- `POST /api/v1/invitations/{invitationId}/accept`
- `DELETE /api/v1/memberships/{membershipId}`
- `GET /api/v1/tags/{tagId}/members`

### Abnahme

- Parallel erstellte Einladungen überschreiten das Limit nicht.
- Falsche oder nicht verifizierte E-Mail kann nicht annehmen.
- Entfernte Backup Owner verlieren Zugriff beim nächsten Request.
- Entfernen und erneutes Einladen öffnet keine alten Archive.
- Ein SMTP-Fehler ändert keine Einladung unbemerkt in `SENT`.

## Phase 5: Finder-Draft und geräteübergreifende Bestätigung

**Ziel:** Ein Finder erstellt ohne vorheriges Konto eine Fundmeldung, bestätigt seine E-Mail über Magic Link und kann den Ablauf auf einem zweiten Gerät abschliessen.

### Arbeitsschritte

1. Öffentliche Fundseite zu `/f/{publicId}` erstellen: initialer Text und E-Mail; das optionale Bild ergänzt Phase 9 an demselben Draft-Endpunkt.
2. `finderReportDrafts` mit `flowId`, HMAC des nicht erratbaren Statustokens, `tagId`, initialem Text, normalisierter E-Mail, Ablauf, Versandstatus und verschlüsseltem kurzlebigem PKCE-Verifier erzeugen. Das rohe Statustoken wird nur einmal an den Browser zurückgegeben.
3. Draft erstellen, Supabase-Magic-Link direkt im HTTP-Request versenden und UI-Status wie bei Einladungen ausgeben.
4. Das Callback-Dokument setzt `Referrer-Policy: no-referrer`, entfernt Code und `flowId` vor asynchronen Aufrufen und lässt den Backend-Exchange ausführen. Dieser liefert Access- und Refresh-Token genau einmal in einer nicht cachebaren JSON-Antwort; Angular setzt sie mit `supabase.auth.setSession()` und verwirft die Response-Daten danach. Es werden keine Auth-Cookies eingeführt.
5. Das Backend akzeptiert Completion nur für ein gültiges JWT mit exakt passender normalisierter verifizierter E-Mail.
6. Draft-Status ist abrufbar, enthält aber nie Nachricht, E-Mail oder Chatinhalt. Abgelaufene Drafts werden beim Zugriff und im täglichen Wartungslauf logisch entfernt.

### Endpunkte

- `POST /api/v1/public/tags/{publicId}/finder-drafts`
- `POST /api/v1/public/finder-drafts/{flowId}/send-link` mit `X-Draft-Token`
- `GET /api/v1/public/finder-drafts/{flowId}/status` mit `X-Draft-Token`
- `POST /api/v1/auth/finder-callback`
- `POST /api/v1/finder-drafts/{flowId}/complete`

### Abnahme

- Same-Device- und Cross-Device-Ablauf funktionieren.
- Sessionübergabe, Refresh, Logout und Replay-Ablehnung funktionieren.
- `flowId` oder Statustoken geben keinen Zugriff auf sensible Inhalte.
- Nur `flowId` plus korrektes Statustoken dürfen Status und erneuten Linkversand auslösen; Completion verlangt unabhängig davon die passende verifizierte Identität.
- Abgelaufene, verbrauchte und falsch zugeordnete Links schlagen sicher fehl.
- Der Finder muss beim ersten Beitrag nicht leerem Text übermitteln.

## Phase 6: Verlustfall, private Chats und gesperrte Owner-Übersicht

**Ziel:** Die bestätigte Meldung erzeugt atomar einen Verlustfall und privaten Finder-Chat; Owner sehen vor Zahlung nur Anzahl und Zeitpunkte.

### Arbeitsschritte

1. `lossCases` mit `tagId`, `status`, `activeKey`, Fristen, `accessGrantedAt`, `chatAccessUntil`, archiviertem Grund und berechtigungsrelevanten Membership-Snapshots modellieren.
2. `finderChats` sowie `messages` mit `finderChatId`, `senderKind`, Text, Zeit und optionalem `imageId` modellieren und die Initialnachricht im selben Firestore-Transaktionsablauf erzeugen. Die Finder-Identität ist die bestätigte Supabase-Identität, nicht die E-Mail als Zugriffsschlüssel.
3. Bei gleichzeitigen Completes: aktiven Fall finden oder erzeugen, anschliessend Chat je Finder finden oder erzeugen. Der Ablauf muss ohne doppelte Initialnachricht wiederholbar sein.
4. Eine direkte Owner-Benachrichtigung per SMTP senden; Fehlstatus bleibt am Fall sichtbar und kann manuell erneut ausgelöst werden.
5. Owner-Übersicht zeigt nur Fallstatus, Anzahl Finder-Chats und Zeitpunkte, nie Text, Bild, E-Mail oder Kontaktinformationen.
6. Finder sieht nur den eigenen Chat und kann ihn bis zum Grant zurückziehen.

### Endpunkte

- `GET /api/v1/cases`
- `GET /api/v1/cases/{caseId}` als gesperrte Zusammenfassung vor Grant
- `GET /api/v1/finder/chats/{chatId}`
- `POST /api/v1/finder/chats/{chatId}/withdraw`
- `POST /api/v1/cases/{caseId}/notifications/retry`

### Abnahme

- Zwei parallele Finder: genau ein aktiver Fall und zwei getrennte Chats.
- Wiederholung desselben Finder-Completes erzeugt keinen zweiten Chat, keine Initialnachricht und keine interne Benachrichtigung.
- Kein Owner-Endpunkt leakt vor Grant Chatinhalt.
- Entfernter Backup Owner kann den Fall weder sehen noch beeinflussen.

## Phase 7: Owner-Entscheidung und Textchat nach Freischaltung

**Ziel:** Berechtigte Owner bestätigen oder verwerfen den Fall; nach späterem Grant ist Textchat möglich.

### Arbeitsschritte

1. Owner-Entscheidung `REPORTED -> AWAITING_PAYMENT` sowie `REPORTED -> ARCHIVED(NOT_LOST)` als idempotente Zustandsübergänge implementieren.
2. Nur Primary Owner und aktive Backup Owner mit operativen Rechten dürfen entscheiden; Berechtigungen sind pro Aktion explizit festgelegt.
3. Nachrichtenendpunkt prüft bei jedem Request Chatbesitz/Mitgliedschaft, Tagstatus, Fallstatus, Access Grant und Deadline. Vor Grant sind Owner-Inhalte komplett blockiert.
4. Cursor-Pagination nutzt `(createdAt, id)` als stabilen Sortierschlüssel.
5. Ein berechtigter Chatteilnehmer kann eine für ihn sichtbare Nachricht einmal als missbräuchlich melden; dadurch entsteht ein offener `abuseReports`-Eintrag für Phase 10.
6. Ein berechtigter Owner kann einen offenen Fall manuell abschliessen; alle Chats werden dadurch schreibgeschützt.
7. Die Angular-Ansichten zeigen leere, gesperrte, offene und archivierte Zustände ohne eigene komplexe Widget-Bibliothek.

### Endpunkte

- `POST /api/v1/cases/{caseId}/confirm-loss`
- `POST /api/v1/cases/{caseId}/reject-loss`
- `GET /api/v1/cases/{caseId}/chats`
- `GET /api/v1/chats/{chatId}/messages`
- `POST /api/v1/chats/{chatId}/messages`
- `POST /api/v1/messages/{messageId}/abuse-reports`
- `POST /api/v1/cases/{caseId}/complete`

### Abnahme

- Gleichzeitige Owner-Entscheidungen führen zu genau einem legalen Übergang.
- Leere oder über 2'000 Unicode-Zeichen lange Texte werden abgelehnt.
- Nach Ablauf oder Archivierung sind alle Chats schreibgeschützt.
- Doppelte Inhaltsmeldungen desselben Benutzers erzeugen keinen zweiten offenen Supportvorgang.
- Tastatur- und Screenreader-Tests decken Chatliste, Chatverlauf und Composer auf Deutsch und Englisch ab.

## Phase 8: Mollie Checkout und Access Grant

**Ziel:** CHF 10.00 öffnen Chats erst nach direkt bei Mollie geprüfter Zahlung.

### Arbeitsschritte

1. `payments` mit `lossCaseId`, logischem Checkout-Key, erwarteten CHF 10.00, Mollie-Metadaten, Provider-ID, Status und Refundstatus modellieren.
2. Checkout lokal reservieren, Mollie ausserhalb der Transaktion aufrufen und Ergebnis idempotent speichern. Bei verlorener Create-Antwort wird zuerst derselbe Provideraufruf innerhalb seiner Idempotenzfrist wiederholt und danach über interne Metadata und ein begrenztes Erstellungszeitfenster in der Mollie-Liste gesucht. Eine Checkout-URL wird nicht als dauerhafte Wahrheit behandelt.
3. Redirect-Seite zeigt nie Erfolg als Zahlungsbeweis, sondern fragt einen autorisierten Statusendpunkt ab.
4. Mollie-Webhook akzeptiert nur verifizierbare Providerereignisse; das Backend ruft Mollie zusätzlich direkt ab.
5. Statusaktualisierung per Owner-Button und täglicher Wartungslauf gleichen unklare Zahlungen ab.
6. Nur ein direkt bestätigtes Live-`paid` mit korrektem Betrag, Währung, Fall und Ausgangszustand setzt atomar Grant-Felder und `CHAT_OPEN`.

### Endpunkte

- `POST /api/v1/cases/{caseId}/checkouts`
- `GET /api/v1/payments/{paymentId}`
- `POST /api/v1/payments/{paymentId}/refresh`
- `POST /api/v1/webhooks/mollie`

### Abnahme

- Testmodus, falscher Betrag, falsche Währung, fremder Fall, doppelter Provider-ID und Redirect ohne `paid` öffnen keinen Chat.
- Webhook-Duplikate und parallele Statusaktualisierungen erzeugen genau einen Grant.
- Eine bei Mollie angelegte Zahlung mit verlorener Create-Antwort wird ohne zweite logische Zahlung wiedergefunden.
- Externe Mollie-Aufrufe finden nie innerhalb einer Firestore-Transaktion statt.
- Checkout-URLs, Zahlungsinstrumente und Webhook-Payloads erscheinen nicht in Logs.

## Phase 9: Optionale bereinigte Bilder in Firestore

**Ziel:** Autorisierte Chatteilnehmer können sichere Bilder austauschen, ohne einen weiteren Storage-Dienst einzuführen.

### Arbeitsschritte

1. `multipart/form-data` am bestehenden Nachrichtenendpunkt und am Finder-Draft-Endpunkt akzeptieren; JSON bleibt für text-only Nachrichten.
2. Vor dem vollständigen Decodieren maximal 5'000'000 Eingabebytes, erlaubte Formate und Bilddimensionen bis 2048 x 2048 prüfen.
3. Bildverarbeitung auf klare Zeit-, Speicher- und Parallelitätsgrenzen beschränken, Metadaten und Animationen entfernen, in ein festes Ausgabeformat re-encodieren und auch das Ergebnis auf höchstens 5'000'000 Bytes begrenzen. Damit bleibt das vollständige BSON-Dokument unter der 16-MiB-Grenze.
4. Bereinigte Bytes und `mediaType`, `width`, `height`, `byteLength` in einem eigenen `messageImages`-Dokument speichern. Während des Finder-Flows referenziert es den Draft; bei Completion werden Bild und Initialnachricht atomar verbunden. Bei normalen Chatnachrichten entstehen Bilddokument und Nachrichtenreferenz in derselben Transaktion.
5. `GET /api/v1/images/{imageId}` prüft dieselbe Chatberechtigung, streamt Bytes und setzt den gespeicherten `Content-Type`, `Content-Disposition: inline` und `X-Content-Type-Options: nosniff`.
6. UI verlangt bei informativen Bildern einen beschreibenden Text; die erste Nachricht bleibt immer textpflichtig.
7. Beim Ablauf oder Abbruch eines Finder-Drafts wird sein noch nicht einer Nachricht zugeordnetes Bild zusammen mit dem Draft-Inhalt gelöscht.

### Endpunkte

- `POST /api/v1/chats/{chatId}/messages` mit `multipart/form-data`
- `POST /api/v1/public/tags/{publicId}/finder-drafts` mit `multipart/form-data`
- `GET /api/v1/images/{imageId}`

### Abnahme

- Bilddaten erscheinen nie in Nachrichtenlisten oder Logs.
- Übergrösse, unlesbare, animierte, nicht unterstützte und ressourcenintensive Eingaben werden sicher abgelehnt.
- ID-Manipulation kann kein fremdes Bild ausliefern.
- Bereinigte Bilder enthalten keine EXIF-Metadaten oder Animationen.
- Die initiale Fundmeldung kann ein optionales Bild enthalten; ein Transaktionsfehler hinterlässt kein verwaistes Bild.

## Phase 10: Abschluss, Refunds, Wartung und minimale Supportoberfläche

**Ziel:** Zeitabläufe, Ausnahmen und notwendige Supportaktionen sind bedienbar, ohne eine breite Admin-Anwendung zu bauen.

### Arbeitsschritte

1. Vor jeder Fall- oder Chatoperation die Deadline prüfen und nötigenfalls synchron archivieren.
2. Einen dedizierten Scheduler-Service-Account und genau einen täglichen Cloud-Scheduler-Job anlegen; erwartete Audience und Service-Account-E-Mail sind validierte API-Konfiguration.
3. Den täglichen Wartungsendpunkt implementieren: Ein eigener Guard prüft Google-OIDC-Signatur, Issuer, Audience und die exakte Scheduler-Service-Account-E-Mail; danach werden abgelaufene Drafts/Fälle/Grants, unklare Zahlungen und fällige Löschungen in kleinen Batches behandelt.
4. `CLOSING` bei unklarem Zahlungsausgang einführen. Spätes `paid` in diesem Zustand wird protokolliert und vollständig zurückerstattet, ohne Grant.
5. Refund des grant-erzeugenden Payments archiviert den Fall. Refunds anderer Payments und Chargebacks folgen den dokumentierten Regeln.
6. Die minimale Support-UI enthält nur:

   - Arbeitsliste für offene Inhaltsmeldungen und fehlgeschlagene Payments/Refunds;
   - Vorgangsdetail mit der konkret gemeldeten Nachricht, Status, Zeitpunkten und pseudonymen IDs;
   - Sperren, Entsperren, Refund auslösen und Vorgang abschliessen.

7. Jede Supportaktion verlangt Grund und Ticketreferenz, prüft einen explizit konfigurierten Supportbenutzer und schreibt `auditLog`.
8. Keine Volltextsuche, keine freie Chatnavigation, keine Impersonation, keine direkte Dokumentbearbeitung.

### Endpunkte

- `POST /api/v1/internal/maintenance/daily`
- `GET /api/v1/support/work-items`
- `GET /api/v1/support/work-items/{workItemId}`
- `POST /api/v1/support/tags/{tagId}/block`
- `POST /api/v1/support/tags/{tagId}/unblock`
- `POST /api/v1/support/payments/{paymentId}/refund`
- `POST /api/v1/support/cases/{caseId}/close`

### Abnahme

- Nur der Scheduler-Service-Account darf den Wartungsendpunkt ausführen.
- Fehlendes, abgelaufenes oder für eine andere Audience beziehungsweise Identität ausgestelltes OIDC-Token wird abgelehnt.
- Wiederholter Wartungslauf ist sicher und führt zu keinem doppelten Refund oder Archivverlauf.
- Support kann keine beliebigen Chats lesen; nur die konkret gemeldete Nachricht ist erreichbar.
- Jede Supporteinsicht und -aktion enthält Akteur, Zeit, Grund und Ticketreferenz im Audit.
- Blockieren sperrt Endnutzerzugriff, aber reaktiviert nach Entsperren keinen archivierten Fall.

## Phase 11: Account-Löschung, Aufbewahrung und Release

**Ziel:** Ein Benutzer kann sein Konto löschen; das System ist für den kontrollierten Produktionsstart prüfbar.

### Arbeitsschritte

1. Löschanforderung legt zuerst dauerhaft ein `deletionLedger` mit Status und nächstem Schritt an und setzt danach den lokalen Sperrstatus, damit gültige Sessions bei der nächsten API-Anfrage keinen Zugriff mehr haben.
2. Der auslösende HTTP-Request führt die Kaskade idempotent aus: eigene aktive Fälle schliessen, eigene Tags `RETIRED`, fremde Backup-Mitgliedschaften entfernen und deren `membershipSlotCount` transaktional reduzieren, Identitätsreferenzen in Tags, Memberships, Fall-Snapshots, Chats, Nachrichten und Drafts pseudonymisieren, E-Mail-Kopien entfernen, zuletzt Supabase-Identität und lokales Konto löschen.
3. Nach jedem erfolgreichen Schritt wird das Ledger fortgeschrieben. Bei Teilfehler kann Support denselben Vorgang mit auditierter Aktion fortsetzen. Der tägliche Lauf behandelt nur fällige Datenlöschungen, nicht reguläre Benutzerlöschungen.
4. Konfigurierbare Retentionsfristen für Archive, Nachrichten, Bilder und Zahlungsnachweise implementieren, sobald rechtlich freigegeben.
5. Backups, Restore und anschliessende Anwendung des Löschledgers in einer isolierten Umgebung üben.
6. Monitoring und Runbooks nur für reale Betriebsfälle erstellen: API-/Webhook-Fehler, Mollie-Reconciliation, SMTP-`UNKNOWN`, fehlgeschlagener Refund, Wartungslauf, Account-Löschung und Restore.
7. Vollständigen E2E- und Accessibility-Abnahmelauf durchführen.

### Endpunkte

- `DELETE /api/v1/me`
- `POST /api/v1/support/deletions/{deletionId}/resume`

### Abnahme

- Zugriff endet sofort fachlich, auch bei noch gültigem Access Token.
- Wiederholung an jedem Abbruchpunkt konvergiert auf denselben Löschendzustand.
- `RETIRED`-Tags und öffentliche IDs werden nie wiederverwendet.
- Restore stellt gelöschte Daten nicht wieder fachlich zugreifbar bereit.
- Der vollständige Ablauf Tag, zwei Finder, Zahlung, getrennte Chats, Bild, Abschluss, Supportfall und Löschung ist automatisiert testbar.

## Tägliche Wartung im Detail

Der Scheduler ruft einmal täglich um 01:00 Uhr `Europe/Zurich` den intern verwendeten Cloud-Run-Endpunkt mit OIDC auf. Der Anwendungs-Guard schützt diesen einzelnen Pfad. Der Endpunkt verarbeitet immer nur eine feste kleine Anzahl pro Kategorie und liefert eine zusammengefasste, nicht sensitive Zählung zurück. Für das erwartete Phase-1-Volumen genügt ein Batch; eine aufwendigere Abarbeitung wird erst bei messbarem Rückstand eingeführt.

| Kategorie | Bedingung | Aktion |
| --- | --- | --- |
| Draft | abgelaufen oder abgeschlossen | Inhalt und nicht übernommenes Draft-Bild entfernen, minimalen Tombstone belassen |
| Verlustfall | Owner- oder Zahlungsfrist überschritten | archivieren; bei unklarem Payment zuerst `CLOSING` |
| Chatgrant | `chatAccessUntil` überschritten | Fall archivieren und Chats schreibschützen |
| Payment | lokaler Status unklar | bekannte Provider-ID direkt abfragen oder unbekannte ID über Metadata und begrenztes Zeitfenster suchen |
| Aufbewahrung | konfigurierte Frist überschritten | betroffene Daten und Bilddokumente löschen, Ledger aktualisieren |

Der Wartungslauf ist eine Ergänzung. Jede fachliche Anfrage erzwingt ihre eigenen Fristen sofort und darf keinen abgelaufenen Zustand lesbar oder beschreibbar machen.

## Durchgehende Qualitätsprüfung

Bei jeder Phase werden mindestens diese Prüfungen erweitert:

- Rechteprüfung mit fremder Ressourcen-ID, entfernter Membership und gültigem, aber unberechtigtem JWT;
- Idempotenztest für jeden schreibenden Endpunkt mit identischem Schlüssel oder identischer Fachoperation;
- Test für doppelte und falsch geordnete externe Providerantworten, sobald der Provider beteiligt ist;
- Test für verlorene Mollie-Create-Antwort und Wiederfinden ohne bekannte Provider-ID;
- Test des Scheduler-Guards mit fehlendem, falschem und gültigem OIDC-Token;
- Log-Test oder strukturierte Log-Inspektion gegen Tokens, E-Mails, Chattexte, Bilder und Checkout-URLs;
- deutscher und englischer UI-Test mit Tastatur; bei komplexen Ansichten zusätzlich VoiceOver auf macOS;
- `git diff --check`, Build, Lint und Tests vor jedem Merge.

## Reihenfolge und Parallelisierung

Die Phasen 1 bis 3 sind strikt nacheinander. Nach Phase 3 können Phase 4 und die UI-Grundlage für Phase 5 parallel vorbereitet werden, die produktive Finder-Completion hängt aber von Phase 5 ab. Phase 9 kann erst nach Phase 7 in die Hauptanwendung, weil die Chatberechtigung bereits vollständig erzwungen sein muss. Phase 10 benötigt Payment, Phase 11 alle vorherigen Phasen.

```text
0 Risiken beweisen
└─ 1 Durchstich
   └─ 2 Identität
      └─ 3 Tags/QR
         ├─ 4 Backup Owner
         └─ 5 Finder-Draft
            └─ 6 Fall/Chats
               └─ 7 Entscheidung/Textchat
                  └─ 8 Payment/Grant
                     └─ 9 Bilder
                        └─ 10 Wartung/Support
                           └─ 11 Löschung/Release
```

## Produktionsgates

Vor dem jeweiligen Produktionsdeployment muss bestätigt sein:

| Vor Phase | Erforderliche Entscheidung |
| --- | --- |
| 4 | SMTP-Provider, Datenstandort, Absenderdomain und Umgang mit Bounces |
| 8 | Mollie-Vertrag, CHF-Preis, erlaubte Live-Zahlungsarten sowie Beleg-/Steuerpflichten |
| 10 | Support-Arbeitsanweisung und Vier-Augen-Pflicht für Sperren und Refunds |
| 11 | Aufbewahrungsfristen, Rechtsgrundlage, Lösch- und Restore-Verfahren |
| Produktivstart | Domains, Anbieterangaben, Budgets, Warnschwellen und Go-live-Rechtsprüfung |
