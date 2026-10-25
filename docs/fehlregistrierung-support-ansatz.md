# Vorschlag: Fehlregistrierungen durch Support klären

Stand: 25. Oktober 2026. Diskussionsgrundlage, noch keine beschlossene oder implementierte Korrekturfunktion.

## Grenze

Hardware-Tags werden ohne Aktivierungscode registriert. Die erste explizite Zuordnung mit verifizierter E-Mail gewinnt. Support soll eine irrtümliche oder missbräuchliche Erstzuordnung nach Prüfung korrigieren können. Das ist eine eng begrenzte Ausnahme, kein Weiterverkaufsprozess und keine Kontowiederherstellung bei verlorenem E-Mail-Zugang.

## Vorgeschlagener Ablauf

1. **Meldung:** Die Tag-Seite nennt einen noch festzulegenden Supportkanal für „Tag bereits registriert“. Sie gibt keine Daten des bisherigen Besitzers preis. Anfangs ist kein eigenes Ticket-/Uploadsystem nötig.
2. **Zielkonto:** Antragsteller meldet sich regulär an, sodass ein aktives Konto mit bestätigter E-Mail existiert. Eine im Ticket genannte Adresse allein berechtigt nicht zur Übernahme.
3. **Foto prüfen:** Keine Zuordnung von Kauf-/Ausgabebelegen zu einzelnen Tags voraussetzen. Stattdessen aktuelles Foto des physischen Tags mit lesbarer ID bzw. QR und einer vom Support vorgegebenen Vorgangsreferenz auf einem danebenliegenden Zettel anfordern; bei Bedarf Vorder-/Rückseite. Ein QR-Screenshot oder eine bekannte UUID allein genügt nicht. Das Foto dient als pragmatischer Hinweis auf aktuellen Zugriff auf den Tag, beweist aber weder Kauf noch berechtigten Besitz; auch ein Finder könnte den Tag fotografieren.
4. **Rückfrage:** Support prüft Registrierungszeitpunkt und bestehende Zuordnung und fragt den bisherigen Benutzer über dessen bestätigte Kontaktadresse an. Dessen Kontaktdaten bleiben gegenüber dem Antragsteller verborgen. Vorschlag für die Antwortfrist: sieben Tage. Zustimmung erlaubt die weitere Prüfung; bei Widerspruch oder widersprüchlichen Angaben keine einfache Neuzuordnung. Ohne Antwort darf Support anhand des aktuellen Fotos und der Gesamtumstände entscheiden; Schweigen allein genügt nicht und löst keine automatische Übertragung aus. Frist und genaue Kriterien sind noch zu bestätigen. Der Plan autorisiert keinen tatsächlichen Nachrichtenversand.
5. **Optionale Sperre:** Bei plausiblem Missbrauch kann berechtigter Support den Tag mit Grund und Ticketreferenz reversibel auf `BLOCKED` setzen. Eine blosse Anfrage löst keine automatische Sperre aus. Die Besitzerreferenz bleibt erhalten.
6. **Entscheidung:** Bei nach Supportprüfung plausibler Fehlzuordnung gezielt auf das bestätigte Zielkonto korrigieren. Bei nicht auflösbarem Konflikt keine Übertragung. Der Antragsteller kann dann einen anderen Tag verwenden und den physischen Tag entsorgen; ein Ersatz ist keine automatisch zugesagte Leistung. Keine zusätzliche Hardware-Bescheinigung oder Händlerdatenbank verlangen.
7. **Abschluss:** Ergebnis und Grund dokumentieren, Beteiligte über den festgelegten Supportkanal informieren. Tag niemals zwischenzeitlich öffentlich unzugewiesen setzen. Unabhängige Sperrgründe bleiben wirksam; nicht pauschal entsperren.

## Technische Leitplanken einer späteren Funktion

