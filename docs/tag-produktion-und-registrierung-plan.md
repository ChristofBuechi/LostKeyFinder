# Plan: eigene Tags, vorproduzierte Tags und gemeinsamer Scan-Einstieg

Stand: 25. Oktober 2026. Grundlage: aktueller Repository-Code und bestehende Fach-/Implementierungsdokumentation nach PR #5. Dieses Dokument beschreibt die vorgeschlagene Umsetzung; es ändert noch keine Anwendung und legt offene Produktentscheidungen nicht eigenmächtig fest.

Der vereinbarte Tag-/Admin-Umfang ist im PR #6 implementiert. Fähigkeiten, Verifikation, manuelle Admin-Einrichtung und spätere Produktionsvoraussetzungen stehen im [Umsetzungsstand](tag-implementation-status.md). Fundmeldung/Kontakt, formatierte Druckvorlagen und allgemeine Besitzerübertragung bleiben spätere eigene Schritte.

## 1. Zielbild

### Aktuell vereinbarter Umsetzungsumfang

Jetzt werden eigene Tags, vorproduzierter Bestand, Registrierung und Admin-Übersicht vorbereitet. Fundmeldungen, Besitzerbenachrichtigungen und Kontakt-/Chatprozess folgen in einer späteren Umsetzung. Die Zuordnung soll bereits die spätere Auswahl des Finder-Ablaufs erlauben; die aktuelle UI darf noch keine funktionierende Meldung oder Kontaktaufnahme behaupten.

Die Serienfreigabe ist beschlossen: Ein Admin gibt nach der Produktionskontrolle und vor dem Verkauf explizit zur Registrierung frei. Admin-Konten werden manuell gesetzt. Dauerhafte Domain und physisches Produktionsformat werden später festgelegt; die Domain muss vor Herstellung der Verkaufstags feststehen. Ein generisches Serienmanifest kann vorher vorbereitet werden, ohne das finale Herstellerformat festzulegen.

### Gemeinsamer Tag-Einstieg

Ein Admin legt beispielsweise 100 Tags als Produktionsserie an. Jeder Tag existiert vor Herstellung und Verkauf in der Datenbank, besitzt eine dauerhafte öffentliche ID und ist zunächst keinem Benutzer zugewiesen.

**Die Erstellung eigener Tags durch Benutzer bleibt ebenfalls Bestandteil des Produkts.** Ein angemeldeter Benutzer kann einen neuen Tag erzeugen, der unmittelbar seinem Konto zugeordnet wird. Dieser Weg ergänzt die Registrierung vorproduzierter Hardware-Tags. Später kann der Benutzer für seine Tags Druckvorlagen erzeugen, beispielsweise im Kreditkartenformat.

| Entstehungsweg | Erzeugung und Zuordnung |
| --- | --- |
| Eigener Tag | Benutzer erzeugt nach Anmeldung einen neuen Tag; Erstellung und Besitzerzuordnung erfolgen atomar. QR-Code steht danach zur Verfügung. |
| Vorproduzierter Hardware-Tag | Admin erzeugt zunächst unzugewiesenen Bestand; Käufer registriert einen vorhandenen, freigegebenen Tag. |

Beide Wege verwenden dasselbe Tag-Modell, dieselben öffentlichen Links, dieselbe Verwaltung und denselben Finder-Prozess. Die Herkunft ist keine zusätzliche Auswahl beim Scannen.

Alle Benutzer verwenden denselben Einstieg: QR-Code scannen, Tag-Link öffnen oder Tag-ID eingeben. Erst das Backend entscheidet anhand des aktuellen Zustands, welchen nächsten Schritt die Anwendung anbietet:

| Zustand | Verhalten |
| --- | --- |
| Existiert und ist zur Registrierung freigegeben, noch ohne Besitzer | Erstregistrierung anbieten; E-Mail bestätigen bzw. vorhandenes Konto verwenden; Tag verbindlich zuordnen. |
| Bereits zugewiesen und aktiv | Fundmeldung und Kontaktaufnahme mit dem Besitzer starten. |
| Bereits zugewiesen, angemeldeter Benutzer ist selbst Besitzer | Eigene Tag-Verwaltung anzeigen; keine Fundmeldung an sich selbst. |
| ID existiert nicht, Tag noch nicht freigegeben, inaktiv, gesperrt oder endgültig stillgelegt | Neutrale Nicht-verfügbar-Seite mit Hilfe anbieten; keine Registrierung und keine Kontaktdaten preisgeben. |

„Unbekannt“ bedeutet im gewünschten Registrierungsablauf also **vorhanden, aber noch nicht zugewiesen**. Eine frei erfundene ID darf keinen neuen Tag erzeugen.

Die Startseite benötigt dafür keine Auswahl „registrieren oder gefunden“. Der gemeinsame Bereich sollte neutral „Tag scannen oder ID eingeben“ heissen. Die E-Mail-Anmeldung ohne Tag bleibt für bestehende Benutzer erreichbar.

## 2. Ausgangslage bei Planerstellung nach PR #5

Vorhanden sind Firebase-E-Mail-Link-Anmeldung, Callback, Erfolgsseite nach Linkversand, lokales Benutzerkonto, geschützte Kontoseite sowie QR-/ID-Eingabe mit Navigation nach `/f/{publicId}`. Firebase-Identitäten werden über die Firebase-UID einem lokalen Benutzer zugeordnet.

Bei Planerstellung fehlten Tag-Datenmodell und Tag-API, tatsächliche öffentliche Auflösung, Besitzerzuordnung, Produktionsserien und Admin-Oberfläche; diese sind inzwischen im oben verlinkten Umsetzungsstand enthalten. Fundmeldungen, private Chats und Besitzerbenachrichtigungen bleiben spätere Funktionen.

Der bestehende Plan muss fachlich korrigiert werden:

