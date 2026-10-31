# Backendgesteuerte Kontolöschung

Stand: 31. Oktober 2026. Ersetzt die browsergesteuerte Fortsetzung aus PR #16. Die Anwendung führt Löschungen unabhängig von Browser, Benutzeranmeldung und SMTP aus. Die fachlichen Regeln für Tags und erhaltene gemeinsame Inhalte bleiben gleich.

## Ablauf

1. Der Benutzer bestätigt die Löschung mit höchstens fünf Minuten alter Anmeldung. `DELETE /api/v1/me` prüft die bestätigte Firebase-Identität und speichert Auftrag und `users.status=DELETING` transaktional. Adminrechte entfallen sofort. Wiederholungen desselben Auftrags sind idempotent.
2. HTTP **202 Accepted** bestätigt ausschliesslich die dauerhafte Annahme. Es wird noch keine Datenbereinigung oder Firebase-Löschung im Benutzerrequest durchgeführt. Das Frontend öffnet eine öffentliche Bestätigungsseite und beendet die Sitzung. Keine Polling-/Fortsetzungsschleife, kein Abwarten der Löschung.
3. Cloud Scheduler ruft alle fünf Minuten `POST /api/v1/internal/account-deletions/dispatch` auf. Höchstens zehn fällige Aufträge werden pro Aufruf bearbeitet. Ein Durchlauf verarbeitet maximal 25 Tags sowie begrenzte Folgegruppen von Chats, Entwürfen und Versandständen; grössere Konten brauchen mehrere Durchläufe.
4. Eigene Tags werden stillgelegt, Hardware-Zuordnungen gelöst, betroffene Chats geschlossen. Gesperrte/stillgelegte Hardware wird nicht reaktiviert. Bestehende Historien und gemeinsame Nachrichten/Bilder bleiben mit pseudonymen Referenzen erhalten. Temporäre Daten werden bereinigt und das lokale Profil vor dem externen Abschluss pseudonymisiert.
5. Der Worker löscht die ursprüngliche Firebase-UID über die projektgebundene Admin-REST-API und Google Application Default Credentials (ADC). UID und Löschledger müssen zusammenpassen. Nur ein weiterhin `DELETING` gesetztes Konto wird verarbeitet. Danach werden das lokale Profil entfernt und der Auftrag abgeschlossen, gemeinsam in einer Transaktion.

Eine Anmeldung ist nach Annahme nicht mehr erforderlich. Ein wieder aufgerufener bestehender Auftrag wird erneut mit 202 bestätigt; dadurch entsteht keine zweite Löschung. Eine Neuanmeldung mit neuer Firebase-UID erhält keine alten Tag-/Chatrechte.

## Wiederholungen und Parallelität

`accountDeletions` bleibt das bestehende Löschledger: gehashte Firebase-UID als `_id`, lokale Benutzerreferenz, `createdAt`, `localCleaned`, `completedAt`. Hinzu kommen `nextAttemptAt`, `leaseUntil`, zufälliges `leaseToken`, `failures` und `lastFailureAt`. Weder UID-Klartext noch E-Mail, Benutzer-/Refresh-Token oder Providerantwort werden im Auftrag gespeichert. Die ursprüngliche UID bleibt bis zum endgültigen Abschluss ausschliesslich im gesperrten Benutzer-Dokument verfügbar.

Die Reservierung ist atomar und fünf Minuten gültig. Jeder schreibende Verarbeitungsschritt prüft und erneuert die aktuelle Reservierung innerhalb derselben Transaktion. Ein abgelaufener oder ersetzter Worker darf keine weiteren Daten ändern oder den Fortschritt seines Nachfolgers überschreiben. Ein rein lesender Check vor einer Schreibtransaktion würde hierfür nicht genügen.

