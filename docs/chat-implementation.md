# Fundmeldungen und privater Chat

Stand: 26. Oktober 2026. Verbindliche Regeln dieses Ausbaus ersetzen die älteren Zahlungs- und 30-Tage-Schreibsperren in den ursprünglichen Phasenplänen.

## Vereinbarter Umfang

- Kostenloser Chat mit Text und Bildern. Zahlungen und Backup Owner sind spätere Features.
- Ein aktiver Fundfall pro Tag, ein privater Chat je Finder in diesem Fall. Eigene Tags und registrierte Hardware-Tags verwenden denselben Ablauf.
- Erste Nachricht mit Text (maximal 2000 Unicode-Codepoints), optional einem Bild. Weitere Nachrichten benötigen Text oder Bild. JPEG/PNG bis 5'000'000 Bytes und 2048 × 2048 Pixel; serverseitige Neukodierung in JPEG entfernt Metadaten, Transparenz und Animation. Bilder werden nur nach explizitem Laden angezeigt, wichtige Bildinhalte sollen im Text beschrieben werden.
- Keine automatische Schreibfrist. Der Besitzer schliesst den gesamten Fundfall mit allen Finder-Chats ab. Weitere Fundmeldungen können danach einen neuen Fall erzeugen.
- Besitzerfreigabe und Soft Delete schliessen bestehende Chats im selben Commit. Deaktivierung verhindert nur neue Meldungen; bestehende Chats bleiben beschreibbar. Sperrung verhindert Schreiben, erhält aber die bestätigten Leserechte.
- Teilnehmer werden beim Erstellen fest gespeichert. Ein Besitzerwechsel überträgt keine alten Chatrechte. Ehemalige Besitzer finden ihre Chats über `/chats`; die Ansicht enthält keine aktuellen privaten Tag-Daten anderer Besitzer.
- Admins dürfen alle Chats eines Tags lesen, aber nicht unter fremder Identität schreiben, Lesestände ändern oder allein kraft Adminrecht einen Fall abschliessen.
- Chat-Erstellung, Nachrichten und Abschluss erscheinen mit stabilen Chat-/Nachrichtenreferenzen in der Historie. Inhalte bleiben ausschliesslich in Nachrichten/Bildern. Normale Tag-Historien enthalten nur die Chats mit eigener Teilnehmerzuordnung, Admins alle.

## Ablauf und Daten

Ein Scan öffnet `/f/{publicId}`. Der Finder erfasst Nachricht, optionales Bild und E-Mail. Ein maximal vier Stunden gültiger `finderReportDrafts`-Datensatz entsteht; er vermittelt keinen Chat-Zugriff. Firebase bestätigt die E-Mail, auch auf einem anderen Gerät. Die API prüft die bestätigte Adresse und die unveränderte Besitzerzuordnung erneut. Erst dann entstehen Fall, privater Chat, Initialnachricht, Historie und Benachrichtigungsauftrag transaktional. Bereits angemeldete Finder benötigen keinen weiteren Anmeldelink.

Der Browser erneuert abgelaufene Meldungsentwürfe mit einem neuen Idempotenzschlüssel; normale Wiederholungen verwenden weiterhin denselben Vorgang. Multipart-Eingaben (E-Mail, Text, Bild) bleiben im Request-Body des generierten API-Clients. Fehlgeschlagene Nachrichtenabrufe zeigen eine Wiederholen-Aktion und werfen keinen unbehandelten Resource-Fehler.

Die Callback-URL erlaubt exakt einen validierten Zweck: Tag-Registrierung, Fundmeldung oder konkreter Chat. Auth-Codes werden vor Konfigurationsabruf und Routing aus der Browseradresse entfernt. Benachrichtigungslinks führen auf die Chatseite mit eigener Anmeldemöglichkeit und sicherer Fortsetzung.

Ergänzte Collections:

