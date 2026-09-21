# Roadmap

## Reihenfolge

1. **Fundament:** Angular, NestJS, Supabase Auth, Firestore-Anbindung, OpenAPI und getrennte `dev`/`prod`-Umgebungen.
2. **Tag und Identität:** Magic-Link-Login, kostenlose UUID-v4-Tags, QR-Route und Backup-Einladungen.
3. **Fund und Chat:** Finder-Draft, geräteübergreifende Bestätigung, private Text-Chats, optionale bereinigte Bilder und Benachrichtigungen.
4. **Payment:** CHF 10.00 über die im Mollie-Live-Profil freigeschalteten Zahlungsarten, Reconciliation und Access Grant.
5. **Betrieb:** Archivierung, synchrone Account-Löschung, täglicher Wartungsaufruf, minimale Supportoberfläche, Support-Audit, Backups, Monitoring und Runbooks.

## Abnahme

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

Weitere Länder, Sprachen, Währungen, physische Tags, eine erweiterte Supportoberfläche, Infrastructure as Code und Finderlohn-Verarbeitung folgen nur nach validiertem Bedarf. Eine Finderlohn-Verarbeitung benötigt zusätzlich eine neue rechtliche und paymenttechnische Prüfung.
