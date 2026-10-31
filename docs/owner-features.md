# Druckvorlagen, Kontolöschung und Inhaltsmeldungen

Stand: 31. Oktober 2026. Dieser Ausbau ergänzt den aktuellen Tag-/Chatstand. Supportaktionen, Besitzerkorrekturen und Backup-Besitzer sind auf ausdrücklichen Wunsch zurückgestellt. Zahlungen bleiben ebenfalls späterer Umfang.

## Druckvorlagen

`/tags/print` verlangt dieselbe aktive Sitzung wie die Besitzerverwaltung. Einstieg über eigene Tagliste und Details selbst erstellter Tags. Die geschützte Besitzer-API liefert die Auswahl; Hardware-, gesperrte und stillgelegte Tags werden nicht angeboten. Bis zu 25 Tags können über mehrere Listenseiten ausgewählt werden. Vor dem Export ist der Benutzer weiterhin Besitzer seiner Sitzung; eine Sessionänderung verwirft das Ergebnis.

Der PDF-Download entsteht lokal über die bei Bedarf geladene Standardbibliothek `pdf-lib` und den bestehenden QR-Encoder. Kreditkartenformat: 85,60 × 53,98 mm je PDF-Seite. A4: zehn Karten je Seite, Schnittlinien und weitere Seiten bei Bedarf. Druck mit 100 % / tatsächlicher Grösse; die PDF-Viewer-Empfehlung deaktiviert Skalierung. Gedruckt werden Marke, lokalisierte Scanaufforderung, vorhandene öffentliche UUID und QR-Link derselben Umgebung. Keine Neuanlage von Tags und keine privaten Bezeichnungen. Inaktive Tags können gedruckt werden, müssen jedoch vor Verwendung aktiviert werden. Dev-Ausdrucke sind keine Produktionsdateien. Die dauerhafte Domain bleibt vor Herstellung von Verkaufstags festzulegen.

## Kontolöschung

`DELETE /api/v1/me` verlangt einen gültigen Firebase-Token. Vor dem Start ist eine höchstens fünf Minuten alte Anmeldung sowie eine frische bestätigte Firebase-Identität erforderlich. Ein Checkbox-Dialog erklärt die unwiderruflichen Folgen. Bei zu alter Anmeldung gibt es HTTP 409 und einen Weg zur erneuten E-Mail-Anmeldung.

1. Eine Transaktion setzt das lokale Konto auf `DELETING`, entzieht Adminrechte und speichert einen Löschauftrag. Normale fachliche APIs lehnen dieses Konto sofort ab. `/me` liefert noch das eigene Profil mit `DELETING`, damit die Anmeldung zur geschützten Fortsetzungsseite umleiten kann; andere geschützte Ansichten werden nicht aktiviert.
2. In begrenzten, wiederholbaren Schritten werden eigene Tags stillgelegt und Hardware-Zuordnungen gelöst, jeweils mit atomarer Historie und Chatabschluss. Vorher gesperrte/stillgelegte Hardware wird nicht reaktiviert. Private aktuelle Bezeichnungen und Registrierungszeitpunkte werden entfernt. Alte Tag-Registrierungsentwürfe werden entwertet.
3. Eigene Finder-Chats werden schreibgeschützt. Zugehörige temporäre Entwürfe, unzugeordnete Entwurfsbilder und Benachrichtigungen werden entfernt. Das lokale Profil wird bis zum externen Abschluss pseudonymisiert.
4. Der bestehende Firebase-REST-Adapter löscht ausschliesslich die durch den ID-Token bezeichnete Identität. Kein Admin-Schlüssel und keine zusätzlichen Cloud-Berechtigungen. Nach Erfolg wird das lokale Benutzerkonto entfernt und der Löschauftrag abgeschlossen.

Antwort `{completed:false}` verlangt einen weiteren Aufruf desselben Endpunkts; die UI führt diese nacheinander aus. Ein Fehler hebt die Sperre nicht auf. Nach erneuter Anmeldung kann ein unvollständiger Auftrag fortgesetzt werden. Wiederholungen abgeschlossener Aufträge sind erfolgreich und erzeugen keine erneuten Historienereignisse. Es gibt keinen automatischen Hintergrundlauf für diese Löschaufträge; liegengebliebene Aufträge sind vor Betriebsfreigabe im Wartungs-/Supportprozess zu berücksichtigen.

`accountDeletions` enthält als `_id` ausschliesslich den SHA-256-Prüfwert der zufälligen Firebase-UID, die pseudonyme lokale Benutzerreferenz und den Fortschritt/Zeitpunkte. Kein E-Mail- oder Tokeninhalt. Diese minimalen Tombstones bleiben zur Wiederholungssperre und als Löschledger erhalten. Ein Restore muss sie anwenden, bevor fachlicher Zugriff freigegeben wird. Eine Neuanmeldung derselben E-Mail mit neuer Firebase-UID erzeugt ein neues Konto ohne frühere Rechte.

Schreibende Tag-/Chattransaktionen aktualisieren das aktive Benutzerkonto und serialisieren sich dadurch gegen den Löschbeginn. Die Registrierung neuer Hardware und neue Fundmeldungen können einem löschenden Konto keine neuen Rechte zuweisen. Bestehende pseudonyme Teilnehmer-/Sender-/Akteurreferenzen bleiben stabil; gemeinsame Chattexte und Bilder bleiben für andere berechtigte Teilnehmer lesbar. Auch selbst im Chat mitgeteilte persönliche Angaben werden nicht automatisch redigiert. Der Bestätigungsdialog benennt dies ausdrücklich. Das Feature ersetzt keine noch festzulegenden Aufbewahrungsfristen oder gezielte Inhaltsbereinigung.

## Inhaltsmeldungen

Nur gespeicherte Chatteilnehmer können eine tatsächlich zu diesem Chat gehörende Nachricht melden; reine Admin-Leserechte reichen nicht. Auch geschlossene Chats bleiben meldbar. `POST /api/v1/chats/{chatId}/messages/{messageId}/reports` akzeptiert `SPAM`, `HARASSMENT` oder `OTHER`. Der Zugriff wird serverseitig geprüft; maximal 20 neue Meldeversuche je Benutzer/Stunde. Der stabile Dokumentenschlüssel aus Benutzer- und Nachrichtenreferenz verhindert Duplikate. Wiederholen ändert den ursprünglichen Grund nicht.

`abuseReports` speichert Chat-/Nachrichten-/Melderreferenz, Grund, Zeitpunkt und `OPEN`, ohne Kopie von Text/Bild/E-Mail. Eine Meldung blendet nichts aus, sperrt niemanden und verändert keinen Chat. Die UI weist ausdrücklich darauf hin, dass Supportbearbeitung derzeit nicht verfügbar ist. Es gibt keine neue Supportrolle, keinen öffentlichen Meldungsabruf und keine Bearbeitungsoberfläche in diesem Umfang.
