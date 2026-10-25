# Login und Finder-Einstieg

## Navigation

Die Startseite bietet einen expliziten E-Mail-Login im Kopfbereich und im Hero.
Die Links führen auf `/login#owner`, damit auf kleinen Bildschirmen direkt das
Besitzerformular sichtbar wird. Der Finder-Einstieg führt auf `/login#finder`.

Die Loginseite unterscheidet zwei Aufgaben: Finder geben eine Tag-ID ein oder
scannen einen QR-Code; Besitzer melden sich per E-Mail-Link an. Ein öffentlicher
Tag-Link ist kein Nachweis von Besitz und führt nicht zur Besitzer-Anmeldung.

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

Der vollständige Tag-/Fundmeldungsprozess ist weiterhin ein geplanter eigener
Use Case. Die öffentliche `/f/{publicId}`-Ansicht zeigt bis zu dessen Implementierung
neutral an, dass eine Fundmeldung derzeit nicht gestartet werden kann. Sie behauptet
weder, einen Tag serverseitig geprüft zu haben, noch eine Meldung gespeichert zu haben.

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
