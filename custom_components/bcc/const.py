"""Constants for BarCodeCards."""

DOMAIN = "bcc"
NAME = "BarCodeCards"
VERSION = "1.3.0"

PANEL_URL_PATH = "bcc"
PANEL_COMPONENT = "bcc-panel"
PANEL_TITLE = "BarCodeCards"
PANEL_ICON = "mdi:barcode-scan"

STATIC_URL = "/bcc_static"
IMAGE_URL = "/api/bcc/image"

STORAGE_KEY = "bcc.cards"
STORAGE_VERSION = 1
IMAGE_DIR = "bcc_images"

MAX_IMAGE_BYTES = 4 * 1024 * 1024

DATA_REGISTERED = f"{DOMAIN}_registered"

SIDES = ("front", "back")
PREVIEWS = ("none", "front", "back")

BARCODE_FORMATS = (
    "code128",
    "ean13",
    "ean8",
    "upca",
    "upce",
    "code39",
    "code93",
    "interleaved2of5",
    "itf14",
    "rationalizedCodabar",
    "qrcode",
    "pdf417",
    "azteccode",
    "datamatrix",
    "none",
)
