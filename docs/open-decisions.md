> Aktueller Stand 31.10.2026: [Roadmap](roadmap.md) und [Besitzerfunktionen](owner-features.md) haben Vorrang vor überholten Fortschrittsangaben hier. Chats sind umgesetzt und bleiben kostenlos. Kontolöschung und Inhaltsmeldungen sind implementiert; Supportaktionen, Besitzerkorrekturen und Backup-Besitzer wurden zurückgestellt.

# Offene Go-live-Entscheidungen

Vor Produktivstart sind festzulegen oder zu bestätigen:

- Rechtsgrundlage, Nutzerinformation und Fristen für Chats, Bilder und Zahlungsnachweise;
- Schweizer Vertrags-, Datenschutz-, Steuer-, Beleg- und Konsumentenrecht;
- Mollie-Vertrag, Preise und im Live-Profil verfügbare Zahlungsarten;
- SMTP-Provider, Datenstandort, Bounce-Verarbeitung und Metadatenaufbewahrung;
- Support-Arbeitsanweisung und Vier-Augen-Pflicht für Sperren und Refunds;
- Budgets, Warnschwellen, Domains, Absenderdomain und Anbieterangaben.

Phase 1 bietet keine Tag-Übertragung und keine Support-Recovery für verlorenen Primary-Owner-Zugriff. Ist das nicht akzeptabel, muss diese Produktentscheidung vor Implementierung der Lösch- und Supportabläufe geändert werden.

Eine eng begrenzte Korrektur plausibler Fehlregistrierungen wird separat [als Support-Ansatz diskutiert](fehlregistrierung-support-ansatz.md): aktuelles Foto mit Supportreferenz und Rückfrage beim bisherigen Besitzer, ohne Händler-/Kaufbelegzuordnung vorauszusetzen. Der genaue Ablauf ist noch nicht beschlossen und ersetzt weder Besitzerwechsel bei Weiterverkauf noch Recovery bei verlorenem Kontozugang. Kriterien, Supportkanal, Berechtigung und Antwortfrist (Vorschlag: sieben Tage) vor Hardware-Verkauf festlegen. Ohne klärbares Ergebnis keine Neuzuordnung; Entsorgen des physischen Tags bedeutet keine automatische technische Stilllegung.

Für Tags bereits beschlossen: Eigene Tags und Hardware-Bestand bestehen nebeneinander; erste bestätigte Registrierung ohne Aktivierungscode gewinnt. Admin-Konten werden manuell über `users.isAdmin` gesetzt; Serienfreigabe erfolgt nach Produktionskontrolle vor Verkauf. Domain und Herstellerformat werden später festgelegt, die dauerhafte Domain muss jedoch vor Herstellung der Verkaufstags bestehen. Fundmeldungen und Kontaktprozess werden in einer späteren Umsetzung ergänzt und blockieren den aktuellen Tag-/Admin-Durchstich nicht.

Akzeptiert sind Scale-to-zero-Cold-Starts, manuell verwaltete Infrastruktur, bis zu 60 Minuten gültige Access Tokens nach Rechteentzug, seltene SMTP-Doppelzustellung und ein bestehender Chat-Zugang trotz Chargeback. Diese Risiken werden überwacht, aber nicht vorab mit zusätzlicher Architektur kompensiert.