| Collection | Zweck |
| --- | --- |
| `finderReportDrafts` | Temporärer Meldungsentwurf; TTL, E-Mail/Text werden nach Abschluss entfernt. |
| `lossCases` | Aktueller oder abgeschlossener Fundfall; eindeutiger sparsamer `activeKey` verhindert mehrere aktive Fälle. |
| `chats` | Feste Teilnehmer, Fall-/Tagreferenz, Nachrichtenstand, Lesestände und Abschluss. |
| `messages` | Unveränderliche Nachrichten, Sender, stabile ID und laufende Nummer pro Chat. |
| `messageImages` | Bereinigtes Bild, zunächst Entwurf, danach genau eine Nachricht. Nur nicht zugeordnete Bilder haben TTL. |
| `chatNotifications` | Gebündelter Versandstand pro Chat und Empfänger mit Fälligkeit, Lease und Fehlerstatus. |

Kein Einbetten wachsender Verläufe in `tags` oder `users`. Bestehende Collections und Daten bleiben erhalten; kein Datenbankreset. Ein technischer `tags.chatVersion`-Zähler serialisiert Meldung/Versand gegen parallele Besitzerfreigabe und Löschung. Wiederholungen verwenden stabile UUID-Idempotenzschlüssel. Eine neue Meldung desselben Finders im aktiven Fall ergänzt seinen bestehenden Chat; derselbe Meldungsabschluss bleibt ohne doppelte Nachricht wiederholbar. API-Zugriffe prüfen das aktive lokale Benutzerkonto bei jedem Request.

Chatlisten haben 25 Einträge pro Seite, Nachrichten werden in Fenstern zu 50 geladen. Offene Chatseiten aktualisieren sich alle zehn Sekunden im sichtbaren Browserfenster. Keine WebSockets, Lesebestätigungen für den Gegenüber, Nachrichtenedits oder Nachrichtenlöschung.

## E-Mail und Betrieb

Neue bestätigte Fundmeldungen lösen unmittelbar einen Versandversuch aus. Weitere Nachrichten werden je Chat/Empfänger zusammengefasst: frühestens 15 Minuten nach Beginn des offenen Bündels. Lesen bis zu diesem Nachrichtenstand unterdrückt den Versand. Die E-Mail enthält nur einen Link, keine Chattexte, Bilder oder fremde Kontaktdaten.

SMTP-Aufrufe finden ausserhalb der fachlichen Transaktion statt. Ein Mailfehler macht die Fundmeldung nicht rückgängig. Der Auftrag bleibt `FAILED` und wird frühestens 15 Minuten später erneut versucht. Eine dreiminütige Lease verhindert konkurrierenden Versand; nach Timeout kann SMTP bereits angenommen haben, deshalb sind seltene Doppelzustellungen möglich. Während des Versands eingehende Antworten bleiben für das nächste Bündel vorgemerkt. `SENT` bedeutet SMTP-Annahme, keine garantierte Zustellung.

Benötigte Umgebungsvariablen:

- `CHAT_SMTP_HOST`, `CHAT_SMTP_PORT` (587), `CHAT_SMTP_USERNAME`, `CHAT_SMTP_PASSWORD`, `CHAT_MAIL_FROM`.
- `CHAT_SMTP_START_TLS=true` im Betrieb; `false` ausschliesslich für den lokalen Testserver.
- `TAG_PUBLIC_BASE_URL`: kanonische HTTPS-Origin für die Chatlinks.
- `CHAT_NOTIFICATION_JOB_AUDIENCE`: exakte konfigurierte Audience des Scheduler-Tokens.
- `CHAT_NOTIFICATION_JOB_SERVICE_ACCOUNT`: ausschliesslich dafür verwendetes Servicekonto.

SMTP-Passwort über Secret Manager einbinden. Ohne SMTP-Konfiguration funktioniert der Chat; Versandaufträge bleiben fehlgeschlagen. Anbieter, Absender und Domain müssen für echten Versand eingerichtet werden.

