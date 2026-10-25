# Login und Finder-Einstieg

## Navigation

Die Startseite zeigt Tag-ID-/QR-Einstieg und E-Mail-Anmeldung unmittelbar als zwei
Bereiche. Auf kleinen Bildschirmen stehen sie untereinander. Der Kopfbereich führt
unangemeldete Besitzer direkt auf `/#owner`; Finder können `/#finder` verwenden.

Es gibt keine separate Login-Formularseite mehr. Bestehende `/login`-Links werden
auf `/#owner` umgeleitet; `/login#finder` bleibt durch Weiterleitung auf `/#finder`
nutzbar. Erfolgsseite `/login/link-sent` und Callback `/auth/callback` bleiben stabil.

Ein öffentlicher Tag-Link enthält ausschliesslich die öffentliche UUID-v4. Freigegebene unzugewiesene Hardware-Tags führen zur Registrierung mit E-Mail-Anmeldung und expliziter Zuordnung; zugewiesene Tags zur eigenen Verwaltung oder zum Hinweis auf den späteren Kontaktprozess. Ein zusätzlicher Aktivierungscode ist nicht vorgesehen; die erste bestätigte Zuordnung gewinnt. Eigene Tags werden weiterhin nach Anmeldung neu erzeugt und unmittelbar zugeordnet.

Admins mit manuell gesetztem `users.isAdmin` erreichen nach gewöhnlicher Anmeldung die globale Tagübersicht. Laufende Registrierungen haben Vorrang vor diesem Standardziel. Vollständiger Stand: [Tag-/Admin-Umsetzung](tag-implementation-status.md).

## QR und Tag-ID

Akzeptiert werden öffentliche UUID-v4-Tag-IDs sowie Tag-Links dieser Website im
Format `/f/{publicId}`. Fremde URLs, zusätzliche Query-/Fragment-Daten, ungültige
IDs und aktive Inhalte werden nicht als Navigationsziel übernommen. Navigation
bleibt immer auf der lokalen Tag-Route.

Der QR-Reader wird erst bei Bedarf geladen. Kamerazugriff erfolgt ausschliesslich
nach einem Klick auf den Scan-Button. Der Reader bevorzugt die Rückkamera und wird
nach Scan, Abbruch und Verlassen der Seite zerstört. Bei fehlender Kamera oder
verweigerter Freigabe stehen Tag-Eingabe und lokale QR-Bildauswahl zur Verfügung.
QR-Bilder bis 10 MB werden lokal verarbeitet und nicht an einen Server hochgeladen.
Der Decoder enthält einen Worker-Fallback für Browser ohne native QR-Erkennung.

Die öffentliche `/f/{publicId}`-Ansicht löst den Zustand über das Backend auf. Antworten enthalten weder Besitzeridentität noch private Bezeichnungen. Ein noch nicht existierender, nicht freigegebener, inaktiver, gesperrter oder stillgelegter Tag ist neutral nicht verfügbar. Unbekannte IDs erzeugen keinen Bestand. Fundmeldung und Kontaktaufnahme sind weiterhin ein späterer eigener Use Case; bis dahin wird keine Meldung als gespeichert oder versandt dargestellt.

Bei Registrierung enthält der feste Continue-Link ausschliesslich die nicht geheime `registrationId`. Der Callback validiert direkte oder verschachtelte Fortsetzung auf gleicher Origin und festem Pfad, entfernt Query/Fragment und bewahrt Auth-Link und Vorgangs-ID getrennt im Speicher. Nach Firebase-Anmeldung lädt der neue Browser den verifizierten Vorgang und zeigt eine ausdrückliche Zuordnungsbestätigung. E-Mail, Frist und aktueller Tag-Zustand werden im Backend geprüft; der ursprüngliche Tab ist nicht erforderlich.

## Nach dem Linkversand

Nach erfolgreicher Annahme durch Firebase ersetzt die Navigation das Formular durch
`/login/link-sent`. Bei Fehler bleibt das Formular mit einer sicheren Fehlermeldung
sichtbar. Die Erfolgsseite sendet auch nach Reload keine weitere E-Mail, pollt keine
Sitzung und ist nicht zum Abschluss der Anmeldung erforderlich.

Die Seite erklärt, dass sie geschlossen werden kann und die Anmeldung aus der
E-Mail im dort geöffneten Browser weitergeht. Bei einem anderen Browser/Gerät fragt
der bestehende Callback die E-Mail erneut ab. Die Adresse wird nicht in die URL
der Erfolgsseite geschrieben. Ein Hinweis nennt den Spam-/Junk-Ordner.

## Versandprüfung am 25. Oktober 2026

Ein vom Benutzer autorisierter echter Versand wurde über die öffentliche Dev-Seite
angefordert. Firebase nahm die Anfrage erfolgreich an; der Empfang im Junk-Ordner
wurde bestätigt. Es lag damit kein reproduzierter Versandfehler vor.

Die gelesenen Cloud-Logs enthielten Konfigurationsereignisse, aber keine
protokollierten Versandfehler. Nutzeraktivitätslogs von Identity Platform waren
bisher nicht aktiv. Da der Browser direkt mit Firebase spricht, enthält Cloud Run
keinen Versandrequest. Eine umfangreiche Request-/Response-Protokollierung wurde
nicht nachträglich aktiviert; E-Mail, Link und Tokens werden nicht in diese
Dokumentation aufgenommen. Der funktionierende Versandablauf bleibt unverändert.

## Verifikation

- Komponenten-/Routingtests prüfen getrennte Formulare, direkte Login-Einstiege,
  Erfolgsnavigation mit `replaceUrl`, Fehlerpfad und Schutz vor Doppelversand.
- Validierungstests prüfen Tag-IDs, lokale Links und Ablehnung fremder Ziele.
- Reader-Tests prüfen Kamerafehler, lokale Bilder, Scanergebnis und Cleanup,
  auch wenn das Laden des Readers erst nach Komponentenzerstörung endet.
- Ein echter QR-PNG-Code wurde im lokalen Browser ausgewählt, vom realen Decoder
  erkannt und zur richtigen öffentlichen Tag-Route weitergeleitet.
- Desktopdarstellung und mobiler Breakpoint wurden im Browser geprüft; kein
  horizontaler Overflow, E-Mail-Einstieg erreicht das Formular auf kleinem Bildschirm.
- Live-Kamerazugriff auf echte Hardware wurde nicht automatisiert freigegeben.