- `docs/implementation-plan.md`, Phase 3: Geplante Selbsterstellung durch Besitzer erhalten und um Admin-Bestand und Registrierung vorproduzierter Tags erweitern.
- `docs/key-tag-lifecycle.md`: Ein selbst erzeugter Tag hat ab Erstellung genau einen Primary Owner; ein vorproduzierter Tag hat vor Registrierung keinen und danach genau einen.
- `docs/login-finder-entry.md`: Öffentliche Links bleiben kein Besitznachweis, führen aber zustandsabhängig zur Registrierung.
- Architektur, Übersicht, Roadmap und Datenschutzdokumentation an Bestand, Admin-Zugriff, Registrierungsentwürfe und Produktionsexporte anpassen.

Der Einstieg und die Identität sind vorhanden; Zustandsmodell, Produktionsprozess und verbindliche Zuordnung fehlen. Die folgenden Schritte definieren die dafür nötigen Regeln und Abnahmen.

## 3. Beschlossen: Registrierung ohne zusätzlichen Aktivierungscode

**Die erste erfolgreiche, explizit bestätigte Registrierung mit verifizierter E-Mail erhält den vorhandenen, freigegebenen und noch unzugewiesenen Hardware-Tag.** Ein separater Aktivierungscode oder Kaufnachweis ist nicht vorgesehen. Selbst erzeugte Tags sind bereits bei ihrer Erstellung zugewiesen.

Die öffentliche ID wird mit einem kryptografisch sicheren Zufallszahlengenerator als UUID-v4 erzeugt. Der 122-Bit-Zufallsraum verhindert praktisches Erraten und systematisches Durchprobieren. Keine fortlaufenden IDs oder vorhersagbaren Zufallsquellen verwenden. Rate Limits für öffentliche Auflösung, Registrierung und Versand bleiben als Missbrauchs- und Kostenschutz bestehen.

Die bestätigte E-Mail belegt die Kontrolle über das Postfach, nicht den Kauf. Eine UUID schützt nicht gegen das Kopieren eines bereits sichtbaren QR-Codes. Das Risiko, dass jemand mit einer solchen Kopie vor dem Käufer registriert, wurde ausdrücklich akzeptiert. Es blockiert weder Umsetzung noch Hardware-Produktion.

Ein bereits zugewiesener Tag kann über die Erstregistrierung nicht übernommen werden. Fehlzuordnungen werden durch Support nach gesonderter Prüfung geklärt; es gibt keine öffentliche Überschreibfunktion. Das Modell enthält keine Codefelder, Codeprüfungen oder geheimen Codeexporte. Ein späterer zusätzlicher Aktivierungsnachweis wäre eine neue Produktentscheidung.

## 4. Fachmodell und unveränderliche Regeln

Die Anwendung bleibt ein modularer Kotlin-/Spring-Boot-Monolith mit Angular, Firebase Authentication und der bestehenden Firestore-Datenbank mit MongoDB-Kompatibilität. Keine zusätzlichen Dienste, Queues oder generischen Berechtigungssysteme sind dafür nötig.

### Tag

Vorgeschlagene Felder:

- interne ID, unveränderliche zufällige `publicId` (weiter UUID-v4), Herkunft `SELF_CREATED` oder `PREPRODUCED`;
- `batchId` und fortlaufende Position nur für vorproduzierte Tags; bei selbst erzeugten Tags entfallen beide;
- `inventoryStatus`: `PREPARED` oder `RELEASED` nur für vorproduzierten Bestand; selbst erzeugte Tags benötigen keine Produktionsfreigabe;
- `primaryOwnerUserId`: bei Selbsterstellung sofort gesetzt, bei vorproduzierten Tags bis zur Registrierung leer;
- Erstellungs-Idempotenzschlüssel für selbst erzeugte Tags, an den erzeugenden Benutzer gebunden;
- bestehender fachlicher Status: `ACTIVE`, `INACTIVE`, `BLOCKED`, `RETIRED`;
- `registeredAt`, optionaler privater Anzeigename, Erstellungs-/Änderungszeit;

Bestand/Freigabe und Besitzerzuordnung sind verschiedene Sachverhalte: Ein hergestellter Tag kann bereits freigegeben sein und trotzdem noch keinen Besitzer haben. Registrierbar ist er nur, wenn er freigegeben, unzugewiesen und nicht gesperrt oder stillgelegt ist. `ACTIVE` allein bedeutet nicht, dass ein Finder bereits einen Besitzer erreichen kann.

Regeln:

1. Admin-Aktionen erzeugen unzugewiesenen Produktionsbestand. Authentifizierte Benutzer dürfen über eine gesonderte Erstellungsaktion eigene, unmittelbar zugewiesene Tags erzeugen. Öffentliche Auflösung und Registrierung bestehender Tags legen niemals Tags an.
2. Öffentliche IDs sind eindeutig, unvorhersagbar und werden niemals wiederverwendet. Produktionsnummern dürfen fortlaufend sein, öffentliche IDs nicht.
3. Registrierung wechselt atomar von „ohne Besitzer“ zu „genau dieser Besitzer“.
4. Zwei gleichzeitige Registrierungen können nicht beide gewinnen. Ein Wiederholungsaufruf desselben Besitzers liefert denselben Erfolg; ein anderer Benutzer darf nicht überschreiben.
5. Anmeldung, blosses Öffnen eines Links oder ein GET-Request ordnen keinen Tag zu. Der Benutzer bestätigt die Registrierung explizit.
6. Ein angefangener E-Mail-Ablauf reserviert keinen Tag dauerhaft. Erst der erfolgreiche Abschluss entscheidet.
7. Deaktivierung, Logout oder Account-Löschung machen einen Tag nicht wieder frei. Bei Account-Löschung bleibt die geplante Stilllegung `RETIRED` bestehen.
8. Besitzerwechsel und Weiterverkauf sind spätere eigene Prozesse mit Besitzprüfung; keine Wiederverwendung der Erstregistrierung.
9. Selbsterstellung und Besitzerzuordnung erfolgen in einer Transaktion. Derselbe benutzerbezogene Idempotenzschlüssel erzeugt bei Wiederholung denselben Tag; ein fremdes Konto erhält darüber keinen Zugriff.
10. Download, erneuter Druck und Wechsel einer Druckvorlage verändern weder Tag-ID noch Besitzer. Mehrere Ausdrucke desselben Tags sind Kopien derselben Identität und teilen sich Verwaltung und Verlustfall. Für unabhängig verwaltete Gegenstände sind unterschiedliche Tags zu erzeugen.

