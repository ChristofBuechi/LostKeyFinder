# Roadmap

Stand: 31. Oktober 2026. Dieser Status ersetzt frühere Fortschrittsangaben der ursprünglichen Phasenpläne. Aktuelle Detailregeln: [Chat](chat-implementation.md), [Besitzerfunktionen](owner-features.md), [Tag-Historie](tag-history.md).

## Umgesetzt

- Firebase-E-Mail-Anmeldung, bestätigte Identität, serverseitige Besitzer-/Teilnehmer-/Adminrechte und geschützte Direktlinks.
- Eigene Tags, Hardware-Produktionsserien, CSV, Freigabe und Registrierung über dieselbe Scanroute, Soft Delete, Historie, nummerierte Pagination und Inline-Bezeichnungen.
- Private kostenlose Chats mit Text/Bildern, Kameraaufnahme bei Fundmeldung, Fallabschluss und wiederholte Fundzyklen; Mailversand bei konfiguriertem SMTP und separate Scheduler-Schnittstelle.
- Gemeinsame DE-/EN-Kataloge mit Transloco und lokalisierten Backend-E-Mails.
- PDF-Druckvorlagen für eigene Tags: Kreditkartengrösse und A4-Sammelbogen.
- Wiederaufnehmbare Kontolöschung mit Tag-Bereinigung und Erhalt gemeinsamer Historie.
- Inhaltsmeldungen erfassen; noch keine Supportbearbeitung.

## Vor Produktivstart klären und prüfen

- Dauerhafte QR-Domain vor Herstellung von Verkaufstags und endgültiges Herstellerformat.
- Aufbewahrung/Löschung von Chatinhalten, Bildern, Meldungen und Löschledger; Nutzerinformationen und Anbieterangaben.
- SMTP-/Absenderdomain, Scheduler, Zustellung, Backups/Restore mit Löschledger, Monitoring und Kostenalarme tatsächlich abnehmen. Implementierter Code allein bestätigt keine Cloud-Konfiguration.
- Vorgehen für liegengebliebene Löschaufträge sowie manueller Supportkanal; keine automatische Neuzuordnung aufgrund einer Meldung oder eines Fristablaufs.
- Reale Geräte-/Browserabnahme einschliesslich Kamera, E-Mail-Browserwechsel und PDF-Druckmassstab.

## Bewusst zurückgestellt

- Supportaktionen und Besitzerkorrekturen: [Vorschlag für Fehlregistrierungen](fehlregistrierung-support-ansatz.md), noch keine implementierte Funktion.
- Backup-Besitzer und Einladungen.
- Zahlungen; Chats bleiben kostenlos.
- Shop/Versand, native Apps und weitere Formate erst bei konkretem Bedarf.
