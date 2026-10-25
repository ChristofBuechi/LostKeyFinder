# Verbindliche Tag-Historie

Stand: 25. Oktober 2026. Tag-Änderungen werden jetzt implementiert; der Chat-Prozess folgt später.

## Vorhandene Tag-Funktionen

Jede über die Anwendung ausgeführte fachliche Änderung am Tag muss zusammen mit ihrem Historieneintrag atomar gespeichert werden. Der Eintrag enthält Zeitpunkt, Aktion, handelnden lokalen Benutzer und die geänderten Felder mit Vorher-/Nachher-Wert. Die Historie bleibt bei Soft Delete und Besitzerwechsel bestehen.

Abgedeckt sind eigene Erstellung, Serienerstellung und Produktionsfreigabe, Registrierung, Aktivierung/Deaktivierung, Besitzerfreigabe und Soft Delete. Serienereignisse werden über die beständige Serienreferenz dem Tag zugeordnet. Exporte werden ebenfalls protokolliert. Wiederholte idempotente Aufträge erzeugen keine doppelten Änderungsereignisse.

Bei Registrierung und Freigabe wird insbesondere `primaryOwnerUserId` vorher/nachher festgehalten. Beim Freigeben bleiben die vorherige private Bezeichnung und der Registrierungszeitpunkt in der geschützten Historie erhalten. Laufende Tag-Daten werden wie vorgesehen zurückgesetzt.

Admins sehen alle Ereignisse, Akteur- und Besitzerreferenzen sowie Änderungsdetails. Der aktuelle Besitzer sieht die Tag-Historie ohne fremde Benutzerreferenzen und ohne frühere private Bezeichnungen anderer Besitzer. Die öffentliche Scan-API liefert keine Historie.

Bestehende Audit-Ereignisse bleiben erhalten. Frühere Registrierungs-/Freigabeereignisse erlauben eine eindeutige Ableitung der Besitzerzuordnung aus Aktion und Akteur. Weitere fehlende Vorher-/Nachher-Werte werden nicht erfunden. Ein vorhandenes Erstellungsdatum bleibt als historischer Eckpunkt sichtbar. Direkte Datenbankänderungen ausserhalb der Anwendung werden von diesen API-Transaktionen nicht erfasst.

## Chats: verbindlich für die spätere Umsetzung

- Jeder Chat gehört dauerhaft zu genau einem Tag und dem ursprünglichen Fund-/Verlustfall. Besitzerwechsel, Freigabe und Soft Delete lösen diese Zuordnung nicht auf.
- Die Tag-Historie führt sämtliche Chats aller Besitzerperioden und Fälle auf. Ereignisse für Chat-Erstellung, Nachrichten, relevante Zustandsänderungen und Abschluss verweisen auf stabile Chat-/Nachrichtenreferenzen.
- Aus einem berechtigten Historieneintrag lässt sich der konkrete Chat mit Nachrichten und zugehörigen Bildern öffnen. Inhalte werden nicht zusätzlich in das Audit-Protokoll kopiert.
- **Admins dürfen sämtliche vorhandenen Chats des Tags lesen**, einschliesslich früherer Besitzer und geschlossener Fälle. Das lokale Konto muss bei jedem Zugriff aktiv und `isAdmin == true` sein. Dieser Lesezugriff verlangt keinen Owner-Access-Grant und erlaubt kein Schreiben unter fremder Identität.
- **Normale Benutzer dürfen jeden Chat lesen, in dem sie im Chat-Objekt als Owner oder Teilnehmer gespeichert sind.** Die Identitätsprüfung verwendet die verknüpfte lokale Benutzer-ID.
- Die Autorisierung wird direkt anhand des Chat-Objekts geprüft. Eine zeitliche Berechnung von Tag-Besitzerperioden oder ausschliesslich der aktuelle `tags.primaryOwnerUserId` entscheidet nicht über das Chat-Leserecht.
- Besitzerfreigabe, Wechsel, erneute Registrierung und Soft Delete des Tags schreiben die Owner-/Teilnehmerreferenzen bereits vorhandener Chats nicht um. Frühere Besitzer können ihre Chats weiterhin lesen, sofern sie dort als Teilnehmer hinterlegt sind. Ein neuer Tag-Besitzer bekommt keine fremden Chats automatisch zugeordnet.
- Eigene Chats bleiben über die Historie beziehungsweise eine Teilnehmer-Chatübersicht erreichbar, auch wenn der Benutzer den Tag inzwischen nicht mehr besitzt. Diese historische Ansicht darf keine aktuellen privaten Tag-Daten späterer Besitzer offenlegen.
- Ein Beteiligter kann einen vorhandenen Chat auch nach dessen Abschluss lesen. Zahlungs-/Freigabestatus für Schreibvorgänge ersetzt die Teilnehmer-/Adminprüfung beim Lesen nicht. Die Schreib- und Zahlungsabläufe werden im späteren Chat-Prozess separat umgesetzt.
- Chat-/Nachrichtenänderung und zugehöriges Historienereignis werden gemeinsam gespeichert. Die bestehenden offenen Entscheidungen zur Aufbewahrung werden dadurch nicht als unbegrenzte Speicherung entschieden.

Abnahmetests bei der Chat-Umsetzung: mehrere Besitzerwechsel und mehrere Chats pro Tag; Adminzugriff auch nach Freigabe/Soft Delete; ehemalige Besitzer behalten Zugriff auf Chats mit ihrem Teilnehmerverweis; Nichtbesitzer mit Teilnehmerverweis dürfen ihren Chat lesen; Besitzer ohne Teilnehmerverweis dürfen den Chat nicht lesen; Tag-Wechsel schreibt keine Chat-Teilnehmer um; Verlinkung auf den richtigen Verlauf; Rechteentzug beim nächsten Request; atomarer Rollback ohne verwaiste Ereignisse.

Die Chat-Funktion und entsprechende Endpunkte sind mit dieser Änderung noch nicht implementiert.