### Eigene Tags und spätere Druckvorlagen

In „Meine Tags“ bleibt die Aktion „Neuen Tag erstellen“ erhalten bzw. wird wie ursprünglich geplant implementiert. Nach bestätigter Anmeldung erzeugt sie einen eigenen aktiven Tag und zeigt dessen QR-Code, öffentliche ID und private Verwaltungsdaten. Mengen-/Kostenlimits verhindern missbräuchliche Massenerstellung; sie ersetzen keine Admin-Produktionsserie.

Der erste Umfang enthält die Selbsterstellung und den QR-Download. Als späterer Ausbau folgt „Druckvorlage herunterladen“ pro eigenem Tag, etwa als PDF im Kreditkartenformat. Das Format ist eine Darstellung des bestehenden Tags und keine neue Tag-Art oder neue Identität. Dieselbe autorisierte Druckfunktion soll auch für bereits registrierte Hardware-Tags verwendbar sein.

Vor der Druckfunktion festlegen: konkrete Abmessungen (für Kreditkartenformat beispielsweise 85,60 × 53,98 mm), Vorder-/Rückseite, Beschnitt und Sicherheitsabstand, Beschriftung, Sprache, Mindestgrösse und Ruhezone des QR-Codes sowie eine bei 100 % Skalierung druckbare Ausgabe. PDF-/QR-Erzeugung verwendet dieselbe kanonische öffentliche URL wie der Produktionsexport; private Kontodaten gehören nicht auf die Vorlage. Druckqualität und Scannbarkeit werden an echten Ausdrucken geprüft. Ein allgemeiner Layout-Editor ist dafür zunächst nicht erforderlich.

### Produktionsserie

`tagBatches`: ID, gewünschte Stückzahl, Ersteller, Zeit, Erstellungs-Idempotenzschlüssel und Status des Produktionsvorgangs. Für den ersten Umfang maximal 100 Tags pro Anlagevorgang; grössere Serien erst nach belegtem Bedarf.

### Registrierungsentwurf

`tagRegistrationDrafts`: zufällige `flowId`, Tag-Referenz, serverseitig normalisierte Ziel-E-Mail, Ablaufzeit, Status und nach Abschluss Benutzerreferenz/Zeitpunkt. Der Entwurf speichert keinen Auth-Link und keinen Firebase-Token. Er ist an eine verifizierte Identität mit genau dieser Zieladresse gebunden, erteilt selbst aber keine Rechte.

Empfohlene Frist zunächst vier Stunden, serverseitig geprüft und konfigurierbar. Ablaufende Entwürfe geben keinen bereits vergebenen Tag frei. Nicht mehr benötigte E-Mail-Daten zeitnah entfernen; Bereinigung in den bestehenden geplanten Wartungsprozess aufnehmen.

## 5. Registrierung über E-Mail und Browserwechsel

1. Scan oder Eingabe führt wie bisher zu `/f/{publicId}`.
2. Backend löst den Tag auf und liefert ausschliesslich die erlaubte nächste Aktion.
3. Bei freiem Tag: Seite erklärt „Diesen Tag registrieren“. Benutzer gibt seine E-Mail ein. Bei gültiger bestehender Sitzung kann er sein Konto direkt verwenden oder bewusst wechseln.
4. Backend prüft Tag, Freigabe und fehlende Besitzerzuordnung. Für unangemeldete Benutzer legt es einen kurzlebigen, an Tag und E-Mail gebundenen Entwurf an. Keine Besitzerzuordnung zu diesem Zeitpunkt.
5. Der vorhandene Firebase-Linkversand erhält einen fest erlaubten Continue-Link mit der `flowId`. Nach angenommenem Versand folgt die vorhandene Erfolgsseite: Browser kann geschlossen werden. Versandfehler bleiben sichtbar, erneuter Versand ist begrenzt möglich.
6. Im Browser aus der E-Mail: Callback übernimmt Firebase-Anmeldedaten und die getrennt validierte `flowId` zunächst in den Speicher und entfernt sie aus der Adresszeile. Heute verwirft der Callback alle zusätzlichen Zustandsdaten; er muss gezielt erweitert werden.
7. Bei anderem Browser/Gerät wird die E-Mail erneut abgefragt. Die Fortsetzung darf nicht von LocalStorage oder einem Tab im ursprünglichen Browser abhängen. Die E-Mail wird nicht in die Continue-URL geschrieben.
8. Nach Firebase-Anmeldung lädt die Anwendung den Entwurf über einen geschützten Endpunkt. Backend prüft bestätigte E-Mail, UID-Zuordnung, lokalen Benutzerstatus und Frist. Neue Benutzer erhalten das vorhandene lokale Benutzerkonto; bestehende verwenden dasselbe Konto.
9. Eine geschützte Bestätigungsseite unter beispielsweise `/tag-registrations/{flowId}` zeigt den konkreten Tag und das verwendete Konto. Die nicht geheime Vorgangs-ID erlaubt dort Reloads; alle Inhalte werden erneut autorisiert vom Backend geladen. „Tag registrieren“ sendet die verbindliche Mutation. Vorher noch keine Zuordnung, damit Mail-Link-Vorschauen und unbeabsichtigtes Öffnen keine Registrierung auslösen.
10. Backend prüft den Zustand erneut und ordnet Tag und Entwurf in einer Transaktion zu. Falls zwischenzeitlich vergeben: verständlicher Konflikt, keine automatische Überschreibung und keine automatische Fundmeldung.
11. Erfolg führt zur eigenen Tag-Detailseite und der Tag erscheint in „Meine Tags“.