- Globale Bestandsadministration verleiht keine automatische Korrekturberechtigung. Die bestehend geplante explizite Supportberechtigung verwenden; eine Person darf beide Berechtigungen besitzen.
- Dedizierter Anwendungsfall mit Pflichtgrund und Ticketreferenz; keine freie Datenbankbearbeitung als regulärer Supportprozess und kein öffentliches Zurücksetzen.
- Aktuellen Besitzer bzw. Versionsstand und aktives Zielkonto vor dem Schreiben erneut prüfen. Zwischenzeitliche Änderungen erzeugen einen Konflikt.
- Besitzeränderung, Korrekturabschluss und Audit atomar speichern. Stabile Korrektur-ID macht Wiederholungen idempotent. Alte offene Registrierungsentwürfe entwerten.
- Öffentliche UUID, QR-Link und Serienzuordnung bleiben gleich. Frühere Benutzer verlieren bei der nächsten Ressourcenprüfung Zugriff. Keine alten Backup-Rechte übernehmen.
- Erster Korrekturumfang nur für unbenutzte Fehlregistrierungen: keine Memberships/Einladungen, Verlustfälle, Chats oder Zahlungen. Bei solchen Referenzen ablehnen und gesondert klären; historische Inhalte oder Rechte niemals dem neuen Konto übertragen. Vor dem späteren Finder-/Kontaktrelease weiter ausarbeiten.
- `RETIRED`-Tags bleiben endgültig stillgelegt. Keine Benutzeridentität simulieren und keine Konten zusammenführen.
- Audit mit Akteur, Tag, interner alter/neuer Benutzerreferenz, Korrektur-ID, Zeit, Grund und Ticketreferenz. Belege bleiben im geschützten Supportvorgang mit festzulegender Aufbewahrung, nicht in Anwendungslogs oder Audit-Inhalten.

## Einordnung in die Umsetzung

Für die aktuelle Tag-/Admin-Vorbereitung ist dies ein Vorschlag. Die verbindliche Regel gegen automatische Überschreibung bleibt bestehen. Eine Korrekturfunktion ist separat zu beschliessen und umzusetzen. Bis dahin keine bereits funktionierende Korrekturmöglichkeit in der UI versprechen.

Vor Verkauf freigegebener Hardware-Tags müssen Supportkanal, berechtigte Personen, Nachweiskriterien, Fristen, Streitfallbehandlung und Aufbewahrung feststehen. Ein geregelter Ersatzprozess kann eine Zwischenlösung sein.

Entsorgen des physischen Tags ist von einer technischen Stilllegung zu unterscheiden. Eine unbestätigte Anfrage oder das Ausbleiben einer Antwort darf einem möglicherweise berechtigten bisherigen Besitzer nicht automatisch seinen Tag sperren oder endgültig stilllegen. Eine technische Stilllegung benötigt eine gesondert begründete Supportentscheidung oder den vorgesehenen Besitzerprozess. Die öffentliche UUID wird niemals wiederverwendet.

## Noch zu besprechen

- Foto mit frischer Supportreferenz als Prüfgrundlage; keine Händler-/Belegzuordnung erforderlich. Genaue Kriterien für eine manuelle Entscheidung ohne Antwort festlegen.
- Genügt ein berechtigter Supportbenutzer oder ist eine zweite Freigabe gewünscht?
- Welcher Supportkanal wird verwendet, und übernehmen wir die vorgeschlagene Antwortfrist von sieben Tagen?
- Gibt es bei ungeklärtem Konflikt ein Ersatzangebot? Andernfalls keine Neuzuordnung; Antragsteller kann den Tag entsorgen und einen anderen verwenden.

## Abnahme einer späteren Implementierung

- Normale Benutzer und reine Bestandsadmins können keine Besitzerkorrektur auslösen.
- Anfragen oder Kenntnis der UUID entziehen niemandem automatisch Rechte.
- Foto allein und Fristablauf führen zu keiner automatischen Übertragung oder Stilllegung; bei Widerspruch greift keine einfache Neuzuordnung.
- Inaktives/falsches Zielkonto, geänderter Ausgangszustand und bereits benutzte Tags werden abgelehnt.
- Wiederholung nach Timeout erzeugt dieselbe Korrektur und genau einen Audit-Eintrag.
- Kein Dritter kann während der Korrektur den Tag neu registrieren.
- Frühere Besitzer und alte Entwürfe können keine Besitzerrechte mehr ausüben; historische Inhalte werden nicht übertragen.
