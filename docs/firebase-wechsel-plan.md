# Umsetzungsplan: Firebase Authentication einführen und Supabase vollständig entfernen

Stand: 24. Oktober 2026 · Projekt: Lost Key Finder

## Ziel und verbindliche Rahmenbedingungen

Die Anwendung verwendet ausschliesslich Firebase Authentication für Benutzeridentitäten und Anmeldungen. Supabase wird aus Anwendung, aktiver Dokumentation, Konfiguration, CI/CD und Betrieb entfernt; anschliessend werden die ausschliesslich für dieses Projekt verwendeten Supabase-Ressourcen stillgelegt.

- Es gibt keine Supabase-Konten. Benutzerimport, Kontenzuordnung und Übergangsbetrieb mit zwei Auth-Anbietern entfallen.
- Die Verarbeitung der Authentifizierungsdaten in den USA ist akzeptiert.
- Angular, Kotlin/Spring Boot auf Cloud Run und Firestore Enterprise mit MongoDB-Kompatibilität bleiben die Grundlage.
- Dev und Prod erhalten getrennte Firebase-Projekte und Konfigurationen.
- Mollie und der Mailanbieter für fachliche E-Mails bleiben bestehen. Firebase Authentication ersetzt keine Zahlungsabwicklung oder allgemeinen Benachrichtigungsversand.
- Dieser Plan beschreibt die nächsten Arbeiten. Die Erstellung dieser Datei führt weder Deployments noch Ressourcenlöschungen aus.

## Ausgangslage

Im lokalen Arbeitsstand wurden Firebase-SDK, E-Mail-Link-Anmeldung, Callback, ID-Token-Übergabe, Backend-Verifikation, `firebaseUserId`, Konfigurationsgenerator und Deployment-Workflow bereits vorbereitet. Der Supabase-Identity-Adapter wurde durch einen Firebase-Adapter ersetzt.

Zuletzt bestanden 30 Frontend- und 33 Backend-Tests sowie Lint, Backend-Coverage-Prüfung und beide Produktionsbuilds. Der Angular-Produktionsbuild lief wegen eines lokalen nativen Cache-Absturzes erfolgreich mit `CI=true` und deaktiviertem lokalen Cache. Diese Ergebnisse ersetzen keine Abnahme gegen die echten Firebase-Projekte.

Cloud-Konfigurationen, Datenbankindizes und externe Ressourcen wurden noch nicht vollständig inventarisiert oder geändert. Mehrere aktive Planungsdokumente enthalten weiterhin alte Supabase- und PKCE-Vorgaben; ein vorangestellter Hinweis allein genügt für den Abschluss nicht.

## 1. Bestand aufnehmen und offene Auth-Regeln festlegen

- [ ] Den aktuellen Git-Diff prüfen und den bereits vorbereiteten Stand als Grundlage verwenden.
- [ ] Für Dev und Prod Projekt-IDs, Web-App-IDs, Hosting-Domains, Cloud-Run-Service, Firestore-Datenbank und zuständige Service Accounts erfassen.
- [ ] Supabase-Projekt-IDs und ausschliesslich diesem Projekt zugeordnete Ressourcen erfassen: Auth, Datenbank, Storage, Functions, Webhooks, OAuth-Anbindungen, SMTP, Domains und Abrechnung. Fehlende Konten bedeuten nicht automatisch, dass alle anderen Ressourcen leer sind.
- [ ] Vorhandene lokale Testdatensätze und Indizes in Firestore prüfen. Keine Kontenmigration einplanen; etwaige Testreste gezielt behandeln.
- [ ] Alte Fristen explizit auf Firebase abbilden: Die bisher geplanten vier Stunden für Magic Links und 30 Tage für Sitzungen dürfen nicht ungeprüft als Firebase-Verhalten dokumentiert werden.
- [ ] Entscheiden und dokumentieren, ob eine feste maximale Sitzungsdauer benötigt wird. Falls 30 Tage verbindlich bleiben, serverseitig anhand einer verifizierten Anmeldezeit durchsetzen; ansonsten Firebase-Sitzungsverhalten übernehmen.
- [ ] Verantwortlichkeit für Supportbenutzer und deren Berechtigungsvergabe auf Firebase-UIDs festlegen. Eine Authentifizierung allein verleiht keine Supportrechte.

