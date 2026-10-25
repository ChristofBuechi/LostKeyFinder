# Lösungsübersicht

## Zweck

Lost Key Finder verbindet Finder und Owner verlorener Gegenstände über QR-Tags, ohne Kontaktdaten automatisch offenzulegen. Die Plattform verarbeitet nur eine Servicegebühr; Rückgabe und Finderlohn vereinbaren die Beteiligten selbst.

## Rollen

| Rolle | Rechte |
| --- | --- |
| Primary Owner | Erstellt eigene Tags oder registriert vorproduzierte Tags, verwaltet Backup Owner und entscheidet über Verlustfälle. Die Rolle ist nicht übertragbar. |
| Backup Owner | Besitzt nach bestätigter Einladung operative Rechte, verwaltet aber keine Berechtigungen. |
| Finder | Meldet einen Fund und sieht nur den eigenen privaten Chat. |
| Admin | Manuell über `users.isAdmin` berechtigt; sieht alle Tags, erstellt Produktionsserien und verwaltet deren Freigabe und Export. Kein pauschaler Zugriff auf private Chats. |

## Happy Path

1. Ein Owner erstellt kostenlos einen aktiven Tag mit sofortiger Zuordnung oder registriert einen vorhandenen, freigegebenen Hardware-Tag über Scan/ID und bestätigte E-Mail. Beide verwenden dieselbe öffentliche UUID-v4-URL. Eigene Tags erhalten einen QR-Download; später folgen Druckvorlagen etwa im Kreditkartenformat.
2. Ein Finder erfasst Text, optional ein Bild und seine E-Mail-Adresse.
3. Ein Magic Link bestätigt die Identität; erst danach entstehen Verlustfall, privater Chat und Owner-Benachrichtigung.
4. Owner sehen ohne Freischaltung nur Anzahl und Zeitpunkte der Meldungen.
5. Ein berechtigter Owner bestätigt den Verlust und bezahlt CHF 10.00 über Mollie Hosted Checkout.
6. Nur eine serverseitig verifizierte Zahlung öffnet aktive, ungesperrte Chats für höchstens 30 Tage.
7. Nach Abschluss oder Ablauf bleiben berechtigte Verläufe schreibgeschützt.

## Phase 1

Enthalten sind Deutsch und Englisch, WCAG 2.2 AA, Magic Links, private Finder-Chats, optionale bereinigte Bilder, bis zu drei Backup Owner und Mollie-Zahlungen in CHF.

Der erweiterte Tag-Umfang umfasst zusätzlich Admin-Produktionsserien, Produktionsexport, Registrierung unzugewiesener Hardware-Tags und eine globale Admin-Tagübersicht. Die Selbsterstellung bleibt erhalten. Ein gemeinsamer Scan-Einstieg wählt anhand des serverseitigen Zustands Registrierung oder Fundmeldung. Ausarbeitung und Reihenfolge stehen im [Tag-Plan](tag-produktion-und-registrierung-plan.md); die beschriebenen Fähigkeiten sind Zielumfang, nicht sämtlich bereits implementiert.

Nicht enthalten sind native Apps, Passwörter, Versand-/Shopabwicklung für physische Tags, Tag-Übertragung, Finderlohn-Zahlungen, Marketplace-Funktionen, KYC, Abonnements und gemeinsame Finder-Chats. Formatierte Druckvorlagen sind ein späterer Ausbau.
