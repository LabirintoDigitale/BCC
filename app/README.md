# BarCodeCards per Android

App Android che si collega a Home Assistant e usa direttamente l'integrazione **BarCodeCards (BCC)**.
Tiene una **copia locale** delle carte (database SQLite + foto) così, se alla cassa non c'è campo
o Home Assistant non è raggiungibile, si apre comunque **in sola lettura** con le carte scaricate l'ultima volta.

## Come funziona

- All'avvio mostra subito le carte salvate sul telefono e intanto prova a collegarsi a Home Assistant.
- **Online**: scarica l'elenco (`bcc/cards/list`) e le foto nuove (`/api/bcc/image/…`), sostituisce la copia
  locale e permette di creare, modificare, eliminare carte, cambiare le foto e segnare le preferite.
  Ogni modifica viene salvata anche nella copia locale.
- **Offline**: banner *Senza connessione · sola lettura* con la data dell'ultimo aggiornamento; le carte si
  aprono normalmente (codice a barre, foto fronte/retro, note) ma non si possono modificare.
  Appena torna la rete l'app riprova da sola (o con *Riprova*).
- Se il download fallisce a metà la copia locale precedente resta intatta.
- Vista carta: codice a barre grande su sfondo bianco, **luminosità al massimo** e schermo sempre acceso,
  foto che si gira con un tocco, schermo intero, numero copiabile.
- Gli stessi formati del pannello web (Code 128, EAN-13/8, UPC-A/E, Code 39/93, ITF, ITF-14, Codabar, QR,
  PDF417, Aztec, Data Matrix o solo numero), disegnati sul telefono con ZXing: nessuna rete necessaria.

## Configurazione

1. In Home Assistant: **Profilo → Sicurezza → Token di accesso a lunga durata → Crea token**.
2. Nell'app inserisci l'indirizzo di Home Assistant (es. `http://192.168.1.10:8123`) e il token.
3. Facoltativo: un **indirizzo alternativo** (es. `https://xxxx.ui.nabu.casa`), provato se il primo non risponde.

Cambiando server o token la copia locale viene cancellata e riscaricata.

## Scaricare l'APK

Il workflow **Android app** compila l'APK a ogni push che tocca `app/`: lo trovi negli *Artifacts* della run
(Actions → Android app). Con un tag `app-vX.Y.Z` l'APK viene allegato anche a una release.

Senza chiave di firma l'APK è firmato con una chiave di debug temporanea, diversa a ogni build: per
aggiornare bisogna disinstallare la versione precedente (e la copia locale si riscarica).
Per aggiornamenti normali crea una chiave e aggiungi questi *secrets* al repository:

```bash
keytool -genkeypair -v -keystore bcc.jks -alias bcc -keyalg RSA -keysize 4096 -validity 10000
```

```bash
base64 -w0 bcc.jks
```

`BCC_KEYSTORE_BASE64` (output del comando sopra), `BCC_KEYSTORE_PASSWORD`, `BCC_KEY_ALIAS` (`bcc`), `BCC_KEY_PASSWORD`.

## Compilare in locale

Serve l'Android SDK (Android Studio). Dalla cartella `app/`:

```bash
./gradlew assembleRelease
```

## Note

- Il token è salvato nelle preferenze private dell'app (escluse dal backup).
- È ammesso HTTP in chiaro e sono accettati i certificati CA installati dall'utente, per gli Home Assistant
  raggiungibili solo in LAN o con certificato autofirmato.
- Requisiti: Android 8.0 o superiore, integrazione BarCodeCards installata e configurata.