**Ergebnis:** Eine eindeutige Konfiguration je Umgebung und dokumentierte Regeln für Link-, Session- und Draft-Laufzeiten.

## 2. Firebase-Projekte und E-Mail-Anmeldung konfigurieren

Zuerst Dev vorbereiten; die gleiche geprüfte Konfiguration danach getrennt für Prod durchführen.

- [ ] Firebase Authentication im jeweiligen bestehenden Google-/Firebase-Projekt aktivieren und eine Web-App registrieren beziehungsweise die vorhandene verwenden.
- [ ] Den Anbieter E-Mail/Passwort und zusätzlich E-Mail-Link-Anmeldung aktivieren. Die Anwendung bietet weiterhin ausschliesslich den vorgesehenen passwortlosen Ablauf an.
- [ ] Erlaubte Domains auf die tatsächlich verwendeten Hosting-/Custom-Domains begrenzen. `localhost` nur für Dev hinzufügen.
- [ ] Absender, Supportadresse, Sprache und Anmelde-Mailvorlage prüfen; Zustellung und Spam-Einstufung mit echten Postfächern testen.
- [ ] Blaze-Abrechnung und passende Budgetbenachrichtigungen konfigurieren. Budgetbenachrichtigungen sind kein hartes Ausgabenlimit.
- [ ] Versandquoten prüfen: Laut aktueller Dokumentation gelten für Firebase-Anmeldemails 5 pro Tag auf Spark und 25’000 auf Blaze. Vor Freischaltung die dann aktuellen Werte erneut prüfen.
- [ ] API-Key-Einschränkungen so setzen, dass die benötigten Firebase-Auth-Aufrufe erlaubt sind. Der aktuelle Backend-Adapter verwendet ebenfalls `accounts:lookup`; reine Browser-Referrer-Einschränkungen dürfen diesen Aufruf nicht blockieren. Falls getrennte Keys benötigt werden, deren Konfiguration ausdrücklich trennen.

**Ergebnis:** Firebase kann in jeder Umgebung Links an erlaubte Callback-Domains versenden und Benutzer authentifizieren.

## 3. Anwendungskonfiguration und CI/CD fertigstellen

| Wert | Lokale Entwicklung | GitHub Environment / Laufzeit |
| --- | --- | --- |
| `FIREBASE_PROJECT_ID` | `.env.dev.local` | Im bestehenden Deploy-Workflow aus `GCP_PROJECT_ID`; an Cloud Run weitergeben |
| `FIREBASE_API_KEY` | `.env.dev.local` | Je Umgebung setzen; Browser und aktueller Backend-Adapter benötigen einen passenden Key |
| `FIREBASE_AUTH_DOMAIN` | `.env.dev.local` | Je Umgebung für den Web-Build setzen |
| `FIREBASE_APP_ID` | `.env.dev.local` | Je Umgebung für den Web-Build setzen |

- [ ] `.env.example`, lokale Startbefehle und Build-Anleitung auf Vollständigkeit prüfen.
- [ ] Den Generator `apps/web/scripts/write-auth-config.mjs` mit vollständiger, leerer und unvollständiger Konfiguration prüfen. Teilkonfiguration muss verständlich scheitern.
- [ ] Der Deploy-Workflow muss alle vier erforderlichen Web-Werte vor dem Build validieren; Backend und Frontend müssen dasselbe Firebase-Projekt verwenden.
- [ ] `auth-config.json` ausschliesslich mit öffentlicher Webkonfiguration erzeugen. Service-Account-Schlüssel und administrative Zugangsdaten dürfen dort nie erscheinen.
- [ ] Fehlende Auth-Konfiguration darf öffentliche Seiten weiter zulassen, aber geschützten Zugriff nicht ermöglichen.
- [ ] Cloud-Run-Konfiguration, Hosting-Rewrites, CORS und Callback-Header für beide Umgebungen prüfen. Callback und Auth-Konfiguration dürfen nicht zwischengespeichert werden; Callback setzt `Referrer-Policy: no-referrer`.
- [ ] Vorhandene Provider-Verifikation um eine dokumentierte Firebase-Live-Abnahme ergänzen; Offline-Tests weiterhin ohne echte Cloud-Zugangsdaten ausführen.

