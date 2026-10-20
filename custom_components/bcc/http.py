"""Authenticated HTTP view serving card images."""

from __future__ import annotations

from aiohttp import web

from homeassistant.components.http import HomeAssistantView

from .const import DOMAIN, IMAGE_URL


class BCCImageView(HomeAssistantView):
    """Serve a stored card image (requires auth or a signed path)."""

    url = IMAGE_URL + "/{filename}"
    name = "api:bcc:image"
    requires_auth = True

    async def get(self, request: web.Request, filename: str) -> web.StreamResponse:
        hass = request.app["hass"]
        store = hass.data.get(DOMAIN)
        if store is None:
            return web.Response(status=404)
        path = await hass.async_add_executor_job(store.image_path, filename)
        if path is None:
            return web.Response(status=404)
        # File names change on every upload, so they can be cached forever.
        return web.FileResponse(
            path, headers={"Cache-Control": "private, max-age=31536000, immutable"}
        )
