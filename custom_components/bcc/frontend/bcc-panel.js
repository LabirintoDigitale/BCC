/*
 * BarCodeCards (BCC) – sidebar panel for Home Assistant.
 * Plain web component, no build step. Barcodes are drawn with the bundled
 * bwip-js; scanning uses the native BarcodeDetector or, when missing
 * (iOS, Firefox, desktop Windows), the zxing-wasm based polyfill from jsDelivr.
 */

const BASE = new URL(".", import.meta.url).href;
const POLYFILL_URL =
  "https://cdn.jsdelivr.net/npm/barcode-detector@3.2.2/dist/es/ponyfill.js/+esm";

const FORMATS = [
  ["code128", "Code 128"],
  ["ean13", "EAN-13"],
  ["ean8", "EAN-8"],
  ["upca", "UPC-A"],
  ["upce", "UPC-E"],
  ["code39", "Code 39"],
  ["code93", "Code 93"],
  ["interleaved2of5", "ITF (2 of 5)"],
  ["itf14", "ITF-14"],
  ["rationalizedCodabar", "Codabar"],
  ["qrcode", "QR Code"],
  ["pdf417", "PDF417"],
  ["azteccode", "Aztec"],
  ["datamatrix", "Data Matrix"],
  ["none", "—"],
];
const TWO_D = new Set(["qrcode", "pdf417", "azteccode", "datamatrix"]);

// BarcodeDetector format name -> bwip-js bcid
const DETECTED = {
  code_128: "code128",
  ean_13: "ean13",
  ean_8: "ean8",
  upc_a: "upca",
  upc_e: "upce",
  code_39: "code39",
  code_93: "code93",
  itf: "interleaved2of5",
  codabar: "rationalizedCodabar",
  qr_code: "qrcode",
  pdf417: "pdf417",
  aztec: "azteccode",
  data_matrix: "datamatrix",
};

const COLORS = ["#e53935", "#fb8c00", "#fdd835", "#43a047", "#00897b", "#03a9f4", "#3949ab", "#8e24aa", "#6d4c41", "#546e7a"];

const STRINGS = {
  en: {
    search: "Search cards…",
    add: "Add card",
    empty: "No cards yet. Tap + to add your first loyalty card.",
    noResults: "No cards match your search.",
    name: "Name",
    number: "Card number / code",
    format: "Barcode type",
    color: "Color",
    notes: "Notes",
    favorite: "Favorite",
    front: "Front",
    back: "Back",
    choose: "Photo",
    remove: "Remove",
    readCode: "Read code",
    scan: "Scan",
    save: "Save",
    cancel: "Cancel",
    edit: "Edit",
    delete: "Delete",
    confirmDelete: "Tap again to delete",
    flip: "Flip",
    fullscreen: "Full screen",
    copy: "Copy",
    copied: "Copied",
    newCard: "New card",
    editCard: "Edit card",
    nameRequired: "Name is required",
    invalidCode: "This code is not valid for the selected barcode type",
    noCodeFound: "No barcode found",
    codeFound: "Barcode read",
    cameraError: "Camera not available (Home Assistant must be opened over HTTPS)",
    scanHint: "Point the camera at the barcode",
    saved: "Saved",
    sortName: "A–Z",
    sortUsed: "Most used",
    loading: "Loading…",
    import: "Import from screenshots",
    importTitle: "Import cards",
    importHint: "Select one or more screenshots (e.g. from VirtualCards): every barcode found becomes a card.",
    importReading: "Reading barcodes…",
    importBtn: "Import",
    importNone: "No barcode found",
    importDup: "Already in your cards",
    importDone: "cards imported",
    attachShot: "Save the screenshot as the card back",
    card: "Card",
  },
  it: {
    search: "Cerca carte…",
    add: "Aggiungi carta",
    empty: "Nessuna carta. Tocca + per aggiungere la tua prima carta fedeltà.",
    noResults: "Nessuna carta corrisponde alla ricerca.",
    name: "Nome",
    number: "Numero carta / codice",
    format: "Tipo di codice a barre",
    color: "Colore",
    notes: "Note",
    favorite: "Preferita",
    front: "Fronte",
    back: "Retro",
    choose: "Foto",
    remove: "Rimuovi",
    readCode: "Leggi codice",
    scan: "Scansiona",
    save: "Salva",
    cancel: "Annulla",
    edit: "Modifica",
    delete: "Elimina",
    confirmDelete: "Tocca di nuovo per eliminare",
    flip: "Gira",
    fullscreen: "Schermo intero",
    copy: "Copia",
    copied: "Copiato",
    newCard: "Nuova carta",
    editCard: "Modifica carta",
    nameRequired: "Il nome è obbligatorio",
    invalidCode: "Il codice non è valido per il tipo di codice a barre scelto",
    noCodeFound: "Nessun codice a barre trovato",
    codeFound: "Codice letto",
    cameraError: "Fotocamera non disponibile (Home Assistant deve essere aperto in HTTPS)",
    scanHint: "Inquadra il codice a barre",
    saved: "Salvato",
    sortName: "A–Z",
    sortUsed: "Più usate",
    loading: "Caricamento…",
    import: "Importa da screenshot",
    importTitle: "Importa carte",
    importHint: "Seleziona uno o più screenshot (es. da VirtualCards): ogni codice a barre trovato diventa una carta.",
    importReading: "Lettura dei codici…",
    importBtn: "Importa",
    importNone: "Nessun codice trovato",
    importDup: "Già presente tra le tue carte",
    importDone: "carte importate",
    attachShot: "Salva lo screenshot come retro della carta",
    card: "Carta",
  },
};

const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