**Ergebnis:** Builds und Deployments sind reproduzierbar und enthalten keine unbeabsichtigte Dev-/Prod-Vermischung.

## 4. Auth-Code prüfen und Supabase-Code vollständig entfernen

### Angular

- [ ] Ausschliesslich Firebase-SDK für Versand, Einlösung, Sitzungswiederherstellung, Token-Refresh und Logout verwenden.
- [ ] Callback bereinigt Query und Fragment synchron vor Konfigurationsabruf und SDK-Initialisierung; nur erforderliche Linkfelder bleiben vorübergehend im Speicher.
- [ ] E-Mail für denselben Browser lokal speichern und nach erfolgreicher Einlösung entfernen; niemals in der Callback-URL übertragen.
- [ ] Auf einem zweiten Gerät die E-Mail erneut abfragen. Fehler, abgelaufene Links, Wiederverwendung und Neuladen des bereinigten Callbacks verständlich behandeln.
- [ ] Für API-Aufrufe jeweils aktuelle ID-Tokens verwenden und ausschliesslich an erlaubte API-Ziele senden.
- [ ] Alte Supabase-Storage-Einträge und gegebenenfalls Service-Worker-/Browser-Caches gezielt bereinigen, sofern tatsächlich vorhanden. Kein pauschales `localStorage.clear()`, das andere Einstellungen löscht.

### Kotlin / Spring Boot

- [ ] RS256-Signatur, Google-JWKS, projektbezogenen Issuer, Audience, Ablauf, Ausstellungszeit, Anmeldezeit und gültige Firebase-UID prüfen.
- [ ] Firebase-UIDs als Strings bis 128 Zeichen behandeln; nur lokale Konto-IDs bleiben UUIDs.
- [ ] Beim erstmaligen Provisionieren UID und bestätigte E-Mail über den Firebase-Adapter prüfen; bei Providerfehlern kein Konto anlegen und keine internen Details ausgeben.
- [ ] Fachliche Berechtigungen und lokaler Status werden bei jedem Zugriff geprüft. E-Mail und frei gesetzte Claims führen nicht automatisch zu Kontozusammenführung oder Berechtigungen.
- [ ] Den Umgang mit Firebase-Sperrung, Löschung und Token-Widerruf festlegen. Reine JWT-Signaturprüfung erkennt Widerruf nicht sofort; erforderlichenfalls zusätzliche Status-/Widerrufsprüfung einbauen.
- [ ] Supabase-Adapter, Imports, Endpunkte, Konfigurationsklassen, alte Test-Fixtures und ungenutzte spezifische Hilfsfunktionen entfernen.
- [ ] `@supabase/supabase-js` und alle ausschliesslich dafür benötigten direkten oder transitiven Pakete aus Manifest und Lockfile entfernen. Allgemeine OAuth-/PKCE-Abhängigkeiten anderer Werkzeuge nicht blind löschen.

**Ergebnis:** Kein aktiver Anwendungspfad und kein Test benötigt Supabase.

## 5. Firestore-Indizes und Tests vorbereiten

- [ ] Eindeutigen Index auf `users.firebaseUserId` separat administrativ anlegen und dessen Bereitschaft prüfen. Runtime erzeugt keine Indizes.
- [ ] Zwei unterschiedliche Firebase-UIDs mit derselben E-Mail müssen getrennte lokale Identitäten bleiben.
- [ ] Ein eventuell bestehender eindeutiger Index auf `supabaseUserId` wird vor Freischaltung neuer Konten entfernt; fehlende Werte könnten sonst neue Kontoanlagen blockieren.
- [ ] Falls Testdokumente das alte Feld verwenden, deren Herkunft und weitere Referenzen prüfen und sie gezielt entfernen oder die Testdaten neu erstellen. Keine breit angelegte Löschung produktiver Collections.
- [ ] Den echten Firestore-Integrationstest für paralleles Provisionieren, Eindeutigkeit und lokale Kontosperren ausführen.

**Ergebnis:** Mehrere neue Firebase-Konten lassen sich anlegen; parallele Erstzugriffe erzeugen pro UID genau ein lokales Konto.

## 6. Geplante Fachabläufe vollständig auf Firebase ausrichten

Diese Anpassungen sind Voraussetzung für eine konsistente Planung. Noch nicht vorhandene Produktfunktionen werden dadurch nicht stillschweigend als fertig erklärt.

