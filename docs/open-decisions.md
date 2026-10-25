# Offene Go-live-Entscheidungen

Vor Produktivstart sind festzulegen oder zu bestätigen:

- Rechtsgrundlage, Nutzerinformation und Fristen für Chats, Bilder und Zahlungsnachweise;
- Schweizer Vertrags-, Datenschutz-, Steuer-, Beleg- und Konsumentenrecht;
- Mollie-Vertrag, Preise und im Live-Profil verfügbare Zahlungsarten;
- SMTP-Provider, Datenstandort, Bounce-Verarbeitung und Metadatenaufbewahrung;
- Support-Arbeitsanweisung und Vier-Augen-Pflicht für Sperren und Refunds;
- Budgets, Warnschwellen, Domains, Absenderdomain und Anbieterangaben.

Phase 1 bietet keine Tag-Übertragung und keine Support-Recovery für verlorenen Primary-Owner-Zugriff. Ist das nicht akzeptabel, muss diese Produktentscheidung vor Implementierung der Lösch- und Supportabläufe geändert werden.

Eine eng begrenzte Korrektur nachgewiesener Fehlregistrierungen wird separat [als Support-Ansatz diskutiert](fehlregistrierung-support-ansatz.md). Sie ist noch nicht beschlossen und ersetzt weder Besitzerwechsel bei Weiterverkauf noch Recovery bei verlorenem Kontozugang. Nachweiskriterien, Supportkanal, Berechtigung, Fristen und Ersatzprozess vor Hardware-Verkauf festlegen.

Für Tags bereits beschlossen: Eigene Tags und Hardware-Bestand bestehen nebeneinander; erste bestätigte Registrierung ohne Aktivierungscode gewinnt. Admin-Konten werden manuell über `users.isAdmin` gesetzt; Serienfreigabe erfolgt nach Produktionskontrolle vor Verkauf. Domain und Herstellerformat werden später festgelegt, die dauerhafte Domain muss jedoch vor Herstellung der Verkaufstags bestehen. Fundmeldungen und Kontaktprozess werden in einer späteren Umsetzung ergänzt und blockieren den aktuellen Tag-/Admin-Durchstich nicht.

Akzeptiert sind Scale-to-zero-Cold-Starts, manuell verwaltete Infrastruktur, bis zu 60 Minuten gültige Access Tokens nach Rechteentzug, seltene SMTP-Doppelzustellung und ein bestehender Chat-Zugang trotz Chargeback. Diese Risiken werden überwacht, aber nicht vorab mit zusätzlicher Architektur kompensiert.