Bei bereits angemeldeten Benutzern entfällt ein unnötiger neuer E-Mail-Link; dieselben Backend-Regeln gelten weiterhin. Veraltete oder fehlende E-Mail-Verifikation muss vor der Zuordnung erneuert werden.

Die `flowId` dient nur zum Wiederfinden des Vorgangs. Sie erlaubt weder fremde Daten einzusehen noch einen Tag zu übernehmen. Keine frei wählbaren externen Redirect-Ziele einführen.

Wird der Callback während der Firebase-Einlösung unterbrochen, muss eine sichere Wiederaufnahme oder ein erneuter Linkversand angeboten werden. Auth-Codes dafür nicht dauerhaft speichern. Ein erfolgreich eingelöster Link wird nicht erneut verwendet; die Bestätigungsseite arbeitet anschliessend mit der Sitzung. Die tatsächliche Zustandsübergabe des installierten Firebase-SDK zuerst in einem kleinen Dev-Durchstich auf zwei Browserprofilen verifizieren.

Firebase unterstützt Zustandsübergabe über die Continue-URL und verlangt beim Abschluss die passende E-Mail; für andere Geräte muss diese erneut eingegeben werden. Sie darf dafür nicht aus URL-Parametern übernommen werden. Quellen: [E-Mail-Link-Anmeldung](https://firebase.google.com/docs/auth/web/email-link-auth), [Zustand in E-Mail-Aktionen](https://firebase.google.com/docs/auth/web/passing-state-in-email-actions).

## 6. Gemeinsame Auflösung und Fundmeldung

Vorgeschlagener öffentlicher Endpunkt: `GET /api/v1/public/tags/{publicId}`. Minimale Antwort, etwa `REGISTER`, `REPORT_FOUND` oder `UNAVAILABLE`; keine E-Mail, Benutzer-ID, Besitzerzahl oder privaten Tag-Bezeichnungen.

Damit bleibt der Einstieg einheitlich, während die Folgeseite den passenden Ablauf zeigt. Das Frontend muss den nächsten Schritt darstellen, darf ihn aber niemals selbst aus der ID oder aus Browserdaten ableiten. Jede Mutation prüft den echten Zustand erneut.

Die Unterscheidung `REGISTER`/`REPORT_FOUND` macht öffentlich erkennbar, ob ein Tag bereits zugewiesen ist. Das ist eine unvermeidbare Folge des gewünschten automatischen Ablaufs und eine bewusste Änderung der bisherigen Planung. Nicht vorhandene, nicht freigegebene, inaktive, gesperrte und stillgelegte Tags bleiben untereinander neutral.

Für einen zugewiesenen aktiven Tag wird der geplante Finder-Prozess implementiert: Meldungsentwurf mit Text und E-Mail, Bestätigung, idempotente Erzeugung von Fall/privatem Finder-Chat und Besitzerbenachrichtigung. Die bestehend geplanten Zahlungs- und Chatfreigaberegeln gelten weiter. Ein Scan allein verschickt keine Nachricht und legt keinen Verlustfall an.

Die neue Registrierung darf nicht als fertiggestellter Kontaktprozess gelten. Der Finder-Durchstich ist ausdrücklich ein späterer Umsetzungsschritt und blockiert die aktuelle Tag-/Admin-Umsetzung nicht. Bis dahin zeigt die Folgeseite zugewiesener fremder Tags einen ehrlichen Hinweis, dass Fundmeldung und Kontaktaufnahme noch nicht verfügbar sind. Bilder, Backup Owner und Zahlungen werden entsprechend der bestehenden Roadmap ergänzt.

Für Fehlregistrierungen liegt ein gesonderter [Support-Vorschlag](fehlregistrierung-support-ansatz.md) vor: aktuelles Foto des physischen Tags mit frischer Supportreferenz prüfen, beim bisherigen Besitzer rückfragen und nach manueller Entscheidung gegebenenfalls gezielt korrigieren. Eine Händler-/Kaufbelegzuordnung wird nicht vorausgesetzt. Ohne klärbares Ergebnis keine Neuzuordnung; der Antragsteller kann den Tag entsorgen und einen anderen verwenden. Foto oder Fristablauf allein lösen keine automatische Übertragung/Stilllegung aus. Kriterien und Korrekturberechtigung sind noch zu besprechen; es handelt sich nicht um eine bereits implementierte Übertragungsfunktion.

## 7. Admin und Hardware-Produktion

### Klassifizierung und manuelle Vergabe

Die Admin-Berechtigung wird im lokalen `users`-Dokument als `isAdmin: true` gespeichert. Standard ist `false`; auch ein fehlendes Feld bedeutet `false`, damit bestehende Konten ohne Migration normale Benutzer bleiben. Massgebend ist die Verknüpfung `users.firebaseUserId` mit der verifizierten Firebase-UID der Sitzung. Die E-Mail ist die Anmeldemethode und keine eigenständige Rollenregel.

Manuelles Vorgehen je Umgebung:

1. Die vorgesehene Person meldet sich einmal über ihren E-Mail-Link an und öffnet ihr Konto, sodass das lokale Benutzerkonto angelegt ist.
2. Eine Person mit berechtigtem Datenbankzugriff identifiziert dieses `users`-Dokument anhand der überprüften Firebase-UID und setzt `isAdmin` auf `true`; `updatedAt` entsprechend aktualisieren. Keine neue Firebase-Identität oder zweite Benutzerzeile anlegen.
3. Nach erneutem Laden der Kontodaten erscheint die Admin-Navigation. Eine erneute Anmeldung ist dafür technisch nicht erforderlich.
4. Zum Entziehen der Berechtigung `isAdmin` auf `false` setzen. Der nächste Admin-Request muss bereits abgewiesen werden, auch wenn der Firebase-Token weiterhin gültig ist.

Eine Oberfläche zur Rollenvergabe ist zunächst nicht nötig. Normale Profiländerungen dürfen `isAdmin` niemals schreiben. Die Konto-Provisionierung setzt den Standard ausschliesslich bei Neuanlage und überschreibt bei späteren Logins keine manuell gesetzte Berechtigung. Firebase Custom Claims oder im Token gespeicherte Admin-Rollen sind für diesen Weg nicht notwendig. Manuelle Vergaben/Entzüge im Betriebsprotokoll festhalten; sie erzeugen nicht automatisch ein Anwendungs-Audit, weil sie ausserhalb der API erfolgen.

Bei jedem Admin-Aufruf liest das Backend das lokale Konto und verlangt sowohl `status == ACTIVE` als auch `isAdmin == true`. Das Frontend bekommt über `GET /api/v1/me` ein nur lesbares `isAdmin` für Navigation und Darstellung. Ein ausgeblendeter Menüpunkt oder ein Angular-Route-Guard ersetzt die Backend-Prüfung nicht. Die spätere Chat-Umsetzung gibt Admins Lesezugriff auf sämtliche Chats eines Tags über dessen Historie; normale Chat- und Supportberechtigungen bleiben separat. Siehe [Tag-Historie](tag-history.md).

### Übersicht nach der E-Mail-Anmeldung

Bei einer normalen Anmeldung ohne laufenden Tag-/Finder-Vorgang führt der Admin standardmässig nach `/admin/tags`. Bei einer konkreten Registrierung oder Fundmeldung wird der begonnene Ablauf fortgesetzt; „Administration“ bleibt von dort erreichbar. Die eigene Kontoseite und die normale Tag-Erstellung bleiben auch für Admins verfügbar.

Die Übersicht umfasst **alle erzeugten Tags der jeweiligen Umgebung**, unabhängig davon, ob sie selbst erstellt oder durch eine Produktionsserie erzeugt wurden und ob sie schon zugewiesen sind. Die eigene Besitzerliste bleibt eine separate Ansicht.

- Zusammenfassung: Gesamtzahl, unzugewiesen, zugewiesen sowie gesperrt/stillgelegt. Produktionsfreigabe separat anzeigen; überlappende Zählungen klar beschriften.
- Tabelle: öffentliche Tag-ID, Herkunft, Produktionsserie falls vorhanden, Freigabe falls relevant, Besitzerzuordnung (ohne Besitzer oder interne Besitzerreferenz), fachlicher Status, Erstellungs- und Registrierungszeitpunkt.
- Filter: Herkunft, Serie, zugewiesen/unzugewiesen, Produktionsfreigabe und Tagstatus; Suche zunächst nach exakter öffentlicher ID.
- Stabile Sortierung und serverseitige Cursor-Pagination; keine vollständige Bestandsabfrage im Browser. Filterbare Gesamtzahlen serverseitig berechnen.
- Detailansicht mit QR-Link, Metadaten und zulässigen Admin-Aktionen; Zugang zu Serienerstellung, Serienübersicht und Produktionsexport.
- Leere Listen, Ladefehler und fehlende Berechtigung verständlich darstellen. Private Nachrichten und unnötige persönliche Daten erscheinen nicht in der Tag-Tabelle.

Minimale geschützte Oberfläche:

1. Admin meldet sich mit Firebase an; Backend prüft die manuell gesetzte lokale `isAdmin`-Berechtigung anhand der UID und den aktiven Kontostatus. Alle Tags sind in der beschriebenen Übersicht erreichbar. Admin- und Supportrechte bleiben getrennt.
2. „Serie erstellen“ mit Anzahl, optionaler Bezeichnung und stabiler Idempotenz-ID.
3. Backend erzeugt genau die gewünschte Anzahl einmalig. Serienmetadaten und Tags atomar anlegen; Transaktionsgrenzen für 100 Tags gegen echte Firestore-Dev-Datenbank testen. Ein Retry nach Timeout liefert dieselbe Serie.
4. Serienliste und Detail zeigen Soll-/Ist-Anzahl, Freigabe sowie registrierte und unzugewiesene Tags; keine freie Datenbankbearbeitung.
5. Export liefert UTF-8-CSV mit öffentlicher ID, dauerhafter URL und Produktionsreferenz; bei Bedarf lokal generiertes QR-SVG/PDF für den Hersteller. IDs und QR-Ziele werden automatisiert auf Eindeutigkeit und Übereinstimmung geprüft.
6. Produktionsexporte nur autorisiert ausgeben und sicher an den Hersteller übergeben. Sie enthalten ausschliesslich öffentliche Tag-IDs, URLs und Produktionsreferenzen; zusätzliche Aktivierungsgeheimnisse werden nicht erzeugt. Ein erneuter Export verwendet denselben Bestand und erzeugt keine neuen Tags.
7. Nach Qualitätskontrolle explizit zur Registrierung freigeben. Die Freigabe erzeugt keine Besitzer. Änderungen dürfen zugewiesene Tags nicht zurücksetzen.
8. Admin-Aktionen wie Erstellung, Export, Freigabe und Sperre mit Akteur, Zeitpunkt und Aktion auditieren; keine E-Mail-Inhalte oder Auth-Tokens im Audit.

**Vor dem physischen Druck verbindlich:** produktive dauerhafte Domain und `/f/{publicId}` festlegen. Keine `web.app`-Dev-Links auf Verkaufstags drucken. Dev-/Testserien sind vom Produktionsbestand getrennt und dürfen nicht ausgeliefert werden. Bei einem späteren Domainwechsel müssen bestehende gedruckte URLs dauerhaft weiterleiten.

## 8. API, Datenbank und Zugriffsschutz

Vorgeschlagene fachliche Endpunkte; Namen im OpenAPI-Vertrag abschliessend vereinheitlichen:

| Endpunkt | Zweck und Schutz |
| --- | --- |
| `GET /api/v1/me` (erweitert) | Eigene Kontodaten einschliesslich nur lesbarem `isAdmin`; authentifiziert. |
| `GET /api/v1/public/tags/{publicId}` | Minimale nächste Aktion; öffentlich, begrenzt. |
| `POST /api/v1/public/tags/{publicId}/registration-drafts` | Entwurf anlegen, Tag/Code prüfen; begrenzt, keine Reservierung. |
| `GET /api/v1/tag-registrations/{flowId}` | Fortsetzung nur mit passender bestätigter Identität. |
| `POST /api/v1/tag-registrations/{flowId}/complete` | Explizite, atomare Zuordnung; passende Identität erforderlich. |
| `POST /api/v1/tags/{publicId}/register` | Direkte Registrierung mit bestätigter Sitzung, denselben fachlichen Prüfungen. |
| `POST /api/v1/tags` | Eigenen Tag erstellen und atomar dem angemeldeten Benutzer zuordnen; benutzerbezogener Idempotenzschlüssel und Mengenlimits. |
| `GET /api/v1/tags`, `GET /api/v1/tags/{tagId}` | Eigene Tags und Details, ressourcenbezogene Autorisierung. |
| Später: `GET /api/v1/tags/{tagId}/printout?format=card` | Autorisierter PDF-Download für bestehenden eigenen Tag; Layoutparameter serverseitig begrenzen. |
| `POST /api/v1/admin/tag-batches` | Idempotente Serienerstellung, nur Admin. |
| `GET /api/v1/admin/tags` | Alle Tags mit Filtern, stabiler Sortierung und Cursor-Pagination; nur Admin. |
| `GET /api/v1/admin/tags/summary` | Zusammenfassende Bestandszahlen mit passenden Filtern; nur Admin. |
| `GET /api/v1/admin/tags/{tagId}` | Tag-Metadaten für die Administration, auch bei fremder Besitzerzuordnung; nur Admin, kein Chatinhalt. |
| `GET /api/v1/admin/tag-batches[/{batchId}]` | Serienliste und Detail, nur Admin. |
| `GET /api/v1/admin/tag-batches/{batchId}/export` | Autorisierter und auditierter Produktionsexport. |
| `POST /api/v1/admin/tag-batches/{batchId}/release` | Explizite Freigabe, idempotent. |

Ein gemeinsamer Registrierungsservice erzwingt die Regeln für direkte und E-Mail-gestützte Registrierung. Der direkte Weg wird nicht als zweite Fachlogik implementiert. Support-Sperren aus dem bisherigen Plan bleiben eigene autorisierte Aktionen.

Notwendige Indizes: `tags.publicId` eindeutig, `(batchId, position)` eindeutig nur für Produktions-Tags, benutzerbezogener Erstellungs-Idempotenzschlüssel eindeutig nur für selbst erzeugte Tags, Owner plus Listen-/Zeitfelder, Serien-Idempotenzschlüssel eindeutig, Entwurfs-ID eindeutig und Ablaufindex für Wartung. Für die Admin-Liste die tatsächlich angebotenen Filter-/Sortierkombinationen indexieren und gegen Firestore prüfen; stabiler Cursor über Zeit plus ID. Optionale Produktionsfelder dürfen bei selbst erzeugten Tags keine Eindeutigkeitskonflikte auslösen; die verwendeten Sparse-Indizes gegen die echte Firestore-Datenbank prüfen. Weitere Indizes nur für tatsächlich verwendete Queries.

Die Zuordnung verwendet einen bedingten Schreibzugriff auf denselben Tag mit Prüfung von leerem Besitzer und zulässigem Status. Bei Entwürfen werden Verbrauch des Entwurfs und Zuordnung gemeinsam transaktional gespeichert. Keine E-Mail- oder Firebase-Aufrufe innerhalb einer Datenbanktransaktion. Konkurrenz, Retry und verlorene Antworten gegen die echte Ziel-Datenbank beweisen; MongoDB-Kompatibilität nicht mit vollständiger Gleichheit zu lokalem MongoDB verwechseln. [Firestore mit MongoDB-Kompatibilität](https://firebase.google.com/docs/firestore/enterprise/mongodb-compatibility-overview)

Bestehende Security-Konfiguration gezielt erweitern: nur die vorgesehenen öffentlichen Pfade freigeben; alle Registrierungsabschlüsse und Admin-Mutationen geschützt halten. Kein pauschales Freischalten von `/api/v1/**`.

Weitere notwendige Massnahmen: Eingabevalidierung, Grössenlimits, begrenzte öffentliche Auflösung/Entwurfserstellung, Kosten- und Versandquoten, nicht cachebare zustandsabhängige Antworten, keine sensiblen Daten in Logs. Rate-Limit-Prüfwerte benötigen eine kurze Aufbewahrung. Produktionsdateien und Callback-Seiten nicht durch Analyse-/Drittanbieter-Skripte erfassen lassen.

## 9. Umsetzung als überprüfbare Schritte

### Schritt 1 – Regeln und Produktionsformat festlegen

- Beschlossene Erstzuordnung ohne Aktivierungscode und Freigabe nach Produktionskontrolle im Vertrag festhalten. Produktionsdomain und Herstellerformat als spätere Produktionsvoraussetzungen führen.
- Statusmodell, Besitzregeln, öffentliche Antworten und API-Vertrag dokumentieren.
- Bestehende Fachpläne konsistent aktualisieren; Selbsterstellung als gleichberechtigten Weg ausdrücklich erhalten und spätere Druckvorlagen in der Roadmap aufnehmen.

Abnahme: Beispiele „neu“, „zugewiesen“, „fremde ID“, „gesperrt“ und „parallele Registrierung“ sind eindeutig beschrieben. Domain und finales Herstellerformat blockieren nicht den Implementierungsstart; ein zusätzlicher Aktivierungscode ist nicht vorgesehen.

### Schritt 2 – Bestand, Admin-Berechtigung und Serien-API

- Tag-/Serienmodell, Indizes und Admin-Prüfung implementieren.
- Lokales `isAdmin` mit sicherem Standard, nur lesbarer Ausgabe in `/me` und manueller Vergabeanleitung ergänzen; erneute Anmeldung darf eine Vergabe nicht überschreiben.
- Authentifizierte Selbsterstellung mit atomarer Besitzerzuordnung, Idempotenz und Mengenlimits implementieren; sie benötigt keine Produktionsserie oder Admin-Freigabe.
- Idempotente Erstellung, Liste, Export und Freigabe implementieren; danach minimale Admin-UI.
- Audit und Produktionsdatei-Verifikation ergänzen.
- Admin-Tagübersicht für beide Entstehungswege mit Zusammenfassung, Filtern, Pagination und Login-Ziel implementieren.

Abnahme: Wiederholte Anlage derselben 100er-Serie erzeugt insgesamt exakt 100 Tags; alle unzugewiesen. Nicht-Admins haben keinen Zugriff auf Serienaktionen oder globale Taglisten, noch nicht freigegebene Tags sind nicht registrierbar. Benutzer können unabhängig davon eigene Tags erzeugen, die sofort nur ihnen gehören; Retry erzeugt keine Duplikate. Ein manuell berechtigter Admin sieht nach normaler E-Mail-Anmeldung alle Tags mit korrekten Filtern und kann auch bei grossem Bestand seitenweise navigieren. Entzug in der Datenbank sperrt den nächsten Admin-Aufruf.

### Schritt 3 – Öffentliche Auflösung und gemeinsame Scan-Seite

- Platzhalter durch API-basierte Auflösung ersetzen.
- Neutralen Text auf Startseite und Scan-Komponente ergänzen.
- Zustandsabhängige Folgeseiten, Lade-/Fehlerzustände und Besitzer-Sonderfall implementieren.
- Client aus OpenAPI generieren.

Abnahme: Kamera, Bildauswahl, manuelle ID und direkter QR-Link führen für denselben Tag zum identischen Ablauf. Bei API-Ausfall erscheint ein wiederholbarer Fehler, keine fälschliche Registrierungsfreigabe.

### Schritt 4 – Verbindliche Registrierung und E-Mail-Fortsetzung

- Gemeinsamen Registrierungsservice, Entwürfe und atomaren Abschluss implementieren.
- Firebase-Continue-State und Callback sicher erweitern; bestehende Anmeldung ohne Tag weiter unterstützen.
- Erfolgsseite nach Versand beibehalten; Bestätigungs- und Tag-Detailseite ergänzen.
- Konto-Tagliste und Behandlung von Konflikten, Ablauf und Wiederholungen umsetzen.
- „Neuen Tag erstellen“, QR-Download und gemeinsame Verwaltung für beide Entstehungswege anbieten.

Abnahme: Neue und bestehende Konten können freigegebene Tags registrieren. Wechsel vom Scan-Browser zum E-Mail-Browser funktioniert. Kein fremder Benutzer kann einen Entwurf abschliessen oder eine bestehende Zuordnung überschreiben.

### Späterer Umsetzungsschritt – Finder-Durchstich anschliessen

- Noch fehlende Finder-Entwürfe, bestätigte Meldung, Fall/Chat-Anlage und Benachrichtigung entsprechend dem bestehenden Plan implementieren.
- Registrierte Tags leiten dahin weiter; unzugewiesene erzeugen keine Meldung an einen nicht existierenden Besitzer.
- Erst echte Erfolge als gespeichert/versandt anzeigen; bestehende Chatfreigaberegeln einhalten.

Abnahme: Derselbe physische QR-Code führt vor Registrierung zum Registrieren, danach für einen Finder zur tatsächlichen Fundmeldung. Besitzer wird nach bestätigter Meldung korrekt benachrichtigt.

### Späterer Ausbau – Druckvorlagen für eigene Tags

- Formatierte PDF-Ausgabe für bestehende eigene Tags ergänzen, zunächst eine feste Vorlage im Kreditkartenformat.
- QR-/URL-Erzeugung mit dem vorhandenen Download und Produktionsexport teilen; Drucklayout getrennt vom fachlichen Tag-Modell halten.
- Weitere Druckformate erst bei Bedarf ergänzen; keine zusätzliche Besitzerregistrierung für Ausdrucke.

Abnahme: PDF hat die definierten physischen Abmessungen, enthält den unveränderten öffentlichen Link und ist bei 100 % Skalierung auf echten Ausdrucken scanbar. Erneuter Download erzeugt keinen neuen Tag. Dieser Ausbau blockiert den ersten Release der beiden Erstellungswege nicht.

### Schritt 5 – Dev-Abnahme; Produktion separat vorbereiten

- Build, Lint, Frontend-/Backendtests, OpenAPI- und reale Firestore-Integrationstests in CI ausführen.
- Kleine getrennte Dev-Serie anlegen und mit gedruckten Mustern auf echten Mobilgeräten testen.
- Dev deployen und Browserwechsel, QR-Druckqualität und Mailzustellung prüfen.
- Produktive Domain, Admin-Zugriff, Quoten, Exportzugang, Aufbewahrung und Hilfe bei Fehlregistrierung prüfen.
- Produktion separat: Nach Festlegung von Domain, Herstellerformat und Supportverfahren produktive Serie erzeugen, Druckdaten übergeben und Freigabe nach Qualitätskontrolle vor Verkauf ausführen. Ein Dev-Release der Tag-/Admin-Funktionen benötigt den Finder-/Kontaktprozess noch nicht.

## 10. Zwingende Tests

- Nicht existierende IDs erzeugen durch Scan oder Registrierungsversuch weder Tag noch Zuordnung; nur explizite autorisierte Erstellung darf eine neue ID erzeugen.
- Selbsterstellung ordnet den Tag atomar dem richtigen Benutzer zu, benötigt keine Serie/Freigabe und funktioniert parallel für verschiedene Benutzer.
- Wiederholte Selbsterstellung mit demselben benutzerbezogenen Schlüssel erzeugt genau einen Tag; Limits und fremde Ressourcen-IDs werden geprüft.
- Selbst erzeugte und registrierte Hardware-Tags verwenden dieselbe Besitzerverwaltung. Bis zum späteren Finder-Release keine Meldung oder Kontaktaufnahme behaupten; danach denselben Finder-Prozess für beide prüfen.
- Nicht freigegebene, gesperrte und stillgelegte Tags können nicht registriert werden.
- Zwei unterschiedliche Benutzer registrieren gleichzeitig: genau ein Besitzer, der andere erhält einen Konflikt.
- Derselbe Benutzer wiederholt nach Timeout: derselbe Erfolg, keine zweite Zuordnung.
- Sperre oder fremde Zuordnung zwischen Scan und Abschluss wird berücksichtigt.
- Unbestätigte Identität, falsche E-Mail, falsche UID und deaktiviertes lokales Konto werden abgelehnt.
- Entwurfs-ID allein sowie fremder, abgelaufener oder verbrauchter Entwurf gewähren keine Rechte.
- Callback übernimmt nur erlaubten Zustand; keine offenen Redirects und keine E-Mail-/Codeübernahme aus URL.
- Registrierung funktioniert in anderem Browser und nach Reload der Bestätigungsseite mit gültiger Sitzung; kein Zugriff auf den alten Tab erforderlich.
- Versandfehler erzeugt keinen Erfolg und keinen Besitzer; Erfolgsseite löst bei Reload keinen zweiten Versand aus.
- Bereits angemeldeter Besitzer öffnet seinen Tag; andere Benutzer können ihn nicht übernehmen.
- Admin-Erstellung und Freigabe sind bei Retry idempotent; Exporte enthalten exakt die gespeicherten öffentlichen IDs und korrekte QR-Ziele.
- Fehlendes oder falsches `isAdmin`, gesperrtes Konto und manipulierte Client-Rolle erlauben keinen Admin-Zugriff. Profil-Requests können die Berechtigung nicht ändern; erneutes Provisionieren erhält eine manuelle Vergabe.
- Manuelle Vergabe wird nach Laden von `/me` sichtbar; manueller Entzug wirkt beim nächsten Admin-Request ohne Token-Erneuerung. Direkte fremde Tag-IDs bleiben für normale Benutzer gesperrt.
- Admin-Liste enthält selbst erzeugte und vorproduzierte Tags, zugewiesene und unzugewiesene. Filter, Zählungen und Cursor-Pagination liefern konsistente Ergebnisse ohne doppelte Einträge; normale Besitzerlisten bleiben auf berechtigte Tags begrenzt.
- Normale Admin-Anmeldung öffnet die globale Übersicht; Anmeldung innerhalb eines Tag-/Finder-Vorgangs erhält dessen Fortsetzung.
- Registrierung verlangt keinen zusätzlichen Code. UUID-v4-Erzeugung verwendet eine kryptografisch sichere Zufallsquelle; bereits zugewiesene Tags bleiben gegen erneute Zuordnung geschützt.
- Deutsch/Englisch, Tastaturbedienung, Screenreader-Status, kleine Bildschirme und Kamera ohne Berechtigung prüfen.
- Account-Löschung macht alte Tags nicht erneut registrierbar.
- Beim späteren Druckausbau: physische PDF-Abmessungen, QR-Ruhezone, echte Scanbarkeit, Autorisierung und unveränderte Tag-Identität bei wiederholtem Druck prüfen.

## 11. Spätere Produktionsentscheidungen und Support-Diskussion

1. **Produktion:** dauerhafte Domain, QR-/Beschriftungsformat und sichere Übergabe von Druckdaten.
2. **Freigabezeitpunkt (beschlossen):** explizit durch Admin nach Produktionskontrolle und vor Verkauf.
3. **Betrieb und Support:** erste Admin-Konten benennen und nach erster Anmeldung manuell über `users.isAdmin` berechtigen; Vorgehen bei Fehlregistrierung oder defektem Tag festlegen. Die manuelle Rollenvergabe ist beschlossen; eine Admin-Rollenverwaltung in der UI ist zunächst nicht vorgesehen. Keine automatische Besitzerüberschreibung.

Domain und Produktionsformat werden später festgelegt; die Produktionsdomain muss vor Herstellung der Verkaufstags feststehen. Der [Support-Ansatz](fehlregistrierung-support-ansatz.md) ist zu besprechen. Admin-Vergabe, Serienfreigabe sowie Registrierung ohne Aktivierungscode und das damit verbundene Risiko sind beschlossen. Fundmeldung/Kontakt werden später umgesetzt und blockieren die aktuelle Tag-/Admin-Umsetzung nicht.