Unvollständige reguläre Durchläufe sind nach einer Minute wieder fällig. Bei Fehlern wächst die Wartezeit von einer Minute bis höchstens einer Stunde. Der nächste Schedulerlauf entscheidet über den tatsächlichen Aufrufzeitpunkt. Ein Prozessabbruch lässt die Reservierung auslaufen. Bereits lokal abgeschlossene Schritte und Historien werden bei Wiederholung nicht doppelt ausgeführt. Der externe Firebase-Aufruf kann nach verlorener Antwort wiederholt werden; `USER_NOT_FOUND` gilt als bereits erfolgreich gelöscht. Eine atomare Transaktion über Firebase Auth und Firestore wird nicht behauptet.

Alte unvollständige Aufträge ohne Schedulingfelder bleiben auswählbar. Ein neuer Index `(completedAt, createdAt)` wird nur über den bestehenden Operatorbefehl `firestore:tag-indexes` angelegt. Laufzeit und Deployment legen keine Indizes an; kein Datenreset und keine Migration erforderlich.

## Technische Berechtigungen

Technischer Adminzugriff ist unabhängig von `users.isAdmin`. Der Runtime-Serviceaccount braucht genau `firebaseauth.users.delete` auf dem zugehörigen Projekt. Eine projektspezifische Custom Role reicht; Firebase Authentication Admin, Editor oder Owner sind dafür nicht erforderlich. Die API akzeptiert vom Frontend niemals eine Ziel-UID: Der Worker entnimmt sie ausschliesslich dem zuvor gesperrten eigenen Benutzerkonto und prüft ihren Hash gegen den Auftrag.

Standardbibliothek: `google-auth-library-oauth2-http`. In Cloud Run werden kurzlebige Credentials der angehängten Dienstidentität verwendet. Keine Service-Account-Schlüsseldatei und keine gespeicherten Benutzer-Tokens. ADC wird erst beim Workerzugriff geladen; lokale Entwicklung und Start funktionieren ohne konfigurierte Dienstcredentials. Ohne gültige Berechtigung scheitert der Worker sicher und plant eine Wiederholung; er markiert den Auftrag nicht als abgeschlossen.

Der Scheduler nutzt ein separates Servicekonto. Der interne Endpunkt prüft Google-Signatur, Issuer, Ablaufzeit, exakte Audience und bestätigte Servicekonto-E-Mail. Firebase-Benutzertokens und reine Anwendungs-Adminrechte berechtigen nicht zum Aufruf. Bei fehlender Jobkonfiguration gibt es HTTP 503; auch neue Benutzeraufträge werden dann vor Kontoanlage/Sperre abgelehnt. Das prüft die Konfiguration, nicht die tatsächliche Existenz des Cloud-Scheduler-Jobs oder seiner IAM-Berechtigungen; deren Einrichtung und Abnahme bleiben erforderlich. Der Google-OIDC-Prüfer wird auch für den Chat-Versand verwendet; Konfiguration und fachliche Verarbeitung bleiben getrennt.

