# Datenschutz und Betrieb

## Datenminimierung

Der QR-Code enthält nur die öffentliche UUID-v4-URL. Öffentliche Antworten unterscheiden unbekannte, inaktive, gesperrte und stillgelegte Tags nicht. E-Mail-Adressen werden nicht zwischen Beteiligten offengelegt; freiwillige Kontaktdaten können erst nach Access Grant als normaler Chattext geteilt werden.

Die Plattform speichert keine Karten-, TWINT-Instrument-, Finder-Zahlungs- oder KYC-Daten. Bilder werden begrenzt, bereinigt und nur autorisiert ausgeliefert. Nach Annahme einer Backup-Einladung werden Einladungs-E-Mail und Normalisierungsversion entfernt.

## Aufbewahrung und Löschung

Draft-Inhalte werden nach Abschluss oder Ablauf sofort logisch entfernt; ein minimaler Status darf kurz als Tombstone bestehen bleiben. Externe Versandversuche laufen im auslösenden HTTP-Request. Ihr technischer Status darf am fachlich auslösenden Datensatz bis zur erfolgreichen Providerannahme, manuellen Klärung oder Löschung bestehen bleiben.

Die endgültigen Fristen für Archive, Nachrichten, Bilder und minimale Zahlungsnachweise sind Go-live-Entscheidungen. Bis zur rechtlichen Freigabe werden sie konfigurierbar gehalten und nicht als unbefristet oder zehnjährig festgeschrieben. Stillgelegte Tags, öffentliche IDs und für Archivberechtigungen benötigte `membershipId`-Referenzen bleiben mindestens so lange wie abhängige Archive erhalten; öffentliche IDs werden nie wiederverwendet. Backups dürfen gelöschte Daten nur innerhalb ihrer beschränkten Betriebsfrist enthalten. Ein aktuelles Löschledger bleibt mindestens über diese Frist erhalten und wird nach jedem Restore vor fachlicher Freigabe zusammen mit dem täglichen Wartungslauf angewendet.

Account-Löschung entzieht sofort fachlichen Zugriff. Eine wiederaufnehmbare Kaskade pseudonymisiert notwendige historische Referenzen mit einem eindeutigen Ersatzwert, entfernt E-Mail-Kopien und löscht Supabase- sowie lokales Benutzerkonto.

## Missbrauch und Support

Rate Limits schützen öffentliche Auflösung, Drafts, Magic Links, Tags und Nachrichten. Wiederholter Missbrauch kann reCAPTCHA Enterprise verlangen. Schlüssel werden als versionierte HMAC-Werte gespeichert; ein Rotations-Runbook wird erst vor der ersten Rotation benötigt.

Inhaltsmeldungen blenden Inhalte nicht automatisch aus. Support darf nur die gemeldete Nachricht über einen auditierten Least-Privilege-Pfad lesen. Manuelle Sperren und Refunds benötigen eine benannte Person, Pflichtgrund und Ticketreferenz; ob eine zweite Freigabe nötig ist, wird vor Go-live verbindlich entschieden. Provideränderungen gelten fachlich erst nach API-Verifikation.

Logs und Metriken enthalten nur Correlation IDs, Ereignistypen und pseudonyme IDs. Support-Lesen, Sperren, Paymentstatus, Löschung und Berechtigungsänderungen werden auditiert.