- [ ] Finder-Draft: Supabase-PKCE-Verifier und Backend-Session-Exchange aus dem Entwurf entfernen. Draft weiterhin mit Ablauf und eigenständigem, nicht erratbarem Statuszugang absichern.
- [ ] Finder-Completion: Firebase-Link im Client einlösen; das Backend akzeptiert den Draft nur mit gültiger Firebase-Identität und exakt passender normalisierter, bestätigter E-Mail.
- [ ] Entwurf für geräteübergreifende Draft-Zuordnung, Schutz vor falschem Draft, Replay und abgelaufener Meldung konkret festlegen und vor Implementierung prüfen.
- [ ] Backup-Einladungen: bestehende Prüfung auf passende bestätigte E-Mail und lokale Mitgliedschaft beibehalten; technische Identitätsreferenzen auf Firebase umstellen.
- [ ] Chats, Ownership, Support und Audit verwenden lokale fachliche IDs beziehungsweise die explizit zugeordnete Firebase-Identität.
- [ ] Account-Löschung: Supabase-Admin-Löschung durch Firebase-Admin-Löschung mit minimalen IAM-Rechten ersetzen. Lokalen Zugriff sofort entziehen; vorhandene wiederaufnehmbare Kaskade und Löschledger beibehalten.
- [ ] Allgemeine Benachrichtigungen und Einladungsmails bleiben beim vorgesehenen Mailanbieter. Gegebenenfalls bisher nur für Supabase eingerichtete SMTP-Zugänge später entfernen.

**Ergebnis:** Keine zukünftige Funktion setzt mehr Supabase voraus. Finder-Completion und Löschkaskade erhalten eigene Abnahmen, sobald sie implementiert werden.

## 7. Aktive Dokumentation vollständig bereinigen

Mindestens folgende Dateien inhaltlich überarbeiten, statt lediglich einen neuen Hinweis voranzustellen:

- [ ] `docs/architecture.md`: Firebase-Identität, Kotlin/Spring Boot als tatsächliches Backend, Tokenprüfung, Sessionregeln, Finder-Ablauf und neue Konfiguration beschreiben.
- [ ] `docs/implementation-plan.md`: alle Phasen, Voraussetzungen, Felder, Testfälle, Löschschritte und Auth-Endpunkte aktualisieren.
- [ ] `docs/roadmap.md`, `docs/key-tag-lifecycle.md`, `docs/privacy-and-compliance.md`: Anbieter, Identitätslebenszyklus, Löschung und akzeptierte US-Verarbeitung korrekt abbilden.
- [ ] `KOTLIN-FOUNDATION.md`, `TESTING-PLAN.md`, `FIRESTORE-SETUP.md`: aktuelle Implementierung, Testgrenzen und Indizes dokumentieren; unbestätigte Aussagen zu bereits angelegten Firebase-Indizes entfernen.
- [ ] `docs/phase-2-verification.md`: alten Nachweis aus der aktiven Dokumentation entfernen und durch eine echte Firebase-Abnahme ersetzen. Keine historischen Ergebnisse nachträglich als Firebase-Tests ausgeben.
- [ ] `docs/firebase-auth-migration.md`: nach Abschluss als Firebase-Einrichtungsanleitung neu benennen, Übergangshinweise entfernen und alle Verweise anpassen.
- [ ] Aktive Datenschutz-/Betriebsunterlagen auf Firebase/Google und den vorgesehenen Mailanbieter aktualisieren.

Historische Git-Commits bleiben erhalten. Vollständiges Entfernen bedeutet keine Abhängigkeit im aktuellen Repository oder Betrieb; die Git-Historie wird dafür nicht umgeschrieben.

## 8. Automatisierte Prüfung und Live-Abnahme in Dev

### Automatisch

- [ ] Saubere Installation mit `npm ci` durchführen und Dependency-Baum auf Supabase-Pakete prüfen.
- [ ] `npm run lint`, `npm run build`, `npm run test`, `npm run test:http` und `npm run generate:client` ausführen; generierte Änderungen prüfen.
- [ ] Falls der dokumentierte lokale Angular-Cache-Absturz auftritt, den Produktionsbuild mit `CI=true NG_BUILD_MAX_WORKERS=2 npm run build:web` prüfen; dafür keine fachlichen Änderungen einbauen.
- [ ] `npm run test:integration` mit explizit konfigurierter Dev-Firestore-Verbindung ausführen. Vorher erforderliche Indizes bereitstellen.
- [ ] Tests decken fremde Projekte, falsche Signaturen, fehlende/abgelaufene Claims, nicht bestätigte E-Mail, Providerfehler, Kontosperren und paralleles Provisionieren ab.