const lsGet = (k, d) => {
  try {
    return localStorage.getItem(k) ?? d;
  } catch {
    return d;
  }
};
const lsSet = (k, v) => {
  try {
    localStorage.setItem(k, v);
  } catch {
    /* ignore */
  }
};

let bwipPromise;
function loadBwip() {
  if (window.bwipjs) return Promise.resolve(window.bwipjs);
  if (!bwipPromise) {
    bwipPromise = new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = `${BASE}bwip-js-min.js`;
      s.onload = () => resolve(window.bwipjs);
      s.onerror = () => {
        bwipPromise = undefined;
        reject(new Error("bwip-js failed to load"));
      };
      document.head.appendChild(s);
    });
  }
  return bwipPromise;
}

let detectorPromise;
function getDetector() {
  if (!detectorPromise) {
    detectorPromise = (async () => {
      if ("BarcodeDetector" in window) {
        try {
          const formats = await window.BarcodeDetector.getSupportedFormats();
          if (formats.length) return new window.BarcodeDetector({ formats });
        } catch {
          /* fall back to polyfill */
        }
      }
      const mod = await import(POLYFILL_URL);
      return new mod.BarcodeDetector();
    })().catch((err) => {
      detectorPromise = undefined;
      throw err;
    });
  }
  return detectorPromise;
}

async function detectIn(source) {
  const detector = await getDetector();
  const found = await detector.detect(source);
  if (!found.length) return null;
  const best = found.sort((a, b) => b.rawValue.length - a.rawValue.length)[0];
  return { number: best.rawValue, format: DETECTED[best.format] || "code128" };
}

async function loadBitmap(file) {
  try {
    return await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    return createImageBitmap(file);
  }
}

function bitmapToDataUrl(bitmap, max = 1280) {
  const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL("image/jpeg", 0.85);
}

