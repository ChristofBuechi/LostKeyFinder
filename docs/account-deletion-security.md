# Sicherheitsprüfung der Kontolöschung

Geltungsbereich: Benutzer-API, E-Mail-Bestätigung, Browserfortsetzung und Übergabe an den technischen Firebase-Worker in PR #17. Keine Aussage über eine bereits eingerichtete Cloud-Berechtigung oder einen bereits aktiven Scheduler.

## Ergebnis und Änderungen

Im bisherigen Zielidentitätsabgleich wurde kein Fremdkonto-Bypass gefunden: UID und lokale Benutzerreferenz kamen aus der geprüften Sitzung, der Worker prüfte bereits deren Hash-Zuordnung. Eine frische Sitzung allein konnte den Auftrag jedoch unmittelbar starten. Die nun geforderte zusätzliche E-Mail-Freigabe wird vor jeder neuen Annahme verbindlich geprüft.

1. Die erste Ansicht fragt ausdrücklich nach dem Löschwunsch. `POST /api/v1/me/deletion-confirmations` verlangt eine frische Anmeldung und lädt die aktuelle bestätigte E-Mail serverseitig aus Firebase. Übergebene E-Mail-Adressen, Benutzer-IDs, Firebase-UIDs und Hostheader bestimmen weder Empfänger noch Zielkonto.
2. Der Server erzeugt 32 kryptografisch zufällige Bytes als URL-sicheren Einmallink. Im Benutzer liegt genau ein begrenztes `deletionConfirmation`-Objekt mit Tokenhash, tokengebundenem E-Mail-Prüfwert, Ablaufzeit und Verbrauchszeit. Kein Klartexttoken und keine weitere Bestätigungs-Collection. Ein neuer Link ersetzt den vorherigen; gültig sind maximal 15 Minuten.
3. SMTP-Annahme wird separat bestätigt. Konto und Tags bleiben bis zur Freigabe unverändert. Fehlender SMTP-/Worker-/Origin-Konfiguration oder fehlgeschlagener Versand führen zu Fehlern; ein fehlgeschlagener Versand entwertet nur den gerade erzeugten Link, ohne einen zwischenzeitlich neueren Link zu überschreiben. Maximal drei Versandversuche pro Konto/Stunde.
4. Die E-Mail enthält einen Link zur fest konfigurierten HTTPS-Origin. Das Geheimnis liegt im URL-Fragment, erscheint damit nicht im HTTP-Ziel/Referrer und wird vor Konfigurationsabruf, Firebase-Initialisierung und Routing aus der Browseradresse entfernt. Es bleibt ausschliesslich im Arbeitsspeicher, nicht in Browserstorage, Routerparametern oder der Seite. Nach Reload ist die ursprüngliche E-Mail erneut zu öffnen.
5. Die Linkseite benötigt dieselbe angemeldete Firebase-Identität und einen ausdrücklichen Bestätigungsklick. Ein GET, Mail-Linkscanner, Linkvorschau oder anonymes Öffnen startet keine Löschung. In einem neuen Browser zuerst mit genau diesem Konto anmelden, danach zum geöffneten Bestätigungstab zurückkehren oder den E-Mail-Link nochmals öffnen. Die angezeigte eigene E-Mail wird bei einem Firebase-Kontowechsel neu geladen.
6. `DELETE /api/v1/me` benötigt den Token im JSON-Body. Es gibt keinen UID-/Benutzer-ID-Parameter zur Zielwahl. Tokenprüfung erfolgt im ausschliesslich durch die Sitzung geladenen Benutzer-Dokument; ein anderes Konto kann den Link auch mit gleicher E-Mail oder `isAdmin=true` nicht verwenden. Die aktuelle verifizierte E-Mail muss weiterhin zum ausgestellten Link passen.
7. Innerhalb derselben Datenbanktransaktion werden aktueller Token, Ablauf, unveränderliche UID-Zuordnung und Kontostatus erneut geprüft, der Token verbraucht, das Konto gesperrt und `accountDeletions.confirmedAt` gespeichert. Parallele Bestätigungen erzeugen genau einen Auftrag. Ein Wiederholen mit bereits verbrauchtem Token kann nur denselben bereits angenommenen eigenen Auftrag quittieren; es löst keine zweite Aktion aus. Nach vollständiger Löschung verhindern Tombstone und fehlendes Benutzerkonto weitere Nutzung.
8. Der Worker wählt ausschliesslich Aufträge mit `confirmedAt`. Auch der Prozessor verweigert ungeprüfte Aufträge vor jeder Bereinigung. Leases, atomare Fortschritte, Hash-Abgleich der Firebase-UID und technische IAM-Löschung bleiben erhalten. Anwendungsadmins erhalten dadurch keinen Benutzerlösch-Endpunkt für andere Konten.