Quellen: [Admin-Löschendpunkt und Berechtigung](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/projects.accounts/delete), [ADC](https://docs.cloud.google.com/docs/authentication/application-default-credentials), [Scheduler-Authentifizierung](https://docs.cloud.google.com/scheduler/docs/http-target-auth).

## Einrichtung je Umgebung

Die folgenden Operatoraktionen erweitern bewusst technische Berechtigungen und richten den Job ein. Sie werden nicht beim Anwendungsstart oder in Tests ausgeführt. Runtime-/Scheduler-Servicekonto, Region und die echte Cloud-Run-URL vor Ausführung prüfen. Dev und Prod getrennt konfigurieren. Codeänderungen allein richten keinen aktiven Scheduler oder IAM-Binding ein.

1. Neue Indizes mit Operatorrechten anlegen:

   ```sh
   npm run firestore:tag-indexes --workspace=@lost-key-finder/api
   ```

2. Die eng begrenzte Rolle erstellen und ausschliesslich dem Runtime-Servicekonto zuweisen; Variablen durch die Werte der betreffenden Umgebung setzen:

   ```sh
   gcloud iam roles create firebaseAccountDeleter \
     --project="$PROJECT_ID" --title="Firebase account deletion" \
     --permissions=firebaseauth.users.delete --stage=GA
   gcloud projects add-iam-policy-binding "$PROJECT_ID" \
     --member="serviceAccount:$RUNTIME_SERVICE_ACCOUNT" \
     --role="projects/$PROJECT_ID/roles/firebaseAccountDeleter" --condition=None
   ```

   Eine bereits vorhandene Custom Role vor Wiederverwendung auf genau diese Berechtigung prüfen. Keine fremden Rollen überschreiben.

3. Ein dediziertes Scheduler-Servicekonto erstellen, `roles/run.invoker` auf dem Zielservice zuweisen und in GitHub-Environment-Variablen hinterlegen:

   - `ACCOUNT_DELETION_JOB_AUDIENCE`: vollständige Cloud-Run-URL mit `/api/v1/internal/account-deletions/dispatch`.
   - `ACCOUNT_DELETION_JOB_SERVICE_ACCOUNT`: E-Mail des dedizierten Scheduler-Servicekontos.

   Die Deployment-Pipeline übergibt beide Werte. Neue Backend-/Frontend-Version gemeinsam deployen. Die Audience des Löschjobs von der Chat-Job-Audience unterscheiden.

4. Nach Deployment den Job anlegen. `DELETION_JOB_URL` ist dieselbe vollständige URL wie die konfigurierte Audience; `DELETION_JOB_SERVICE_ACCOUNT` entspricht dem konfigurierten Konto:

   ```sh
   gcloud scheduler jobs create http lost-key-finder-account-deletions \
     --project="$PROJECT_ID" --location="$REGION" \
     --schedule='*/5 * * * *' --http-method=POST \
     --uri="$DELETION_JOB_URL" --attempt-deadline=300s \
     --oidc-service-account-email="$DELETION_JOB_SERVICE_ACCOUNT" \
     --oidc-token-audience="$DELETION_JOB_URL"
   ```

   Cloud Scheduler muss aktiviert sein; der Operator braucht `iam.serviceAccounts.actAs` für das Jobkonto, der Scheduler-Service-Agent muss die Tokens ausstellen dürfen. Vorhandene Jobs zuerst prüfen und gezielt aktualisieren.

5. Mit einem ausdrücklich für die Abnahme angelegten Dev-Konto testen: einmal bestätigen, Browser schliessen, Scheduler ausführen und lokalen/Firebase-Abschluss prüfen. Keine bestehenden Benutzerkonten als Test löschen. Einen IAM-Fehler und anschliessende Wiederaufnahme prüfen, bevor die Produktionsfunktion freigegeben wird.

## Betriebsprüfung

Die interne Antwort `processed` zählt angenommene Bearbeitungsversuche, nicht erfolgreiche Löschungen. HTTP 200 des Scheduleraufrufs beweist keinen erfolgreichen Firebase-Abschluss. Dafür `completedAt` prüfen. Wiederholt fehlgeschlagene Aufträge anhand von `failures`, `lastFailureAt` und `nextAttemptAt` kontrollieren; sehr alte offene Aufträge müssen im Monitoring alarmiert werden. Der Worker schreibt eine feste Fehlermeldung ohne Exception-/Providerinhalt. Kein öffentlicher Zugriff auf das Ledger und keine ungeschützte Diagnose-API.

Bei IAM-/Netzfehlern Berechtigung/Erreichbarkeit reparieren und den nächsten fälligen Lauf zulassen. Kein Konto reaktivieren, keine UID austauschen und keine Historie manuell löschen. Der Hash-Abgleich verhindert den externen Löschaufruf bei einer widersprüchlichen Identität. Nach einem Restore muss das Löschledger vor fachlicher Freigabe angewendet werden; die allgemeine Backup-/Restore-Abnahme bleibt ein eigener Betriebsschritt.