const STYLE = `
  :host {
    display: block;
    height: 100%;
    background: var(--primary-background-color, #fafafa);
    color: var(--primary-text-color, #212121);
    font-family: var(--paper-font-body1_-_font-family, Roboto, sans-serif);
    -webkit-font-smoothing: antialiased;
  }
  * { box-sizing: border-box; }
  button { font: inherit; color: inherit; }
  .toolbar {
    display: flex; align-items: center; gap: 4px;
    height: var(--header-height, 56px);
    padding: 0 12px 0 4px;
    background: var(--app-header-background-color, var(--primary-color, #03a9f4));
    color: var(--app-header-text-color, #fff);
    border-bottom: var(--app-header-border-bottom, none);
    position: sticky; top: 0; z-index: 2;
  }
  .toolbar .title { flex: 1; font-size: 20px; font-weight: 400; margin-left: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .icon-btn {
    display: inline-flex; align-items: center; justify-content: center;
    width: 40px; height: 40px; border: none; border-radius: 50%;
    background: transparent; cursor: pointer;
  }
  .icon-btn:hover { background: rgba(127,127,127,.15); }
  .content { max-width: 1100px; margin: 0 auto; padding: 16px; }
  .bar { display: flex; gap: 8px; margin-bottom: 16px; }
  .bar input, .bar select {
    height: 44px; border-radius: 22px; padding: 0 16px;
    border: 1px solid var(--divider-color, #ddd);
    background: var(--card-background-color, #fff);
    color: var(--primary-text-color); font: inherit;
  }
  .bar input { flex: 1; min-width: 0; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 16px; }
  @media (max-width: 420px) { .grid { grid-template-columns: repeat(2, 1fr); gap: 12px; } }
  .tile { border: none; padding: 0; background: none; cursor: pointer; text-align: left; }
  .art {
    position: relative; aspect-ratio: 1.586; border-radius: 12px; overflow: hidden;
    display: flex; align-items: center; justify-content: center;
    box-shadow: var(--ha-card-box-shadow, 0 2px 6px rgba(0,0,0,.18));
    transition: transform .15s;
  }
  .tile:hover .art { transform: translateY(-2px); }
  .art img { width: 100%; height: 100%; object-fit: cover; display: block; }
  .art .initial { color: #fff; font-size: 22px; font-weight: 600; padding: 8px; text-align: center; text-shadow: 0 1px 3px rgba(0,0,0,.35); word-break: break-word; }
  .label { margin-top: 8px; font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .star { color: #fbc02d; }
  .empty { text-align: center; padding: 48px 16px; color: var(--secondary-text-color, #727272); }

  .backdrop {
    position: fixed; inset: 0; z-index: 10; background: rgba(0,0,0,.55);
    display: flex; align-items: center; justify-content: center; padding: 16px;
  }
  .dialog {
    width: 100%; max-width: 520px; max-height: 100%; overflow: auto;
    background: var(--card-background-color, #fff); color: var(--primary-text-color);
    border-radius: 16px; box-shadow: 0 10px 40px rgba(0,0,0,.4);
  }
  @media (max-width: 600px) {
    .backdrop { padding: 0; }
    .dialog { max-width: none; height: 100%; border-radius: 0; }
  }
  .dlg-head { display: flex; align-items: center; gap: 8px; padding: 8px 8px 8px 20px; position: sticky; top: 0; background: inherit; z-index: 1; }
  .dlg-head h2 { flex: 1; margin: 0; font-size: 20px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .dlg-body { padding: 0 20px 20px; }
  .dlg-foot { display: flex; gap: 8px; justify-content: flex-end; padding: 12px 20px 20px; flex-wrap: wrap; }

  .barcode {
    background: #fff; border-radius: 12px; padding: 16px;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    min-height: 120px; border: 1px solid var(--divider-color, #e0e0e0);
  }
  .barcode canvas { max-width: 100%; height: auto; image-rendering: pixelated; }
  .barcode canvas.linear { width: 100%; max-width: 460px; }
  .barcode canvas.matrix { width: 75%; max-width: 300px; }
  .barcode .big { color: #000; font-size: 28px; font-weight: 600; letter-spacing: 2px; word-break: break-all; text-align: center; font-family: monospace; }
  .barcode .err { color: #c62828; font-size: 13px; margin-top: 8px; text-align: center; }
  .numrow { display: flex; align-items: center; justify-content: center; gap: 8px; margin: 12px 0; font-family: monospace; font-size: 17px; word-break: break-all; }
  .notes { white-space: pre-wrap; color: var(--secondary-text-color); margin: 8px 0 16px; }

  .flip { perspective: 1200px; cursor: pointer; margin-top: 8px; }
  .flip-inner { position: relative; aspect-ratio: 1.586; transition: transform .6s; transform-style: preserve-3d; }
  .flip.flipped .flip-inner { transform: rotateY(180deg); }
  .face { position: absolute; inset: 0; backface-visibility: hidden; -webkit-backface-visibility: hidden; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,.25); display: flex; align-items: center; justify-content: center; }
  .face img { width: 100%; height: 100%; object-fit: cover; }
  .face.back { transform: rotateY(180deg); }
  .face .ph { color: #fff; font-weight: 600; font-size: 18px; text-shadow: 0 1px 3px rgba(0,0,0,.35); }
  .flip-hint { display: flex; align-items: center; justify-content: center; gap: 4px; font-size: 12px; color: var(--secondary-text-color); margin-top: 6px; }
  .fullimg { position: fixed; inset: 0; z-index: 40; background: #000; display: flex; align-items: center; justify-content: center; cursor: zoom-out; }
  .fullimg img { width: 100%; height: 100%; object-fit: contain; }

  .btn {
    display: inline-flex; align-items: center; gap: 6px;
    height: 40px; padding: 0 16px; border-radius: 20px; cursor: pointer;
    border: 1px solid var(--divider-color, #ccc); background: transparent; font-weight: 500;
  }
  .btn.primary { background: var(--primary-color, #03a9f4); border-color: transparent; color: var(--text-primary-color, #fff); }
  .btn.danger { color: var(--error-color, #db4437); border-color: currentColor; }
  .btn.danger.armed { background: var(--error-color, #db4437); color: #fff; }
  .btn.small { height: 32px; padding: 0 12px; font-size: 13px; }
  .btn:disabled { opacity: .5; cursor: default; }
  .spacer { flex: 1; }

  label.field { display: block; margin-bottom: 14px; }
  label.field > span { display: block; font-size: 12px; color: var(--secondary-text-color); margin-bottom: 4px; }
  .field input[type=text], .field select, .field textarea {
    width: 100%; padding: 10px 12px; border-radius: 8px; font: inherit;
    border: 1px solid var(--divider-color, #ccc);
    background: var(--secondary-background-color, transparent); color: var(--primary-text-color);
  }
  .field textarea { min-height: 70px; resize: vertical; }
  .row { display: flex; gap: 8px; align-items: center; }
  .row > input { flex: 1; min-width: 0; }
  .swatches { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
  .swatch { width: 30px; height: 30px; border-radius: 50%; border: 2px solid transparent; cursor: pointer; padding: 0; }
  .swatch.sel { border-color: var(--primary-text-color); box-shadow: 0 0 0 2px var(--card-background-color, #fff) inset; }
  .swatches input[type=color] { width: 34px; height: 34px; border: none; padding: 0; background: none; cursor: pointer; }
  .check { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
  .pics { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 14px; }
  .pic .prev {
    aspect-ratio: 1.586; border-radius: 10px; overflow: hidden; cursor: pointer;
    border: 2px dashed var(--divider-color, #ccc); display: flex; align-items: center; justify-content: center;
    color: var(--secondary-text-color); font-size: 13px; text-align: center;
  }
  .pic .prev img { width: 100%; height: 100%; object-fit: cover; }
  .pic .title { font-size: 12px; color: var(--secondary-text-color); margin-bottom: 4px; }
  .pic .acts { display: flex; gap: 4px; margin-top: 6px; flex-wrap: wrap; }
  .preview-code { margin-bottom: 14px; }

  .scanner { position: fixed; inset: 0; z-index: 20; background: #000; display: flex; flex-direction: column; }
  .scanner video { flex: 1; width: 100%; object-fit: cover; min-height: 0; }
  .scanner .frame { position: absolute; left: 10%; right: 10%; top: 35%; height: 30%; border: 3px solid rgba(255,255,255,.85); border-radius: 12px; pointer-events: none; }
  .scanner .bottom { padding: 16px; display: flex; align-items: center; gap: 12px; color: #fff; }
  .scanner .btn { color: #fff; border-color: #fff; }

  .imp-row { display: grid; grid-template-columns: 56px 1fr; gap: 10px; align-items: start; padding: 10px 0; border-bottom: 1px solid var(--divider-color, #e0e0e0); }
  .imp-row.off { opacity: .55; }
  .imp-row img { width: 56px; height: 90px; object-fit: cover; border-radius: 6px; }
  .imp-row .fields { display: grid; gap: 6px; min-width: 0; }
  .imp-row input[type=text], .imp-row select { width: 100%; padding: 6px 8px; border-radius: 6px; font: inherit; border: 1px solid var(--divider-color, #ccc); background: transparent; color: var(--primary-text-color); }
  .imp-row .msg { font-size: 12px; color: var(--warning-color, #ff9800); }
  .hint { color: var(--secondary-text-color); font-size: 14px; margin: 0 0 12px; }
  .toast {
    position: fixed; left: 50%; bottom: 24px; transform: translateX(-50%);
    background: #323232; color: #fff; padding: 12px 20px; border-radius: 8px; z-index: 30;
    font-size: 14px; max-width: 90vw; box-shadow: 0 4px 12px rgba(0,0,0,.3);
  }
`;

