"""Persistent storage for cards and their images."""

from __future__ import annotations

import base64
import binascii
import os
import re
import secrets
import time
import uuid
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .const import IMAGE_DIR, MAX_IMAGE_BYTES, SIDES, STORAGE_KEY, STORAGE_VERSION

DATA_URL_RE = re.compile(r"^data:(image/(?:jpeg|png|webp));base64,(.+)$", re.DOTALL)
IMAGE_NAME_RE = re.compile(r"^[a-f0-9]{32}_(front|back)_[a-f0-9]{8}\.(jpg|png|webp)$")

MIME_EXT = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}
MAGIC = {
    "jpg": (b"\xff\xd8\xff",),
    "png": (b"\x89PNG\r\n\x1a\n",),
    "webp": (b"RIFF",),
}

EDITABLE_FIELDS = ("name", "number", "format", "color", "notes", "favorite")


class CardError(Exception):
    """Raised for invalid card operations."""


class CardStore:
    """Store cards in .storage and images in <config>/bcc_images."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store[dict[str, Any]] = Store(hass, STORAGE_VERSION, STORAGE_KEY)
        self.image_dir = hass.config.path(IMAGE_DIR)
        self.cards: dict[str, dict[str, Any]] = {}

    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        self.cards = {card["id"]: card for card in data.get("cards", [])}
        await self.hass.async_add_executor_job(os.makedirs, self.image_dir, 0o755, True)

    async def _async_save(self) -> None:
        await self._store.async_save({"cards": list(self.cards.values())})

    def list(self) -> list[dict[str, Any]]:
        return sorted(self.cards.values(), key=lambda c: c["name"].casefold())

    def get(self, card_id: str) -> dict[str, Any]:
        if card_id not in self.cards:
            raise CardError(f"Card {card_id} not found")
        return self.cards[card_id]

    async def async_create(self, fields: dict[str, Any]) -> dict[str, Any]:
        now = time.time()
        card = {
            "id": uuid.uuid4().hex,
            "name": "",
            "number": "",
            "format": "code128",
            "color": "#03a9f4",
            "notes": "",
            "favorite": False,
            "front": None,
            "back": None,
            "use_count": 0,
            "last_used": None,
            "created": now,
            "updated": now,
        }
        card.update({k: v for k, v in fields.items() if k in EDITABLE_FIELDS})
        self.cards[card["id"]] = card
        await self._async_save()
        return card

    async def async_update(self, card_id: str, fields: dict[str, Any]) -> dict[str, Any]:
        card = self.get(card_id)
        card.update({k: v for k, v in fields.items() if k in EDITABLE_FIELDS})
        card["updated"] = time.time()
        await self._async_save()
        return card

    async def async_mark_used(self, card_id: str) -> dict[str, Any]:
        card = self.get(card_id)
        card["use_count"] = card.get("use_count", 0) + 1
        card["last_used"] = time.time()
        await self._async_save()
        return card

    async def async_delete(self, card_id: str) -> None:
        card = self.get(card_id)
        for side in SIDES:
            if card.get(side):
                await self._async_remove_file(card[side])
        del self.cards[card_id]
        await self._async_save()

    async def async_set_image(
        self, card_id: str, side: str, data_url: str | None
    ) -> dict[str, Any]:
        card = self.get(card_id)
        if side not in SIDES:
            raise CardError(f"Invalid side {side}")

        new_name = None
        if data_url:
            match = DATA_URL_RE.match(data_url)
            if not match:
                raise CardError("Unsupported image format (use JPEG, PNG or WebP)")
            ext = MIME_EXT[match.group(1)]
            try:
                raw = base64.b64decode(match.group(2), validate=True)
            except (binascii.Error, ValueError) as err:
                raise CardError("Invalid image data") from err
            if len(raw) > MAX_IMAGE_BYTES:
                raise CardError("Image too large")
            if not raw.startswith(MAGIC[ext]):
                raise CardError("Image content does not match its type")
            new_name = f"{card_id}_{side}_{secrets.token_hex(4)}.{ext}"
            await self.hass.async_add_executor_job(self._write_file, new_name, raw)

        old_name = card.get(side)
        card[side] = new_name
        card["updated"] = time.time()
        await self._async_save()
        if old_name:
            await self._async_remove_file(old_name)
        return card

    def image_path(self, filename: str) -> str | None:
        """Return the absolute path of an image if the name is valid and exists."""
        if not IMAGE_NAME_RE.match(filename):
            return None
        path = os.path.join(self.image_dir, filename)
        return path if os.path.isfile(path) else None

    def _write_file(self, filename: str, raw: bytes) -> None:
        with open(os.path.join(self.image_dir, filename), "wb") as file:
            file.write(raw)

    async def _async_remove_file(self, filename: str) -> None:
        if not IMAGE_NAME_RE.match(filename):
            return
        path = os.path.join(self.image_dir, filename)

        def _remove() -> None:
            if os.path.isfile(path):
                os.remove(path)

        await self.hass.async_add_executor_job(_remove)
