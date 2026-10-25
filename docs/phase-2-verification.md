# Phase 2: Firebase-Owner-Identität – Verifikation

## Stand

Der Firebase-Featurebranch ersetzt den bisherigen Auth-Anbieter. Frühere Provider-Abnahmen belegen den neuen Anbieter nicht; deren Nachweise bleiben in der Git-Historie. Ein echter REST-/Backend-Durchstich ist erfolgreich; vollständige Mail-/Browser-/Hosting-Abnahme bleibt offen.

## Automatisierte Prüfung

```bash
npm run verify
npm run test:http
git diff --check
```

Die Offline-Tests prüfen Firebase-RS256-JWTs und Claims, normalisierte bestätigte E-Mail, UID-Abgleich, Providerfehler, lokale Sperren, parallele Anlage, maximale API-Sitzungsdauer, Callback-Bereinigung, E-Mail-Eingabe auf einem zweiten Gerät, Replay, SDK-Refresh, Logout und Begrenzung der Token-Ziele. Der Build-Konfigurationsgenerator sowie ungültige Runtime-Konfigurationen werden separat geprüft.

CI des ursprünglichen Firebase-Commits `10055e3` war erfolgreich. Neuere Änderungen benötigen eine erneute erfolgreiche CI.

## Lokaler Start

Die vier Werte aus [Firebase-Einrichtung](firebase-auth-setup.md) in `.env.dev.local` setzen. Danach in zwei Terminals:

```bash
node --env-file=.env.dev.local apps/api/scripts/firestore-dev.mjs start
npm run start --workspace=@lost-key-finder/web
```

## Firestore-Integration

```bash
npm run firestore:verify --workspace=@lost-key-finder/api
```

Der explizite Provider-Lauf prüft echte Transaktionen, Mapping, Unique-Index, parallele Anlage und lokalen Status mit Cleanup. Der Identity-Server bleibt dabei ein Testdouble.

## Erfolgreicher Dev-Durchstich am 24. Oktober 2026

- Firebase-Web-App im Projekt `lost-key-finder-dev` registriert und Authentication initialisiert.
- E-Mail-Link-Anmeldung aktiviert; `localhost`, `lost-key-finder-dev.web.app` und `lost-key-finder-dev.firebaseapp.com` erlaubt.
- Vier Firebase-Werte lokal und drei öffentliche Build-Werte im GitHub-Environment `dev` hinterlegt; der Workflow übernimmt die Projekt-ID aus `GCP_PROJECT_ID`.
- Neuer eindeutiger `firebaseUserId`-Index `READY`; bisheriger Identity-Index entfernt. Ein lokales Altdokument blieb unverändert erhalten.
- Beide echten Firestore-Integrationstests erfolgreich: Transaktion sowie Unique-Index, parallele Provisionierung und lokaler Status. Der erste Lauf während der Indexumstellung schlug noch fehl; nach wirksamer Entfernung des alten Indexes war die Wiederholung erfolgreich.
- Für eine zufällige Testadresse unter der reservierten Domain `example.invalid` per administrativem `returnOobLink` einen echten Firebase-Link erzeugt. Es wurde keine E-Mail versendet.
- Link per Firebase-REST eingelöst; bestätigte E-Mail und ID-Token gegen die lokale API auf Port 3301 geprüft. Google-JWKS, `accounts:lookup` und Provisionierung in echtem Dev-Firestore funktionierten.
- Echten Refresh-Token bei Google eingelöst; erneuter `/api/v1/me`-Aufruf lieferte dieselbe lokale Benutzer-ID.
- Erneute Linkeinlösung abgelehnt; `/me` ohne Token lieferte `401`.
- Temporäres Firebase-Testkonto und ausschliesslich sein lokales Firestore-Dokument danach gelöscht. Kein Link und kein Token wurde im Repository gespeichert.

Dies beweist den Provider-/Backend-Vertrag, nicht E-Mail-Zustellung, Angular-SDK-Verhalten im echten Browser oder das Hosting-Deployment. Es wurde kein Prod-Projekt angelegt und kein Deployment ausgelöst. Die lesende Projektliste enthält derzeit nur Dev; GitHub besitzt noch kein `prod`-Environment. Die Billing-Abfrage war wegen deaktivierter Cloud Billing API nicht verfügbar; Abrechnung und Versandquoten bleiben separat zu prüfen.

## Ausstehende Firebase-Live-Abnahme

- [ ] Dev-Frontend und Dev-API mit passender Firebase-Konfiguration ausrollen.
- [ ] Neues und bestehendes Firebase-Konto, mehrere Konten und `/api/v1/me` prüfen.
- [ ] Link im selben Browser und auf einem zweiten Gerät öffnen; im zweiten Fall E-Mail erneut eingeben.
- [ ] Falsche E-Mail, ungültigen/abgelaufenen und bereits eingelösten Link ablehnen.
- [ ] Browserneustart, Token-Refresh und Logout über Tabs prüfen.
- [ ] Lokale Sperre sofort wirksam; Provider-Widerruf entsprechend der dokumentierten Grenze.
- [ ] Callback mit `no-store`/`no-referrer`; URL nach Einlösung bereinigt.
- [ ] Keine Auth-Credentials oder Inhalte in API-Logs; keine Tokens an fremde API-Origins.
- [ ] Ohne Token liefert `/api/v1/me` ein sicheres `401`-Problem.

Nach Durchführung Commit, Umgebung, Datum und Ergebnis dokumentieren. Keine E-Mail-Adressen, Links, Codes oder Tokens in den Nachweis aufnehmen.