class BccPanel extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._cards = [];
    this._loaded = false;
    this._search = "";
    this._sort = lsGet("bcc-sort", "name");
    this._signed = new Map();
    this._wakeLock = null;
    this._onKey = (e) => {
      if (e.key !== "Escape") return;
      if (this._stopScan) this._stopScan();
      else this._closeOverlay();
    };
  }

  set hass(hass) {
    const first = !this._hass;
    this._hass = hass;
    if (this._menu) this._menu.hass = hass;
    if (first) {
      this._renderShell();
      this._load();
    }
  }
  get hass() {
    return this._hass;
  }

  set narrow(narrow) {
    this._narrow = narrow;
    if (this._menu) this._menu.narrow = narrow;
  }

  set panel(panel) {
    this._panel = panel;
    const title = this.shadowRoot.querySelector(".toolbar .title");
    if (title && panel?.title) title.textContent = panel.title;
  }

  connectedCallback() {
    window.addEventListener("keydown", this._onKey);
  }

  disconnectedCallback() {
    window.removeEventListener("keydown", this._onKey);
    if (this._stopScan) this._stopScan();
    this._releaseWake();
  }

  t(key) {
    const lang = (this._hass?.locale?.language || this._hass?.language || "en").slice(0, 2);
    return (STRINGS[lang] || STRINGS.en)[key] || STRINGS.en[key] || key;
  }

  // ---------- data ----------

  async _ws(msg) {
    return this._hass.callWS(msg);
  }

  async _load() {
    try {
      const res = await this._ws({ type: "bcc/cards/list" });
      this._cards = res.cards;
    } catch (err) {
      this._toast(err.message || String(err));
    }
    this._loaded = true;
    this._renderGrid();
  }

  _signedUrl(file) {
    if (!file) return Promise.resolve(null);
    if (!this._signed.has(file)) {
      const p = this._ws({ type: "auth/sign_path", path: `/api/bcc/image/${file}`, expires: 86400 })
        .then((r) => r.path)
        .catch(() => {
          this._signed.delete(file);
          return null;
        });
      this._signed.set(file, p);
    }
    return this._signed.get(file);
  }

  _fillImages(root) {
    root.querySelectorAll("img[data-file]").forEach(async (img) => {
      const url = await this._signedUrl(img.dataset.file);
      if (url) img.src = url;
    });
  }

  _sorted() {
    const q = this._search.trim().toLowerCase();
    const list = this._cards.filter(
      (c) => !q || c.name.toLowerCase().includes(q) || (c.number || "").toLowerCase().includes(q) || (c.notes || "").toLowerCase().includes(q)
    );
    return list.sort((a, b) => {
      if (a.favorite !== b.favorite) return a.favorite ? -1 : 1;
      if (this._sort === "used" && (b.use_count || 0) !== (a.use_count || 0)) return (b.use_count || 0) - (a.use_count || 0);
      return a.name.localeCompare(b.name, undefined, { sensitivity: "base" });
    });
  }

  // ---------- rendering ----------

  _renderShell() {
    const root = this.shadowRoot;
    root.innerHTML = `
      <style>${STYLE}</style>
      <div class="toolbar">
        <span id="menu"></span>
        <div class="title">${esc(this._panel?.title || "BarCodeCards")}</div>
        <button class="icon-btn" id="import" title="${esc(this.t("import"))}"><ha-icon icon="mdi:image-multiple"></ha-icon></button>
        <input type="file" id="import-files" accept="image/*" multiple hidden>
        <button class="icon-btn" id="add" title="${esc(this.t("add"))}"><ha-icon icon="mdi:plus"></ha-icon></button>
      </div>
      <div class="content">
        <div class="bar">
          <input id="search" type="search" placeholder="${esc(this.t("search"))}" autocomplete="off">
          <select id="sort">
            <option value="name">${esc(this.t("sortName"))}</option>
            <option value="used">${esc(this.t("sortUsed"))}</option>
          </select>
        </div>
        <div id="grid" class="grid"></div>
        <div id="empty" class="empty">${esc(this.t("loading"))}</div>
      </div>
      <div id="overlay"></div>
    `;
    const menu = document.createElement("ha-menu-button");
    menu.hass = this._hass;
    menu.narrow = this._narrow;
    root.getElementById("menu").appendChild(menu);
    this._menu = menu;

    root.getElementById("add").addEventListener("click", () => this._openEditor(null));
    const importInput = root.getElementById("import-files");
    root.getElementById("import").addEventListener("click", () => importInput.click());
    importInput.addEventListener("change", () => {
      const files = [...importInput.files];
      importInput.value = "";
      if (files.length) this._openImport(files);
    });
    root.getElementById("search").addEventListener("input", (e) => {
      this._search = e.target.value;
      this._renderGrid();
    });
    const sort = root.getElementById("sort");
    sort.value = this._sort;
    sort.addEventListener("change", (e) => {
      this._sort = e.target.value;
      lsSet("bcc-sort", this._sort);
      this._renderGrid();
    });
    root.getElementById("grid").addEventListener("click", (e) => {
      const tile = e.target.closest(".tile");
      if (tile) this._openViewer(tile.dataset.id);
    });
  }

  _cardArt(card, side = "front") {
    const file = card[side] || (side === "front" ? card.back : null);
    if (file) return `<img data-file="${esc(file)}" alt="">`;
    return `<span class="initial">${esc(card.name)}</span>`;
  }

  _renderGrid() {
    const root = this.shadowRoot;
    const grid = root.getElementById("grid");
    const empty = root.getElementById("empty");
    if (!grid) return;
    const cards = this._sorted();
    grid.innerHTML = cards
      .map(
        (c) => `
        <button class="tile" data-id="${esc(c.id)}" title="${esc(c.name)}">
          <div class="art" style="background:${esc(c.color || "#03a9f4")}">${this._cardArt(c)}</div>
          <div class="label">${c.favorite ? '<span class="star">★</span> ' : ""}${esc(c.name)}</div>
        </button>`
      )
      .join("");
    this._fillImages(grid);
    if (!this._loaded) {
      empty.textContent = this.t("loading");
      empty.hidden = false;
    } else if (!cards.length) {
      empty.textContent = this._cards.length ? this.t("noResults") : this.t("empty");
      empty.hidden = false;
    } else {
      empty.hidden = true;
    }
  }

  _overlay(html) {
    const ov = this.shadowRoot.getElementById("overlay");
    ov.innerHTML = `<div class="backdrop">${html}</div>`;
    const backdrop = ov.firstElementChild;
    backdrop.addEventListener("click", (e) => {
      if (e.target === backdrop) this._closeOverlay();
    });
    return backdrop;
  }

  _closeOverlay() {
    this._closeFullImage();
    this.shadowRoot.getElementById("overlay").innerHTML = "";
    this._releaseWake();
  }

  // Image alone on a black screen; any tap goes back to the card.
  async _openFullImage(file) {
    if (!file) return;
    const url = await this._signedUrl(file);
    if (!url) return;
    this._closeFullImage();
    const el = document.createElement("div");
    el.className = "fullimg";
    el.innerHTML = `<img src="${esc(url)}" alt="">`;
    el.addEventListener("click", () => this._closeFullImage());
    this.shadowRoot.appendChild(el);
    this._fullImg = el;
    try {
      if (el.requestFullscreen) {
        await el.requestFullscreen();
        // Leaving fullscreen with Esc / back also closes the image.
        el.addEventListener("fullscreenchange", () => {
          if (!document.fullscreenElement) this._closeFullImage();
        });
      }
    } catch {
      /* fullscreen API unavailable: the fixed layer still covers the panel */
    }
  }

  _closeFullImage() {
    const el = this._fullImg;
    if (!el) return;
    this._fullImg = null;
    el.remove();
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  }

  _toast(text) {
    const old = this.shadowRoot.querySelector(".toast");
    if (old) old.remove();
    const el = document.createElement("div");
    el.className = "toast";
    el.textContent = text;
    this.shadowRoot.appendChild(el);
    setTimeout(() => el.remove(), 3000);
  }

  async _drawBarcode(box, format, number) {
    box.innerHTML = "";
    if (!number) return;
    if (format === "none") {
      box.innerHTML = `<div class="big">${esc(number)}</div>`;
      return;
    }
    const canvas = document.createElement("canvas");
    canvas.className = TWO_D.has(format) ? "matrix" : "linear";
    box.appendChild(canvas);
    try {
      const bwip = await loadBwip();
      const opts = { bcid: format, text: number, scale: 3, paddingwidth: 6, paddingheight: 6, backgroundcolor: "FFFFFF" };
      if (!TWO_D.has(format)) Object.assign(opts, { height: 22, includetext: true, textxalign: "center" });
      bwip.toCanvas(canvas, opts);
    } catch (err) {
      canvas.remove();
      box.innerHTML = `<div class="big">${esc(number)}</div><div class="err">${esc(this.t("invalidCode"))}</div>`;
    }
  }

  async _requestWake() {
    try {
      if (navigator.wakeLock) this._wakeLock = await navigator.wakeLock.request("screen");
    } catch {
      /* not allowed */
    }
  }

  _releaseWake() {
    if (this._wakeLock) {
      this._wakeLock.release().catch(() => {});
      this._wakeLock = null;
    }
  }

  // ---------- viewer ----------

  _openViewer(id, markUsed = true) {
    const card = this._cards.find((c) => c.id === id);
    if (!card) return;
    const hasImg = card.front || card.back;
    const face = (side) =>
      card[side]
        ? `<img data-file="${esc(card[side])}" alt="${esc(this.t(side))}">`
        : `<span class="ph">${esc(card.name)}</span>`;
    const bd = this._overlay(`
      <div class="dialog">
        <div class="dlg-head">
          <h2>${card.favorite ? '<span class="star">★</span> ' : ""}${esc(card.name)}</h2>
          <button class="icon-btn" data-act="close"><ha-icon icon="mdi:close"></ha-icon></button>
        </div>
        <div class="dlg-body">
          <div class="barcode" id="code"></div>
          ${card.number && card.format !== "none"
            ? `<div class="numrow"><span>${esc(card.number)}</span>
                 <button class="icon-btn" data-act="copy" title="${esc(this.t("copy"))}"><ha-icon icon="mdi:content-copy"></ha-icon></button></div>`
            : ""}
          ${card.notes ? `<div class="notes">${esc(card.notes)}</div>` : ""}
          ${hasImg
            ? `<div class="flip" data-act="flip">
                 <div class="flip-inner">
                   <div class="face front" style="background:${esc(card.color)}">${face("front")}</div>
                   <div class="face back" style="background:${esc(card.color)}">${face("back")}</div>
                 </div>
               </div>
               <div class="flip-hint">${esc(this.t("front"))} / ${esc(this.t("back"))} · ${esc(this.t("flip"))} ↻
                 <button class="icon-btn" data-act="fullscreen" title="${esc(this.t("fullscreen"))}"><ha-icon icon="mdi:fullscreen"></ha-icon></button></div>`
            : ""}
        </div>
        <div class="dlg-foot">
          <button class="btn danger" data-act="delete"><ha-icon icon="mdi:delete"></ha-icon>${esc(this.t("delete"))}</button>
          <span class="spacer"></span>
          <button class="btn primary" data-act="edit"><ha-icon icon="mdi:pencil"></ha-icon>${esc(this.t("edit"))}</button>
        </div>
      </div>`);
    this._drawBarcode(bd.querySelector("#code"), card.format, card.number);
    this._fillImages(bd);
    this._requestWake();

    let armed = false;
    bd.addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-act]");
      if (!btn) return;
      const act = btn.dataset.act;
      if (act === "close") this._closeOverlay();
      else if (act === "flip") btn.classList.toggle("flipped");
      else if (act === "fullscreen") {
        const flipped = bd.querySelector(".flip").classList.contains("flipped");
        const side = flipped ? "back" : "front";
        this._openFullImage(card[side] || card[flipped ? "front" : "back"]);
      }
      else if (act === "edit") this._openEditor(card);
      else if (act === "copy") {
        try {
          await navigator.clipboard.writeText(card.number);
          this._toast(this.t("copied"));
        } catch {
          /* clipboard unavailable */
        }
      } else if (act === "delete") {
        if (!armed) {
          armed = true;
          btn.classList.add("armed");
          btn.lastChild.textContent = this.t("confirmDelete");
          setTimeout(() => {
            armed = false;
            btn.classList.remove("armed");
            btn.lastChild.textContent = this.t("delete");
          }, 4000);
          return;
        }
        try {
          await this._ws({ type: "bcc/cards/delete", card_id: card.id });
          this._cards = this._cards.filter((c) => c.id !== card.id);
          this._closeOverlay();
          this._renderGrid();
        } catch (err) {
          this._toast(err.message || String(err));
        }
      }
    });

    if (!markUsed) return;
    this._ws({ type: "bcc/cards/used", card_id: card.id })
      .then((updated) => {
        const i = this._cards.findIndex((c) => c.id === updated.id);
        if (i >= 0) this._cards[i] = updated;
      })
      .catch(() => {});
  }

  // ---------- editor ----------

  _openEditor(card) {
    const draft = card
      ? { ...card }
      : { name: "", number: "", format: "code128", color: COLORS[Math.floor(Math.random() * COLORS.length)], notes: "", favorite: false, front: null, back: null };
    // Pending image changes: undefined = unchanged, null = remove, string = new data URL
    const pending = { front: undefined, back: undefined };

    const bd = this._overlay(`
      <div class="dialog">
        <div class="dlg-head">
          <h2>${esc(this.t(card ? "editCard" : "newCard"))}</h2>
          <button class="icon-btn" data-act="close"><ha-icon icon="mdi:close"></ha-icon></button>
        </div>
        <div class="dlg-body">
          <label class="field"><span>${esc(this.t("name"))}</span>
            <input type="text" id="f-name" maxlength="100" value="${esc(draft.name)}" autocomplete="off"></label>
          <label class="field"><span>${esc(this.t("number"))}</span>
            <div class="row">
              <input type="text" id="f-number" maxlength="512" value="${esc(draft.number)}" autocomplete="off" inputmode="text">
              <button class="btn small" data-act="scan"><ha-icon icon="mdi:barcode-scan"></ha-icon>${esc(this.t("scan"))}</button>
            </div></label>
          <label class="field"><span>${esc(this.t("format"))}</span>
            <select id="f-format">${FORMATS.map(([v, l]) => `<option value="${v}">${esc(l)}</option>`).join("")}</select></label>
          <div class="barcode preview-code" id="f-code"></div>
          <div class="pics">
            ${["front", "back"]
              .map(
                (side) => `
              <div class="pic" data-side="${side}">
                <div class="title">${esc(this.t(side))}</div>
                <div class="prev" data-act="pick">${draft[side] ? `<img data-file="${esc(draft[side])}" alt="">` : `<ha-icon icon="mdi:camera-plus"></ha-icon>`}</div>
                <div class="acts">
                  <button class="btn small" data-act="pick">${esc(this.t("choose"))}</button>
                  <button class="btn small" data-act="read" ${draft[side] ? "" : "disabled"}>${esc(this.t("readCode"))}</button>
                  <button class="btn small" data-act="remove" ${draft[side] ? "" : "disabled"}>${esc(this.t("remove"))}</button>
                </div>
                <input type="file" accept="image/*" hidden>
              </div>`
              )
              .join("")}
          </div>
          <div class="field"><span style="display:block;font-size:12px;color:var(--secondary-text-color);margin-bottom:6px">${esc(this.t("color"))}</span>
            <div class="swatches" id="f-colors">
              ${COLORS.map((c) => `<button class="swatch" data-color="${c}" style="background:${c}"></button>`).join("")}
              <input type="color" id="f-color" value="${esc(draft.color)}">
            </div></div>
          <label class="check" style="margin-top:14px"><input type="checkbox" id="f-fav" ${draft.favorite ? "checked" : ""}> ${esc(this.t("favorite"))}</label>
          <label class="field"><span>${esc(this.t("notes"))}</span>
            <textarea id="f-notes" maxlength="2000">${esc(draft.notes)}</textarea></label>
        </div>
        <div class="dlg-foot">
          <button class="btn" data-act="cancel">${esc(this.t("cancel"))}</button>
          <button class="btn primary" data-act="save"><ha-icon icon="mdi:content-save"></ha-icon>${esc(this.t("save"))}</button>
        </div>
      </div>`);
    this._fillImages(bd);

    const $ = (sel) => bd.querySelector(sel);
    const fNumber = $("#f-number");
    const fFormat = $("#f-format");
    const fColor = $("#f-color");
    fFormat.value = draft.format;

    let previewTimer;
    const preview = () => {
      clearTimeout(previewTimer);
      previewTimer = setTimeout(() => this._drawBarcode($("#f-code"), fFormat.value, fNumber.value.trim()), 250);
    };
    const markColor = () => {
      bd.querySelectorAll(".swatch").forEach((s) => s.classList.toggle("sel", s.dataset.color.toLowerCase() === fColor.value.toLowerCase()));
    };
    const applyDetected = (res) => {
      fNumber.value = res.number;
      fFormat.value = res.format;
      preview();
      this._toast(`${this.t("codeFound")}: ${res.number}`);
    };
    fNumber.addEventListener("input", preview);
    fFormat.addEventListener("change", preview);
    fColor.addEventListener("input", markColor);
    preview();
    markColor();

    const setPic = (side, dataUrl) => {
      const pic = bd.querySelector(`.pic[data-side="${side}"]`);
      pic.querySelector(".prev").innerHTML = dataUrl ? `<img src="${dataUrl}" alt="">` : `<ha-icon icon="mdi:camera-plus"></ha-icon>`;
      pic.querySelector('[data-act="read"]').disabled = !dataUrl;
      pic.querySelector('[data-act="remove"]').disabled = !dataUrl;
    };

    bd.querySelectorAll(".pic input[type=file]").forEach((input) => {
      input.addEventListener("change", async () => {
        const file = input.files[0];
        input.value = "";
        if (!file) return;
        const side = input.closest(".pic").dataset.side;
        let bitmap;
        try {
          bitmap = await loadBitmap(file);
          const dataUrl = bitmapToDataUrl(bitmap);
          pending[side] = dataUrl;
          setPic(side, dataUrl);
          if (!fNumber.value.trim()) {
            const res = await detectIn(bitmap).catch(() => null);
            if (res) applyDetected(res);
          }
        } catch (err) {
          this._toast(err.message || String(err));
        } finally {
          if (bitmap) bitmap.close();
        }
      });
    });

    bd.addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-act]");
      if (!btn) {
        return;
      }
      const act = btn.dataset.act;
      const pic = btn.closest(".pic");
      if (act === "close" || act === "cancel") {
        if (card) this._openViewer(card.id, false);
        else this._closeOverlay();
      } else if (act === "pick") {
        pic.querySelector("input[type=file]").click();
      } else if (act === "remove") {
        pending[pic.dataset.side] = null;
        setPic(pic.dataset.side, null);
      } else if (act === "read") {
        const img = pic.querySelector(".prev img");
        if (!img) return;
        try {
          if (!img.complete) await new Promise((r) => img.addEventListener("load", r, { once: true }));
          const res = await detectIn(img);
          if (res) applyDetected(res);
          else this._toast(this.t("noCodeFound"));
        } catch (err) {
          this._toast(err.message || String(err));
        }
      } else if (act === "scan") {
        e.preventDefault();
        this._scan(applyDetected);
      } else if (act === "save") {
        btn.disabled = true;
        try {
          await this._save(card, {
            name: $("#f-name").value.trim(),
            number: fNumber.value.trim(),
            format: fFormat.value,
            color: fColor.value,
            notes: $("#f-notes").value,
            favorite: $("#f-fav").checked,
          }, pending);
        } finally {
          btn.disabled = false;
        }
      }
    });
    bd.querySelector("#f-colors").addEventListener("click", (e) => {
      const sw = e.target.closest(".swatch");
      if (!sw) return;
      e.preventDefault();
      fColor.value = sw.dataset.color;
      markColor();
    });
    if (!card) setTimeout(() => $("#f-name").focus(), 50);
  }

  async _save(card, fields, pending) {
    if (!fields.name) {
      this._toast(this.t("nameRequired"));
      return;
    }
    try {
      let saved = card
        ? await this._ws({ type: "bcc/cards/update", card_id: card.id, ...fields })
        : await this._ws({ type: "bcc/cards/create", ...fields });
      for (const side of ["front", "back"]) {
        if (pending[side] === undefined) continue;
        saved = await this._ws({ type: "bcc/cards/set_image", card_id: saved.id, side, image: pending[side] });
      }
      const i = this._cards.findIndex((c) => c.id === saved.id);
      if (i >= 0) this._cards[i] = saved;
      else this._cards.push(saved);
      this._renderGrid();
      this._toast(this.t("saved"));
      this._openViewer(saved.id, false);
    } catch (err) {
      this._toast(err.message || String(err));
    }
  }

  // ---------- bulk import ----------

  async _openImport(files) {
    const bd = this._overlay(`
      <div class="dialog">
        <div class="dlg-head">
          <h2>${esc(this.t("importTitle"))}</h2>
          <button class="icon-btn" data-act="close"><ha-icon icon="mdi:close"></ha-icon></button>
        </div>
        <div class="dlg-body">
          <p class="hint">${esc(this.t("importHint"))}</p>
          <div id="imp-status" class="hint"></div>
          <div id="imp-list"></div>
          <label class="check" style="margin-top:14px"><input type="checkbox" id="imp-attach"> ${esc(this.t("attachShot"))}</label>
        </div>
        <div class="dlg-foot">
          <button class="btn" data-act="close">${esc(this.t("cancel"))}</button>
          <button class="btn primary" data-act="import" disabled><ha-icon icon="mdi:import"></ha-icon><span>${esc(this.t("importBtn"))}</span></button>
        </div>
      </div>`);
    let closed = false;
    const rows = [];
    const list = bd.querySelector("#imp-list");
    const status = bd.querySelector("#imp-status");
    const importBtn = bd.querySelector('[data-act="import"]');
    const known = new Set(this._cards.map((c) => (c.number || "").trim()).filter(Boolean));

    const updateBtn = () => {
      const n = rows.filter((r) => r.el.querySelector(".inc").checked).length;
      importBtn.disabled = !n;
      importBtn.querySelector("span").textContent = `${this.t("importBtn")} (${n})`;
    };

    bd.addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-act]");
      if (!btn) return;
      if (btn.dataset.act === "close") {
        closed = true;
        this._closeOverlay();
      } else if (btn.dataset.act === "import") {
        importBtn.disabled = true;
        await this._runImport(rows, bd.querySelector("#imp-attach").checked);
        updateBtn();
      }
    });
    list.addEventListener("change", (e) => {
      if (!e.target.classList.contains("inc")) return;
      e.target.closest(".imp-row").classList.toggle("off", !e.target.checked);
      updateBtn();
    });

    for (const [i, file] of files.entries()) {
      if (closed) return;
      status.textContent = `${this.t("importReading")} ${i + 1}/${files.length}`;
      let bitmap;
      let dataUrl = null;
      let res = null;
      try {
        bitmap = await loadBitmap(file);
        dataUrl = bitmapToDataUrl(bitmap);
        res = await detectIn(bitmap).catch(() => null);
      } catch {
        /* unreadable image: skip it */
      } finally {
        if (bitmap) bitmap.close();
      }
      if (!dataUrl) continue;
      const dup = res && known.has(res.number);
      const include = !!res && !dup;
      const el = document.createElement("div");
      el.className = `imp-row${include ? "" : " off"}`;
      el.innerHTML = `
        <img src="${dataUrl}" alt="">
        <div class="fields">
          <div class="row"><input type="checkbox" class="inc" ${include ? "checked" : ""}>
            <input type="text" class="name" maxlength="100" value="${esc(`${this.t("card")} ${this._cards.length + rows.length + 1}`)}"></div>
          <input type="text" class="number" maxlength="512" value="${esc(res?.number || "")}" placeholder="${esc(this.t("number"))}">
          <select class="format">${FORMATS.map(([v, l]) => `<option value="${v}">${esc(l)}</option>`).join("")}</select>
          ${!res ? `<div class="msg">${esc(this.t("importNone"))}</div>` : dup ? `<div class="msg">${esc(this.t("importDup"))}</div>` : ""}
        </div>`;
      el.querySelector(".format").value = res?.format || "code128";
      list.appendChild(el);
      rows.push({ el, dataUrl });
      updateBtn();
    }
    status.hidden = true;
  }

  async _runImport(rows, attach) {
    let count = 0;
    for (const { el, dataUrl } of rows) {
      const inc = el.querySelector(".inc");
      if (!inc.checked) continue;
      const name = el.querySelector(".name").value.trim() || `${this.t("card")} ${this._cards.length + 1}`;
      try {
        let card = await this._ws({
          type: "bcc/cards/create",
          name,
          number: el.querySelector(".number").value.trim(),
          format: el.querySelector(".format").value,
          color: COLORS[this._cards.length % COLORS.length],
        });
        if (attach) card = await this._ws({ type: "bcc/cards/set_image", card_id: card.id, side: "back", image: dataUrl });
        this._cards.push(card);
        count += 1;
        // Mark as done so a retry after an error does not create duplicates
        inc.checked = false;
        el.classList.add("off");
      } catch (err) {
        this._renderGrid();
        this._toast(`${name}: ${err.message || err}`);
        return;
      }
    }
    this._closeOverlay();
    this._renderGrid();
    this._toast(`${count} ${this.t("importDone")}`);
  }

  // ---------- camera scanner ----------

  async _scan(onResult) {
    if (!navigator.mediaDevices?.getUserMedia) {
      this._toast(this.t("cameraError"));
      return;
    }
    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false,
      });
    } catch {
      this._toast(this.t("cameraError"));
      return;
    }
    const el = document.createElement("div");
    el.className = "scanner";
    el.innerHTML = `
      <video playsinline muted autoplay></video>
      <div class="frame"></div>
      <div class="bottom"><span class="spacer">${esc(this.t("scanHint"))}</span>
        <button class="btn">${esc(this.t("cancel"))}</button></div>`;
    this.shadowRoot.appendChild(el);
    const video = el.querySelector("video");
    video.srcObject = stream;

    let running = true;
    const stop = () => {
      running = false;
      stream.getTracks().forEach((t) => t.stop());
      el.remove();
      this._stopScan = null;
    };
    this._stopScan = stop;
    el.querySelector("button").addEventListener("click", stop);

    try {
      const detector = await getDetector();
      await video.play().catch(() => {});
      const loop = async () => {
        if (!running) return;
        try {
          if (video.readyState >= 2) {
            const found = await detector.detect(video);
            if (found.length && running) {
              const best = found[0];
              stop();
              onResult({ number: best.rawValue, format: DETECTED[best.format] || "code128" });
              return;
            }
          }
        } catch {
          /* keep trying */
        }
        setTimeout(loop, 200);
      };
      loop();
    } catch (err) {
      stop();
      this._toast(err.message || String(err));
    }
  }
}

if (!customElements.get("bcc-panel")) customElements.define("bcc-panel", BccPanel);
