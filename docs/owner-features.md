# Druckvorlagen, Kontolöschung und Inhaltsmeldungen

Stand: 31. Oktober 2026. Dieser Ausbau ergänzt den aktuellen Tag-/Chatstand. Supportaktionen, Besitzerkorrekturen und Backup-Besitzer sind auf ausdrücklichen Wunsch zurückgestellt. Zahlungen bleiben ebenfalls späterer Umfang.

## Druckvorlagen

`/tags/print` verlangt dieselbe aktive Sitzung wie die Besitzerverwaltung. Einstieg über eigene Tagliste und Details selbst erstellter Tags. Die geschützte Besitzer-API liefert die Auswahl; Hardware-, gesperrte und stillgelegte Tags werden nicht angeboten. Bis zu 25 Tags können über mehrere Listenseiten ausgewählt werden. Vor dem Export ist der Benutzer weiterhin Besitzer seiner Sitzung; eine Sessionänderung verwirft das Ergebnis.

Der PDF-Download entsteht lokal über die bei Bedarf geladene Standardbibliothek `pdf-lib` und den bestehenden QR-Encoder. Kreditkartenformat: 85,60 × 53,98 mm je PDF-Seite. A4: zehn Karten je Seite, Schnittlinien und weitere Seiten bei Bedarf. Druck mit 100 % / tatsächlicher Grösse; die PDF-Viewer-Empfehlung deaktiviert Skalierung. Gedruckt werden Marke, lokalisierte Scanaufforderung, vorhandene öffentliche UUID und QR-Link derselben Umgebung. Keine Neuanlage von Tags und keine privaten Bezeichnungen. Inaktive Tags können gedruckt werden, müssen jedoch vor Verwendung aktiviert werden. Dev-Ausdrucke sind keine Produktionsdateien. Die dauerhafte Domain bleibt vor Herstellung von Verkaufstags festzulegen.

## Kontolöschung

Die Kontolöschung wird serverseitig ausgeführt; Details, Wiederholungen, technische Firebase-Berechtigung und Einrichtung stehen in [Backendgesteuerte Kontolöschung](account-deletion.md).

`DELETE /api/v1/me` prüft eine frische bestätigte Anmeldung, sperrt das Konto und speichert den Auftrag transaktional. HTTP 202 bestätigt die Annahme. Das Frontend zeigt eine Bestätigungsseite und beendet die Sitzung; es verarbeitet keine Löschschritte. Cloud Scheduler führt die begrenzte Bereinigung und administrative Firebase-Löschung mit Dienstcredentials aus, auch nach Schliessen des Browsers und Ablauf des Benutzertokens.

Eigene Tags werden stillgelegt; Hardware-Zuordnungen werden gelöst, ohne gesperrte Hardware zu reaktivieren. Gemeinsame Chattexte, Bilder und Historien bleiben für andere Berechtigte mit pseudonymen Referenzen erhalten. Im Inhalt selbst mitgeteilte persönliche Angaben werden nicht automatisch redigiert; der Dialog benennt dies ausdrücklich. Die endgültigen Aufbewahrungsfristen bleiben festzulegen.

## Inhaltsmeldungen

Nur gespeicherte Chatteilnehmer können eine tatsächlich zu diesem Chat gehörende Nachricht melden; reine Admin-Leserechte reichen nicht. Auch geschlossene Chats bleiben meldbar. `POST /api/v1/chats/{chatId}/messages/{messageId}/reports` akzeptiert `SPAM`, `HARASSMENT` oder `OTHER`. Der Zugriff wird serverseitig geprüft; maximal 20 neue Meldeversuche je Benutzer/Stunde. Der stabile Dokumentenschlüssel aus Benutzer- und Nachrichtenreferenz verhindert Duplikate. Wiederholen ändert den ursprünglichen Grund nicht.

`abuseReports` speichert Chat-/Nachrichten-/Melderreferenz, Grund, Zeitpunkt und `OPEN`, ohne Kopie von Text/Bild/E-Mail. Eine Meldung blendet nichts aus, sperrt niemanden und verändert keinen Chat. Die UI weist ausdrücklich darauf hin, dass Supportbearbeitung derzeit nicht verfügbar ist. Es gibt keine neue Supportrolle, keinen öffentlichen Meldungsabruf und keine Bearbeitungsoberfläche in diesem Umfang.