### Gegen echte Dienste

- [ ] Dev-API und Dev-Frontend gemeinsam deployen; Version und Umgebung verifizieren.
- [ ] Neues Konto, erneute Anmeldung und mehrere verschiedene Konten testen.
- [ ] Anmeldung im selben Browser und auf einem zweiten Gerät/Browser prüfen.
- [ ] Falsche E-Mail, ungültigen/abgelaufenen und bereits eingelösten Link testen.
- [ ] Persistenz nach Browserneustart, automatischen Token-Refresh sowie Logout über mehrere Tabs prüfen.
- [ ] Lokale Sperre und die vereinbarte Behandlung von Firebase-Sperrung/Widerruf testen.
- [ ] Fehlende Konfiguration, Netzwerkfehler und Quotenfehler sicher behandeln.
- [ ] Netzwerkverkehr, Callback-History, Referrer und Logs auf unbeabsichtigte Offenlegung von Tokens oder personenbezogenen Daten prüfen. Der notwendige direkte Auth-Verkehr zu Google ist dabei erwartbar.
- [ ] Sicherstellen, dass Anwendung und Backend keine Supabase-Domain mehr kontaktieren.

**Freigabekriterium:** Reproduzierbarer erfolgreicher Login bis `/api/v1/me`, funktionierender Logout und sichere Ablehnung unberechtigter Zugriffe; Ergebnisse mit Commit und Umgebung dokumentiert.

## 9. Prod ausrollen

- [ ] Dev-Abnahme abschliessen und denselben geprüften Stand für Prod verwenden.
- [ ] Prod-Auth, Mailvorlagen, Domains, Projektwerte, Abrechnung und Indizes separat prüfen.
- [ ] API und Frontend mit zueinander passender Konfiguration deployen; Gesundheitszustand und Version über Hosting prüfen.
- [ ] Echten Login, Kontoanlage und Logout in Prod testen; Testkonto danach geordnet entfernen.
- [ ] Fehlerrate, Auth-Fehler, Mailversand und Kosten nach dem Rollout kontrollieren.
- [ ] Rückfallweg als erneutes Deployment einer geprüften Firebase-Version beziehungsweise vorübergehende Deaktivierung der Anmeldung festhalten. Einen alten Supabase-Build nach der Stilllegung nicht als funktionierenden Rollback voraussetzen.

**Freigabekriterium:** Prod arbeitet eigenständig mit Firebase. Ab jetzt ist Supabase für den Betrieb entbehrlich.

## 10. Supabase aus Konfiguration und Infrastruktur entfernen

Erst nach erfolgreicher Firebase-Abnahme durchführen. Vor jeder externen Löschung die exakten Ressourcen und ihre exklusive Projektzuordnung prüfen.

- [ ] Lokale `.env`-/`.env.*.local`-Dateien und IDE-Run-Konfigurationen von sämtlichen `SUPABASE_*`-Werten bereinigen, ohne andere Werte auszugeben oder zu löschen.
- [ ] GitHub-Repository- und Environment-Variablen/Secrets für Supabase löschen; gegebenenfalls Organisationswerte nur entfernen, wenn kein anderes Projekt sie verwendet.
- [ ] Supabase-Werte und Secret-Referenzen aus Cloud Run, Secret Manager und weiteren Deployment-Systemen entfernen. Alte Revisionen mit Supabase-Abhängigkeit stilllegen, soweit nicht mehr benötigt.
- [ ] Ausschliesslich für Supabase verwendete SMTP-Zugänge, OAuth-Clients, Webhooks, Domains/DNS-Einträge, CI-Tokens und persönliche Automatisierungszugänge aufheben. Gemeinsam verwendete Zugänge erhalten.
- [ ] Tatsächlichen Supabase-Projektinhalt prüfen: auch Datenbank, Buckets, Functions und Integrationen berücksichtigen. Benötigte Nicht-Auth-Daten vor der Löschung sichern oder übernehmen, falls entgegen der Erwartung vorhanden.
- [ ] Ausschliesslich für Lost Key Finder verwendete Supabase-Projekte endgültig löschen; vor Ausführung die konkrete Löschung zur Freigabe vorlegen.
- [ ] Supabase-Abrechnung auf verbleibende Abonnements/Zusatzoptionen prüfen und projektspezifische kostenpflichtige Ressourcen beenden.
- [ ] Eine Supabase-Organisation oder das persönliche Supabase-Konto nur schliessen, wenn sie ausschliesslich diesem Projekt dienen und dies gesondert gewünscht ist.
- [ ] Verwaiste lokale Konfigurationen, Tokens und ausschliesslich hierfür angelegte Verbindungen entfernen. Keine fremden Projekte oder global benötigten Werkzeuge löschen.

