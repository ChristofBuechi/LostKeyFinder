# Gemeinsame Übersetzungen

`packages/translations/de.json` und `en.json` enthalten die gepflegten Anwendungstexte für Frontend und Backend. Beide Dateien müssen dieselben Schlüssel und benannten Platzhalter haben. Technische Bezeichner, Logs, API-Diagnosen und benutzereigene Inhalte werden nicht übersetzt.

## Struktur und Pflege

- `common`: Navigation, Anmeldung, Konto und Finder-Einstieg.
- `tags`: Besitzeransichten, Administration, Registrierung und Historie.
- `chat`: Fundmeldungen und Chats.
- `emails`: Betreff und Nachrichtentext der Benachrichtigungen.

Neue Texte in beiden Dateien ergänzen. In E-Mails werden benannte Platzhalter wie `{chatUrl}` verwendet. Werte werden wörtlich eingesetzt; fehlende Schlüssel oder Parameter führen zu einem Fehler statt zu einer unvollständigen E-Mail. Der Katalog enthält ausschliesslich Klartext, kein HTML.

`node --test packages/translations/*.test.mjs` prüft Schlüssel, nichtleere Texte und übereinstimmende Platzhalter. Diese Prüfung ist Teil von `npm test` und `npm run verify`.

## Frontend

Transloco (`@jsverse/transloco`, stabiles Kernpaket) übernimmt Übersetzungen und Sprachwechsel im Frontend. `provideTranslations()` bindet die beiden JSON-Dateien über einen synchronen Loader ein; zusätzliche HTTP-Anfragen sind nicht nötig. Standard- und Fallback-Sprache ist Deutsch. Die Interpolationsmarker sind auf `{` und `}` eingestellt und entsprechen damit dem Backend.

Allgemeine Texte verwenden die `transloco`-Pipe, beispielsweise `{{ 'common.email' | transloco }}`. Tag- und Chat-Texte verwenden `translateSection('tags')` bzw. `translateSection('chat')`, eine typisierte Anbindung an Transloco `translateObjectSignal`. Die bisherigen `TagCopy`- und `ChatCopy`-Services entfallen. Die Typen stammen direkt aus dem JSON-Katalog; Schlüssel und Platzhalter beider Sprachen werden weiterhin im gemeinsamen Test geprüft.

Der `LanguageService` verwendet die aktive Sprache von Transloco und synchronisiert `document.title`, das HTML-Sprachattribut und den lokalen Speicher. Sprachpräferenz und Benutzerprofil bleiben Anwendungslogik; dafür werden keine zusätzlichen Transloco-Plugins eingesetzt. Die Testumgebung verwendet dieselben Provider und echten Kataloge wie die Anwendung.

Ohne Anmeldung wird die Sprache lokal gespeichert; Standard ist Deutsch. Nach Anmeldung lädt die Anwendung `users.language`. Der Sprachschalter speichert Änderungen über `PATCH /api/v1/me/language` (`{"language":"de"}` oder `{"language":"en"}`). Während Laden/Speichern sind weitere Änderungen gesperrt. Bei fehlgeschlagener Speicherung wird die vorherige Sprache wiederhergestellt und ein übersetzter Fehler angezeigt. Alte Antworten eines anderen Kontos verändern die aktuelle Ansicht nicht.

Die API erlaubt nur Änderungen an der Sprache des angemeldeten, aktiven Benutzers. Bestehende Dokumente ohne Sprachfeld werden als Deutsch gelesen; neue Benutzer erhalten ausdrücklich `language=de`. Eine Datenmigration ist nicht erforderlich.

## Backend und Deployment

Gradle übernimmt dieselben beiden Originaldateien bei `processResources` nach `translations/` im Klassenpfad. Sie liegen anschliessend im Backend-JAR. Der Docker-Build kopiert die gemeinsamen Quellen mit in seinen Build-Kontext. Das Backend benötigt weder das laufende Frontend noch einen Laufzeit-Mount. Änderungen an den Texten erfordern einen neuen Build und ein Deployment beider Anwendungen.

`Translations` lädt die Kataloge einmal beim Start. Benachrichtigungen verwenden die gespeicherte Sprache des Empfängers; bei einer unbekannten Sprache wird Deutsch verwendet. Ohne vollständige SMTP-Konfiguration bleibt der Versand weiterhin deaktiviert.

Firebase-Anmeldelinks werden von Firebase verschickt. Das Frontend übergibt die gewählte Sprache mit `auth.languageCode`; deren Vorlagen werden weiterhin von Firebase verwaltet und nicht aus unseren JSON-Dateien geladen.
