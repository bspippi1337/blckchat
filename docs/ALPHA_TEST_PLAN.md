> Current direction: test over carrier SMS between distant phones, not same-Wi-Fi. First real release requires verified E2EE. Existing tests below describe aspirational acceptance criteria.

# BLCKCHAT Alpha 1 / Testplan for tre mobiler

**Status:** Dette er akseptansekriterier for den kommende fungerende APK-en,
ikke instruksjoner til en app som allerede er lansert.

## Hvem bruker hvilken mobil?

| Mobil | Person | Rolle |
| --- | --- | --- |
| A | Phone A (Admin) | Administrator, GitHub-eier, Android SMS-gateway |
| B | Phone B | Gjest, mottar SMS og chatter uten GitHub-konto |
| C | Phone C | Gjest, mottar SMS og chatter uten GitHub-konto |

## Før testen

Phone A (Admin) installerer APK-en på mobil A og kobler til sin GitHub-konto
med en godkjent autentiseringsflyt. Det opprettes én privat kanal.
Phone A (Admin) velger å bruke telefonens SIM som SMS-avsender og får tydelig
forklaring på eventuelle SMS-kostnader.

En aktiv og TLS-sikret gateway må være tilgjengelig. Pippis telefon
skal kun sende SMS-koder, ikke være eneste meldingsserver: Phone B og
Phone C skal fortsatt kunne chatte dersom mobil A er offline.

## Test 1: To trinn, ingen teknisk oppsett

1. Phone A (Admin) inviterer Phone B og Phone C i appens deltakerliste.
2. Mobil A sender hver sin SMS med lenke til riktig installasjonsside.
3. Phone B installerer appen og åpner **samme SMS-lenke igjen**.
4. Phone C følger nøyaktig samme prosedyre.
5. Etter installasjon mottar hver gjest en NY engangskode på SMS
   (ikke den offentlige installasjonslenken).
6. Begge skriver inn koden, velger visningsnavn og ser riktig kanal.

**Godkjent:** Ingen gjest må kjenne til GitHub, repo, nøkkel, API,
serveradresse eller token. Feil plattform sender aldri brukeren til en
installasjonsfil som telefonen ikke kan kjøre.

## Test 2: Reell gruppesamtale

Phone A (Admin) skriver: `Phone A (Admin) her. Hører dere meg?`

Phone B svarer: `Mobil B online.`

Phone C svarer: `Mobil C online.`

Alle tre skal se de tre meldingene med rett avsender og
samme rekkefølge. Ingen lokal demonstrasjon eller forhåndslagret
melding er godkjent som erstatning.

## Test 3: Ustabilt nett

- Slå av internettilgangen på mobil B. Send meldinger fra A og C.
- Slå på internett på mobil B. Meldinger skal hentes uten duplikater.
- Start appen på mobil C på nytt. Samtalen skal gjenopptas.
- Slå av mobil A etter at begge gjester er innlogget. B og C skal
  fortsatt kunne sende og motta via gatewayen.
- Test kolliderende sendinger, HTTP 429, brutte forbindelser og
  manglende kvitteringer uten tap eller dobbeltpublisering.

## Test 4: Sikkerhet

- Brukt eller utløpt SMS-kode avvises uten å røpe sensitiv informasjon.
- Phone B kan ikke invitere/fjerne deltakere uten eksplisitt autorisasjon.
- Phone A (Admin) fjerner Phone C. Mobil C mister umiddelbart videre tilgang.
- Meldinger skrives ikke under fritt valgt avsenderidentitet; gatewayen
  knytter avsender til en validert økt.
- Private nøkler, invitasjonskoder og GitHub-token opptrer aldri i
  logg, GitHub Pages, krasjrapporter eller innebygde APK-ressurser.

## Leveransekrav

Først når de faktiske testene er bestått på tre installerte mobilklienter
med ekte backend og GitHub-synkronisering, kan APK-en merkes
**BLCKCHAT Alpha 1: fungerende messenger**.

**E2EE** kan ikke loves før egen kryptografisk implementasjon og
revisjon er ferdig. Se `SECURITY.md`.
