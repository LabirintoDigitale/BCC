"""WebSocket API used by the BarCodeCards panel."""

from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.core import HomeAssistant, callback

from .const import BARCODE_FORMATS, DOMAIN, SIDES
from .store import CardError, CardStore

CARD_FIELDS = {
    vol.Optional("number"): vol.All(str, vol.Length(max=512)),
    vol.Optional("format"): vol.In(BARCODE_FORMATS),
    vol.Optional("color"): vol.All(str, vol.Match(r"^#[0-9a-fA-F]{6}$")),
    vol.Optional("notes"): vol.All(str, vol.Length(max=2000)),
    vol.Optional("favorite"): bool,
}
NAME = vol.All(str, vol.Strip, vol.Length(min=1, max=100))


@callback
def async_register_websocket(hass: HomeAssistant) -> None:
    for handler in (
        ws_list,
        ws_create,
        ws_update,
        ws_delete,
        ws_set_image,
        ws_used,
    ):
        websocket_api.async_register_command(hass, handler)


def _store(hass: HomeAssistant, connection, msg) -> CardStore | None:
    store = hass.data.get(DOMAIN)
    if store is None:
        connection.send_error(msg["id"], "not_loaded", "BarCodeCards is not set up")
    return store


async def _run(hass, connection, msg, coro_factory) -> None:
    if (store := _store(hass, connection, msg)) is None:
        return
    try:
        result = await coro_factory(store)
    except CardError as err:
        connection.send_error(msg["id"], "invalid", str(err))
        return
    connection.send_result(msg["id"], result)


@websocket_api.websocket_command({vol.Required("type"): "bcc/cards/list"})
@websocket_api.async_response
async def ws_list(hass: HomeAssistant, connection, msg: dict[str, Any]) -> None:
    async def _do(store: CardStore):
        return {"cards": store.list()}

    await _run(hass, connection, msg, _do)


@websocket_api.websocket_command(
    {vol.Required("type"): "bcc/cards/create", vol.Required("name"): NAME, **CARD_FIELDS}
)
@websocket_api.async_response
async def ws_create(hass: HomeAssistant, connection, msg: dict[str, Any]) -> None:
    fields = {k: v for k, v in msg.items() if k not in ("id", "type")}
    await _run(hass, connection, msg, lambda store: store.async_create(fields))


@websocket_api.websocket_command(
    {
        vol.Required("type"): "bcc/cards/update",
        vol.Required("card_id"): str,
        vol.Optional("name"): NAME,
        **CARD_FIELDS,
    }
)
@websocket_api.async_response
async def ws_update(hass: HomeAssistant, connection, msg: dict[str, Any]) -> None:
    fields = {k: v for k, v in msg.items() if k not in ("id", "type", "card_id")}
    await _run(
        hass, connection, msg, lambda store: store.async_update(msg["card_id"], fields)
    )


@websocket_api.websocket_command(
    {vol.Required("type"): "bcc/cards/delete", vol.Required("card_id"): str}
)
@websocket_api.async_response
async def ws_delete(hass: HomeAssistant, connection, msg: dict[str, Any]) -> None:
    async def _do(store: CardStore):
        await store.async_delete(msg["card_id"])
        return None

    await _run(hass, connection, msg, _do)


@websocket_api.websocket_command(
    {
        vol.Required("type"): "bcc/cards/set_image",
        vol.Required("card_id"): str,
        vol.Required("side"): vol.In(SIDES),
        vol.Required("image"): vol.Any(None, str),
    }
)
@websocket_api.async_response
async def ws_set_image(hass: HomeAssistant, connection, msg: dict[str, Any]) -> None:
    await _run(
        hass,
        connection,
        msg,
        lambda store: store.async_set_image(msg["card_id"], msg["side"], msg["image"]),
    )


@websocket_api.websocket_command(
    {vol.Required("type"): "bcc/cards/used", vol.Required("card_id"): str}
)
@websocket_api.async_response
async def ws_used(hass: HomeAssistant, connection, msg: dict[str, Any]) -> None:
    await _run(
        hass, connection, msg, lambda store: store.async_mark_used(msg["card_id"])
    )
