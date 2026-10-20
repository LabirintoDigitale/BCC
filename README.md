# BarCodeCards (BCC)

Integrazione custom per **Home Assistant** che aggiunge una voce nella **barra laterale** per gestire le tue carte fedeltà (supermercato, farmacia, benzinaio…): foto **fronte e retro** e **codice a barre** da mostrare alla cassa per farlo scansionare.

## Funzionalità

- Pannello **BarCodeCards** nella barra laterale, disponibile per tutti gli utenti di HA (anche dall'app companion).
- Griglia delle carte con anteprima del fronte, ricerca, ordinamento (A–Z / più usate) e carte **preferite** in cima.
- Vista carta a schermo intero con **codice a barre grande su sfondo bianco**, numero copiabile e immagine che si **gira** (tap) tra fronte e retro; un pulsante mostra l'immagine attuale **a schermo intero**, da sola su sfondo nero (un tap per tornare indietro). Lo schermo resta acceso finché la carta è aperta (Wake Lock, dove supportato).
- Formati supportati: Code 128, EAN-13, EAN-8, UPC-A, UPC-E, Code 39, Code 93, ITF / ITF-14, Codabar, QR Code, PDF417, Aztec, Data Matrix, oppure solo numero.
- **Scansione** del codice con la fotocamera, oppure **lettura automatica** del codice dalla foto del retro.
- **Import multiplo da screenshot** (es. dall'app VirtualCards): selezioni più screenshot insieme, il pannello legge i codici a barre, salta i duplicati e ti fa rinominare le carte prima di importarle.
- Le foto vengono ridimensionate nel browser (max 1280 px, JPEG) prima del caricamento.
- Dati salvati localmente: elenco in `.storage/bcc.cards`, immagini in `config/bcc_images/`. Le immagini sono servite solo ad utenti autenticati (URL firmati).

## Installazione

### HACS (consigliato)

1. HACS → menu ⋮ → **Repository personalizzati**
2. URL: `https://github.com/LabirintoDigitale/BCC`, categoria **Integrazione**
3. Installa **BarCodeCards (BCC)** e riavvia Home Assistant.

### Manuale

Copia la cartella `custom_components/bcc` dentro `config/custom_components/` e riavvia Home Assistant.

### Configurazione

**Impostazioni → Dispositivi e servizi → Aggiungi integrazione → BarCodeCards**. Comparirà la voce *BarCodeCards* nella barra laterale.
Dalle opzioni dell'integrazione puoi cambiare il titolo mostrato nella barra laterale.

## Note

- La fotocamera nel browser funziona solo se Home Assistant è aperto in **HTTPS** (o tramite l'app companion / Nabu Casa).
- La scansione usa il `BarcodeDetector` nativo del browser (Chrome/Android, Safari macOS). Dove manca (iOS, Firefox, Chrome su Windows) viene caricato al volo il polyfill [`barcode-detector`](https://github.com/Sec-ant/barcode-detector) (zxing-wasm) da jsDelivr: serve una connessione internet solo per questa funzione. Il disegno dei codici a barre funziona offline.
- Disinstallare l'integrazione **non** cancella le carte: per rimuoverle del tutto elimina `.storage/bcc.cards` e la cartella `bcc_images`.

## Rilasci

HACS mostra come versione l'ultima **release** GitHub. Per pubblicarne una basta aumentare `version` in `custom_components/bcc/manifest.json` e `VERSION` in `custom_components/bcc/const.py` (devono coincidere) e fare push su `main`: il workflow *Release* crea il tag `vX.Y.Z` e la release se non esistono già.

## Crediti

- Codici a barre generati con [bwip-js](https://github.com/metafloor/bwip-js) (MIT), incluso in `custom_components/bcc/frontend/`.

## Licenza

MIT