Ein Cloud-Scheduler-Job ruft alle fünf Minuten `POST /api/v1/internal/chat-notifications/dispatch` direkt auf Cloud Run auf, mit OIDC-Token des festgelegten Servicekontos. Das Backend prüft Google-Signatur, Issuer, Audience, Gültigkeit und bestätigte Servicekonto-E-Mail. Firebase-Nutzertokens gewähren keinen Zugang. Maximal zehn Aufträge pro Aufruf, weitere folgen beim nächsten Lauf. Die Fünf-Minuten-Taktung ersetzt für diesen Zweck die frühere Planung ausschliesslich täglicher Wartung; keine dauerhaft laufenden Worker. Siehe [Google Cloud Scheduler: HTTP-Authentifizierung](https://docs.cloud.google.com/scheduler/docs/http-target-auth).

Vor Deployment: Indizes über `npm run firestore:tag-indexes --workspace=@lost-key-finder/api` mit Operatorrechten anlegen. Der Runtime-Serviceaccount legt keine Indizes an. SMTP/Secrets und Scheduler sind separate Einrichtungsschritte; es gibt keinen Versand echter Test-E-Mails durch die Tests.

## Prüfung und spätere Arbeit

Offline-HTTP-Tests prüfen Identitäten, Teilnehmerrechte, Admin-Lesen, Bilder, Idempotenz, Nachrichtenpagination, Lesestände, Fallabschluss und Tag-Löschung. Ein lokaler SMTP-Test prüft echten Protokollversand und Antworten während des Versands ohne externe Empfänger. Firestore-Tests prüfen konkurrierende Meldungen, genau einen aktiven Fall, parallele Retry-Aufrufe, Rollback, Historienabfragen und Stilllegung. Zusätzlich durchläuft derselbe Tag zweimal den vollständigen Zyklus: Finder meldet, Besitzer antwortet, Finder antwortet, Besitzer schliesst den Fall. Beide behalten den lesbaren Verlauf; Schreiben ist danach gesperrt, der Tag bleibt aktiv und der nächste Durchlauf erhält einen neuen Fall und Chat.

Aufbewahrung und rechtliche Löschfristen sind weiterhin vor dem Produktivstart festzulegen. Ohne diese Entscheidung keine automatische Löschung vorhandener Chats/Bilder. Moderation, Nutzerblockierung, Backup Owner und Zahlungen folgen separat.

Beispiel für die einmalige Scheduler-Einrichtung nach Deployment (Platzhalter ersetzen; kein automatischer Aufruf beim App-Start):

```sh
CHAT_API_URL="https://DEIN-CLOUD-RUN-SERVICE.run.app"
CHAT_JOB_ACCOUNT="chat-notifications@DEIN-PROJEKT.iam.gserviceaccount.com"
gcloud scheduler jobs create http lost-key-finder-chat-notifications \
  --project=DEIN-PROJEKT --location=europe-west6 \
  --schedule='*/5 * * * *' --time-zone=Europe/Zurich \
  --http-method=POST --attempt-deadline=300s \
  --uri="$CHAT_API_URL/api/v1/internal/chat-notifications/dispatch" \
  --oidc-service-account-email="$CHAT_JOB_ACCOUNT" \
  --oidc-token-audience="$CHAT_API_URL"
```

Zuvor Cloud Scheduler aktivieren und das dedizierte Servicekonto anlegen. Die erstellende Identität benötigt `iam.serviceAccounts.actAs`, der Scheduler-Service-Agent muss Tokens dafür ausstellen dürfen. `CHAT_NOTIFICATION_JOB_AUDIENCE` auf dieselbe `CHAT_API_URL` und `CHAT_NOTIFICATION_JOB_SERVICE_ACCOUNT` auf dasselbe Konto setzen. Die Deployment-Pipeline übernimmt beide Variablen sowie SMTP-Einstellungen aus den GitHub-Environment-Variablen. `CHAT_SMTP_PASSWORD_SECRET` enthält eine Secret-Manager-Referenz inklusive Version, beispielsweise `chat-smtp-password:1`; der Runtime-Serviceaccount benötigt Lesezugriff darauf. Die Pipeline erstellt weder Anbieterzugänge noch den Scheduler-Job.