## Ältere offene Aufträge

Aufträge ohne `confirmedAt` pausieren nach diesem Sicherheitsupdate. Existiert das ursprüngliche gesperrte Konto noch, kann dessen Besitzer nach frischer Anmeldung über die Löschansicht eine Bestätigungsmail anfordern und genau diesen Auftrag freigeben. Bereits ausgeführte frühere Bereinigungsschritte werden dabei nicht rückgängig gemacht. Historische Inkonsistenzen, etwa eine bereits extern gelöschte Identität ohne lokalen Abschluss, benötigen eine geprüfte Operatorentscheidung; kein automatisches Erraten einer Zielidentität oder Reaktivieren eines Kontos.

## Nachweise

HTTP-Tests prüfen fehlende Sitzung, fehlenden/falschen Token, Datenbankhash als vermeintlichen Token, Fremdkonto mit gleicher E-Mail, Adminrolle, manipulierte Ziel-IDs/Empfänger/Hostheader, Fristablauf, Resend, E-Mail-Wechsel, SMTP-Ausfall, Versandlimit, Wiederholung und pausierte Altaufträge. Das Konto bleibt ohne gültige Freigabe aktiv; ohne Freigabe entsteht kein verarbeitbarer Auftrag. Die Konto-API gibt keine Bestätigungshashes aus.

Frontendtests prüfen explizite Nachfrage, getrennten Mailversand, keinerlei automatischen DELETE beim Linköffnen, Token ausschliesslich im Request-Body, URL-Bereinigung, fehlende Sitzung, Kontowechsel und abgewiesene Bestätigung. Die echte Firestore-Integration bestätigt parallele Freigaben desselben Tokens transaktional und durchläuft danach die begrenzte Hintergrundverarbeitung. Mail und Firebase-Löschung verwenden ausschliesslich lokale Testadapter; keine bestehenden Firebase-Konten werden als Test gelöscht.

Das Modell folgt den übertragbaren Token-Prinzipien aus [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html): Zufälligkeit, Kontobindung, kurze Frist, sichere Speicherung, begrenzter Versand und keine Kontosperre vor gültiger Freigabe. Es ist keine Zertifizierung oder Garantie für beliebige Betreiberkonfigurationen.

## Vertrauensgrenzen und Betrieb

Die technische Firebase-Berechtigung kann projektweit Konten löschen und gehört ausschliesslich zur Backend-Dienstidentität. Die App lässt diese Identität nur bestätigte eigene Aufträge abarbeiten. Kompromittierte Runtime-Credentials oder direkte privilegierte Datenbankänderungen liegen ausserhalb einer normalen Benutzersitzung und müssen durch IAM und Betriebszugriffe geschützt werden.

SMTP, kanonische HTTPS-Origin, Scheduler und IAM müssen je Umgebung eingerichtet und abgenommen werden. Ohne SMTP bleibt die Anwendung nutzbar, neue Kontolöschungen werden aber sicher abgelehnt. Es gibt keine automatische Löschfreigabe bei Mailfehlern, Tokenablauf oder Zeitablauf eines offenen Auftrags. Niemals einen TTL-Index auf das eingebettete Benutzerfeld setzen: TTL würde das ganze Benutzer-Dokument löschen. Die Frist wird bei jedem Verbrauch fachlich geprüft; es bleibt höchstens ein gehashteter Bestätigungsvorgang im Konto.
