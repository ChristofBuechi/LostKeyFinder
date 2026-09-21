# Fachliche Regeln

## Invarianten

- Ein Tag besitzt genau einen Primary Owner und höchstens einen aktiven Verlustfall.
- Ein Finder besitzt je Verlustfall höchstens einen privaten Chat und sieht keine anderen Finder.
- Owner sehen Chatinhalt nur nach Access Grant; eine bezahlte Zahlung allein genügt nicht.
- Archivierung ist endgültig. Sperren sind reversibel, reaktivieren aber keinen archivierten Fall.

## Tags und Berechtigungen

| Tagstatus | Wirkung |
| --- | --- |
| `ACTIVE` | Öffentlich auflösbar; neuer Verlustfall möglich. |
| `INACTIVE` | Durch berechtigten Owner reaktivierbar. |
| `BLOCKED` | Reversible Support-Sperre ohne Endnutzerzugriff. |
| `RETIRED` | Endzustand nach Löschung des Primary Owners; keine Reaktivierung oder neuen Fälle. |

Der Primary Owner kann höchstens drei offene Einladungen oder aktive Backup Owner verwalten. Die Annahme verlangt die exakt passende normalisierte, verifizierte E-Mail der authentifizierten Identität. Jede Annahme erhält eine neue `membershipId`. Archivzugriff eines Backup Owners verlangt dieselbe noch aktive Mitgliedschaft, die beim Fall bestand. Entfernen und erneutes Einladen stellt daher keinen früheren Zugriff wieder her.

## Fund und Fall

Ein anonymer Draft speichert Eingabe und normalisierte E-Mail nur bis zum Magic-Link-Abschluss. `flowId` und Statustoken erlauben keinen Zugriff auf Inhalt oder Chat. Completion verlangt ein gültiges JWT mit exakt passender normalisierter, verifizierter E-Mail. Die erste erfolgreiche Completion erzeugt Fall, Chat, Initialnachricht und Benachrichtigung idempotent.

| Fallstatus | Übergänge |
| --- | --- |
| `REPORTED` | Owner bestätigt den Fall und wechselt zu `AWAITING_PAYMENT`; andernfalls Archiv mit Grund `NOT_LOST`. |
| `AWAITING_PAYMENT` | Verifizierte Zahlung nach `CHAT_OPEN`; Ablehnung oder Fristablauf ins Archiv. |
| `CHAT_OPEN` | Manueller Abschluss oder Ablauf ins Archiv. |
| `CLOSING` | Klärt eine offene Zahlung vor endgültiger Archivierung. |

Ohne Ownerreaktion endet ein Fall 30 Tage nach der ersten Meldung. Ein bestätigter, unbezahlter Fall endet 30 Tage nach Bestätigung. Ein Finder kann seine Meldung bis zum Access Grant unwiderruflich zurückziehen.

## Chat und Access Grant

Jede Nachricht benötigt Text, ein Bild oder beides; Text ist auf höchstens 2'000 Unicode-Zeichen begrenzt. Die erste Nachricht eines Finder-Chats benötigt immer nicht leeren Text. Bei einem informativen Bild muss der Text dessen wesentliche Aussage beschreiben. Das Backend akzeptiert höchstens 5'000'000 Eingabebytes und 2048 x 2048 Pixel, prüft Dimensionen vor unbeschränkter Pixelallokation, begrenzt Zeit, Speicher und parallele Verarbeitung, entfernt Metadaten und Animationen und codiert das Bild neu. Fehlerhafte oder zu aufwendige Bilder werden abgelehnt. Sichtbare Lesebestätigungen sind nicht Phase 1.

Nur ein direkt bei Mollie geprüftes Live-`paid` mit eindeutiger Provider-ID, CHF 10.00, exakter Fall-/Payment-Zuordnung und Ausgangszustand `AWAITING_PAYMENT` kann atomar `accessGrantedAt`, `accessGrantedPaymentId`, `chatAccessUntil` und `CHAT_OPEN` setzen. Ein spätes `paid` in `CLOSING` wird dokumentiert und vollständig zurückerstattet, öffnet aber keinen Chat. Der Grant gilt höchstens 30 Tage für aktive, ungesperrte Chats des Falls. Danach sind keine neuen Nachrichten oder Chats möglich.

Ein Refund der Zahlung, die den Access Grant erzeugt hat, archiviert den offenen Fall. Eine verspätete Zahlung in `CLOSING` wird nach bestätigter vollständiger Rückerstattung ebenfalls archiviert, ohne einen Grant zu erzeugen; Refunds anderer Zahlungen ändern den Grant nicht. Chargebacks werden protokolliert und geprüft, entziehen einen bestehenden Grant aber nicht automatisch.

Jeder logische Checkout besitzt einen stabilen Idempotency-Key. Nach einem unklaren Create-Ergebnis wird derselbe Provideraufruf innerhalb seiner Idempotenzfrist wiederholt; danach gleichen eine manuelle Statusaktualisierung und der tägliche Wartungslauf ein begrenztes Erstellungszeitfenster über die Mollie-Liste und die interne Metadata-Referenz ab. Ein terminal fehlgeschlagener Refund erscheint in der minimalen Supportoberfläche und wird dort geklärt, nicht automatisch als neue Refund-Ressource wiederholt.

## Account-Löschung

Die Löschung legt vor jeder destruktiven Aktion ein dauerhaftes Löschledger an, entzieht sofort den Zugriff und wird im auslösenden HTTP-Request idempotent abgearbeitet. Nach einem Teilfehler kann Support denselben Vorgang fortsetzen. Aktive Fälle eigener Primary-Owner-Tags werden abgeschlossen, diese Tags auf `RETIRED` gesetzt, Backup-Rechte an fremden Tags entfernt und Owner- sowie Finder-Identitätsreferenzen eindeutig pseudonymisiert. Die Supabase-Identität und das lokale Benutzerkonto werden zuletzt gelöscht.