**Ergebnis:** Keine aktive Ressource, kein gültiger projektspezifischer Zugang und keine laufende projektspezifische Supabase-Kostenposition bleiben übrig.

## 11. Abschlussprüfung und Definition of Done

- [ ] Repository-Suche nach `supabase`, `SUPABASE_`, alten Projekt-Hosts und `supabaseUserId` ergibt keine aktiven Abhängigkeiten, Felder oder Setup-Anweisungen mehr.
- [ ] Übergangsplan und Abschaltungsnachweis werden nach Abschluss aus den aktiven Betriebsanleitungen genommen; notwendige historische Nachweise bleiben ausschliesslich als solche erkennbar. Die Git-Historie darf alte Namen enthalten.
- [ ] Lockfile und Dependency-Baum enthalten keine `@supabase/*`-Pakete. Ein allgemeines Paket wie `pkce-challenge` ist nur dann zu entfernen, wenn es tatsächlich ausschliesslich Supabase diente; derzeitiger PKCE-Treffer im Lockfile ist kein Beleg für eine Supabase-Abhängigkeit.
- [ ] Lokale Konfiguration, GitHub, Cloud Run, Secret Manager und externe Integrationen enthalten keine aktiven projektspezifischen Supabase-Zugänge mehr.
- [ ] Dev und Prod bestehen die Firebase-Live-Abnahme und funktionieren nach Supabase-Abschaltung weiter.
- [ ] Neue Firebase-Konten können ohne alten Indexkonflikt angelegt werden.
- [ ] Aktive Architektur-, Datenschutz-, Test- und Betriebsdokumente beschreiben konsistent Firebase und Kotlin/Spring Boot.
- [ ] Noch nicht implementierte Finder-, Support- und Löschfunktionen sind vollständig auf Firebase geplant und als offen gekennzeichnet.
- [ ] Abschluss mit geprüftem Commit, Deployment-Versionen, Abnahmeergebnissen und bestätigter Ressourcenstilllegung dokumentieren. Keine Schlüssel, Tokens oder Test-E-Mail-Adressen in den Nachweis aufnehmen.

## Reihenfolge und Abhängigkeiten

1. Bestand und Regeln festlegen.
2. Dev-Firebase konfigurieren; Anwendung, CI/CD und Indizes abschliessen.
3. Fachplanung und aktive Dokumentation bereinigen.
4. Automatisierte Prüfungen und echte Dev-Abnahme durchführen.
5. Prod vorbereiten, ausrollen und abnehmen.
6. Alte Zugänge entfernen und konkrete Supabase-Projekte nach Freigabe löschen.
7. Abschlussprüfung durchführen.

Die endgültige Supabase-Projektlöschung erfolgt zuletzt. Für den Wechsel selbst ist keine Benutzer- oder Kontenmigration erforderlich.

## Offizielle Referenzen

- [Firebase: Anmeldung per E-Mail-Link](https://firebase.google.com/docs/auth/web/email-link-auth)
- [Firebase: ID-Token-Prüfung](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Firebase: Sitzungen und Token-Widerruf](https://firebase.google.com/docs/auth/admin/manage-sessions)
- [Firebase: Auth-Limits und E-Mail-Quoten](https://firebase.google.com/docs/auth/limits)
- [Firebase: Datenschutz und Verarbeitungsstandorte](https://firebase.google.com/support/privacy)
- [Google: accounts:lookup](https://cloud.google.com/identity-platform/docs/reference/rest/v1/accounts/lookup)
