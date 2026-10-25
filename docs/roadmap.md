# Roadmap

## Reihenfolge

1. **Fundament:** Angular, Kotlin/Spring Boot, Firebase Authentication, Firestore-Anbindung, OpenAPI und getrennte `dev`/`prod`-Umgebungen.
2. **Tag und Identität:** Magic-Link-Login, eigene kostenlose UUID-v4-Tags mit sofortiger Zuordnung, Admin-Produktionsserien, Registrierung vorproduzierter Tags, gemeinsame QR-Route, globale Admin-Tagübersicht und Backup-Einladungen. Adminrechte werden manuell im lokalen Benutzerkonto gesetzt. Umsetzungsreihenfolge und Abnahmen: [Tag-Plan](tag-produktion-und-registrierung-plan.md).
3. **Fund und Chat:** Finder-Draft, geräteübergreifende Bestätigung, private Text-Chats, optionale bereinigte Bilder und Benachrichtigungen.
4. **Payment:** CHF 10.00 über die im Mollie-Live-Profil freigeschalteten Zahlungsarten, Reconciliation und Access Grant.
5. **Betrieb:** Archivierung, synchrone Account-Löschung, täglicher Wartungsaufruf, minimale Supportoberfläche, Support-Audit, Backups, Monitoring und Runbooks.

Aktuell wird Punkt 2 ohne den späteren Finder-/Kontaktprozess vorbereitet; Backup-Einladungen folgen ebenfalls der weiteren Roadmap. Serienfreigabe erfolgt explizit nach Produktionskontrolle vor Verkauf. Domain und finales Herstellerformat werden später entschieden; vor Herstellung der Verkaufstags muss die dauerhafte Domain bestehen. [Fehlregistrierungen](fehlregistrierung-support-ansatz.md) sind als Support-Vorschlag ausgearbeitet und noch abzustimmen.

## Abnahme

- Selbsterstellung und Registrierung vorproduzierter Tags bestehen nebeneinander; unbekannte IDs erzeugen beim Scan keinen Tag.
- Parallele Registrierung kann nur einen Besitzer zuordnen; Wiederholungen erzeugen weder zweite Tags noch zweite Serien.
- Manuell berechtigte Admins sehen nach normaler Anmeldung alle Tags mit Filtern und Pagination; Rollenentzug wirkt beim nächsten Admin-Request.

- Parallele Finder erzeugen getrennte Chats, aber höchstens einen aktiven Fall.
- Wiederholungen erzeugen weder doppelte Chats noch doppelte Zahlungen oder interne Benachrichtigungen.
- Falsche Identität, `flowId`, Statustoken oder Ressourcenzuordnung legt keine Daten offen.
- Owner sehen ohne Grant nur Anzahl und Zeitpunkte; Inhalte öffnen nur durch den atomaren Access Grant.
- Entfernte Backup Owner verlieren sofort Zugriff; erneutes Einladen stellt alte Archive nicht wieder her.
- Bilder werden begrenzt und bereinigt, Listen laden keine Base64-Daten.
- Deutsche und englische Kernabläufe erfüllen WCAG 2.2 AA und funktionieren per Tastatur und Screenreader.
- Logs enthalten keine Tokens, E-Mails, Chattexte, Bilder oder Checkout-URLs.
- Persistenztests laufen gegen eine echte Firestore-Dev-Datenbank.
- Account-Löschung kann nach Fehlern fortgesetzt werden und entzieht Zugriff sofort.

## Später

Formatierte PDF-Druckvorlagen für bestehende eigene Tags, zunächst beispielsweise im Kreditkartenformat, folgen als Ausbau. Nachdruck verändert keine Tag-ID und erzeugt keinen neuen Tag. Weitere Länder, Sprachen, Währungen, Versand-/Shopabwicklung, eine erweiterte Supportoberfläche, Infrastructure as Code und Finderlohn-Verarbeitung folgen nur nach validiertem Bedarf. Eine Finderlohn-Verarbeitung benötigt zusätzlich eine neue rechtliche und paymenttechnische Prüfung.
