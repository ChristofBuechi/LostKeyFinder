# Fachliche Regeln

> Aktualisierung 26.10.2026: Für Fundmeldungen und Chats gilt [Chat-Umsetzung](chat-implementation.md). Kostenloses Schreiben mit Text/Bildern, feste Teilnehmerrechte, keine automatische 30-Tage-Schreibfrist; frühere widersprechende Zahlungs-/Fristenregeln sind für diesen Ausbau überholt. SMTP und Scheduler benötigen vor echtem Versand ihre Umgebungskonfiguration.
## Invarianten

- Ein selbst erzeugter Tag besitzt ab Erstellung genau einen Primary Owner. Ein vorproduzierter Tag besitzt bis zur Registrierung keinen, danach genau einen Primary Owner. Nur zugewiesene Tags können einen Verlustfall haben; höchstens einen aktiven pro Tag.
- Ein Finder besitzt je Verlustfall höchstens einen privaten Chat und sieht keine anderen Finder.
- Chat-Leserecht gilt für im Chat-Objekt gespeicherte Owner/Teilnehmer und für Admins. Tag-Besitzerwechsel ändern bestehende Chat-Teilnehmer nicht; auch ehemalige Besitzer können ihre zugeordneten Chats weiter lesen. Die Berechtigung wird nicht aus Zeiträumen abgeleitet.
- Archivierung ist endgültig. Sperren sind reversibel, reaktivieren aber keinen archivierten Fall.

## Tags und Berechtigungen

Es gibt zwei gleichberechtigte Entstehungswege: Ein angemeldeter Benutzer erzeugt einen eigenen Tag mit atomarer Besitzerzuordnung; ein Admin erzeugt vorproduzierten Bestand, den ein Käufer später registriert. Die Herkunft `SELF_CREATED` oder `PREPRODUCED` ändert den gemeinsamen Finder-Prozess nicht. QR-Downloads und spätere Druckvorlagen, etwa im Kreditkartenformat, erhalten dieselbe öffentliche Identität.

Vorproduzierte Tags gehören einer Serie an und sind zunächst `PREPARED`. Erst `RELEASED` erlaubt die Registrierung eines vorhandenen, unzugewiesenen und nicht gesperrten oder stillgelegten Tags. Selbsterstellung benötigt keine Produktionsserie oder Freigabe. Scan und Registrierung erzeugen niemals neue Tags für unbekannte IDs. Die verbindliche Zuordnung geschieht explizit und atomar; konkurrierende Benutzer können nicht beide Besitzer werden. Hardware-Tags werden ohne zusätzlichen Aktivierungscode registriert: Die erste explizite Zuordnung durch einen Benutzer mit bestätigter E-Mail gewinnt. Die öffentliche UUID-v4 wird kryptografisch sicher zufällig erzeugt; das Risiko einer fremden Erstregistrierung mit einem vorab kopierten QR-Code ist akzeptiert.

Die gemeinsame Scan-Route bietet bei einem registrierbaren Tag Registrierung und bei einem zugewiesenen aktiven Tag Fundmeldung an. Nicht existente, nicht freigegebene, inaktive, gesperrte und stillgelegte Tags bleiben öffentlich neutral. Bereits zugewiesene Tags werden durch Logout, Deaktivierung oder Account-Löschung nicht wieder frei. Besitzer können die Zuordnung eines aktiven/inaktiven Hardware-Tags ausdrücklich lösen; danach ist derselbe produzierte Tag wieder registrierbar. Alte Registrierungsentwürfe werden dabei ungültig.

| Tagstatus | Wirkung |
| --- | --- |
| `ACTIVE` | Bei vorhandenem Besitzer Fundmeldung möglich; ohne Besitzer nur bei Produktionsfreigabe registrierbar. |
| `INACTIVE` | Durch berechtigten Owner reaktivierbar. |
| `BLOCKED` | Reversible Support-Sperre ohne Endnutzerzugriff. |
| `RETIRED` | Endzustand, auch beim Soft Delete eines selbst erstellten Tags (`deletedAt`); keine Reaktivierung oder neuen Fälle. Das Tag-Dokument bleibt erhalten. |

Der Primary Owner kann höchstens drei offene Einladungen oder aktive Backup Owner verwalten. Die Annahme verlangt die exakt passende normalisierte, verifizierte E-Mail der authentifizierten Identität. Jede Annahme erhält eine neue `membershipId`. Archivzugriff eines Backup Owners verlangt dieselbe noch aktive Mitgliedschaft, die beim Fall bestand. Entfernen und erneutes Einladen stellt daher keinen früheren Zugriff wieder her.

Adminrechte werden manuell im lokalen `users`-Dokument als `isAdmin: true` gesetzt. Fehlendes Feld bedeutet `false`. Jede Admin-Aktion verlangt das anhand der Firebase-UID geladene aktive Konto und diese Berechtigung; deren Entzug wirkt beim nächsten Request. Admins sehen alle erzeugten Tags, dürfen alle Tag-Typen unabhängig vom Besitzer per Soft Delete stilllegen und dürfen über die Tag-Historie sämtliche zugehörigen Chats lesen, einschliesslich früherer Besitzerperioden. Diese Chat-Funktion folgt später; die verbindlichen Regeln stehen in [Tag-Historie](tag-history.md). Details und Abnahmen: [Tag-Produktion und Registrierung](tag-produktion-und-registrierung-plan.md).

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

Die Löschung legt vor jeder destruktiven Aktion ein dauerhaftes Löschledger an, entzieht sofort den Zugriff und wird im auslösenden HTTP-Request idempotent abgearbeitet. Nach einem Teilfehler kann Support denselben Vorgang fortsetzen. Aktive Fälle eigener Primary-Owner-Tags werden abgeschlossen, diese Tags auf `RETIRED` gesetzt, Backup-Rechte an fremden Tags entfernt und Owner- sowie Finder-Identitätsreferenzen eindeutig pseudonymisiert. Die Firebase-Identität und das lokale Benutzerkonto werden zuletzt gelöscht.
