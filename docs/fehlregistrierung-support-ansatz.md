# Vorschlag: Fehlregistrierungen durch Support klären

Stand: 25. Oktober 2026. Diskussionsgrundlage, noch keine beschlossene oder implementierte Korrekturfunktion.

## Grenze

Hardware-Tags werden ohne Aktivierungscode registriert. Die erste explizite Zuordnung mit verifizierter E-Mail gewinnt. Support soll eine irrtümliche oder missbräuchliche Erstzuordnung nach Prüfung korrigieren können. Das ist eine eng begrenzte Ausnahme, kein Weiterverkaufsprozess und keine Kontowiederherstellung bei verlorenem E-Mail-Zugang.

## Vorgeschlagener Ablauf

1. **Meldung:** Die Tag-Seite nennt einen noch festzulegenden Supportkanal für „Tag bereits registriert“. Sie gibt keine Daten des bisherigen Besitzers preis. Anfangs ist kein eigenes Ticket-/Uploadsystem nötig.
2. **Zielkonto:** Antragsteller meldet sich regulär an, sodass ein aktives Konto mit bestätigter E-Mail existiert. Eine im Ticket genannte Adresse allein berechtigt nicht zur Übernahme.
3. **Prüfung:** Support prüft Tag-ID, Kauf-/Ausgabebeleg und den nachvollziehbaren Bezug zum konkreten Tag; bei Bedarf Angaben von Händler/Hersteller und Foto von Tag/Verpackung. Kenntnis der UUID oder ein QR-Foto allein reichen nicht. Auch ein allgemeiner Kaufbeleg ohne Bezug zum Tag ist nicht automatisch ausreichend.
4. **Konfliktklärung:** Support prüft Registrierungszeitpunkt und bestehende Zuordnung. Soweit erreichbar, bisherigen Benutzer um Stellungnahme bitten; dessen Kontaktdaten bleiben gegenüber dem Antragsteller verborgen. Fehlende Antwort allein beweist keinen Anspruch. Frist und Kommunikationsvorlagen sind noch festzulegen. Der Plan autorisiert keinen tatsächlichen Nachrichtenversand.
5. **Optionale Sperre:** Bei plausiblem Missbrauch kann berechtigter Support den Tag mit Grund und Ticketreferenz reversibel auf `BLOCKED` setzen. Eine blosse Anfrage löst keine automatische Sperre aus. Die Besitzerreferenz bleibt erhalten.
6. **Entscheidung:** Nur bei belegter Fehlzuordnung gezielt auf das bestätigte Zielkonto korrigieren. Bei unklaren oder widersprüchlichen Nachweisen keine Übertragung; ein Ersatztag ist gegebenenfalls die praktikablere Lösung.
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

## Noch zu besprechen

- Welche Nachweise kann Händler/Produktion einem einzelnen Tag tatsächlich zuordnen?
- Genügt ein berechtigter Supportbenutzer oder ist eine zweite Freigabe gewünscht?
- Welcher Supportkanal und welche Prüf-/Antwortfrist werden verwendet?
- Wann wird ein Ersatztag angeboten, falls die Zuordnung nicht zuverlässig geklärt werden kann?

## Abnahme einer späteren Implementierung

- Normale Benutzer und reine Bestandsadmins können keine Besitzerkorrektur auslösen.
- Anfragen oder Kenntnis der UUID entziehen niemandem automatisch Rechte.
- Inaktives/falsches Zielkonto, geänderter Ausgangszustand und bereits benutzte Tags werden abgelehnt.
- Wiederholung nach Timeout erzeugt dieselbe Korrektur und genau einen Audit-Eintrag.
- Kein Dritter kann während der Korrektur den Tag neu registrieren.
- Frühere Besitzer und alte Entwürfe können keine Besitzerrechte mehr ausüben; historische Inhalte werden nicht übertragen.
