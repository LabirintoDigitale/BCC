package it.labirintodigitale.bcc.data

import org.json.JSONObject

/** A loyalty card as returned by the BCC integration (`bcc/cards/list`). */
data class Card(
    val id: String,
    val name: String,
    val number: String,
    val format: String,
    val color: String,
    val notes: String,
    val favorite: Boolean,
    val preview: String,
    val front: String?,
    val back: String?,
    val useCount: Int,
    val lastUsed: Double?,
    val created: Double?,
    val updated: Double?,
) {
    fun image(side: String): String? = if (side == "back") back else front

    /** File shown on the grid tile, or null for colour + name only. */
    val previewImage: String?
        get() = when (preview) {
            "none" -> null
            "back" -> back
            else -> front
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("number", number)
        put("format", format)
        put("color", color)
        put("notes", notes)
        put("favorite", favorite)
        put("preview", preview)
        put("front", front ?: JSONObject.NULL)
        put("back", back ?: JSONObject.NULL)
        put("use_count", useCount)
        put("last_used", lastUsed ?: JSONObject.NULL)
        put("created", created ?: JSONObject.NULL)
        put("updated", updated ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject) = Card(
            id = o.getString("id"),
            name = o.optString("name"),
            number = o.optString("number"),
            format = o.optString("format", "code128"),
            color = o.optString("color", "#03a9f4"),
            notes = o.optString("notes"),
            favorite = o.optBoolean("favorite"),
            preview = o.optString("preview", "front"),
            front = o.optStringOrNull("front"),
            back = o.optStringOrNull("back"),
            useCount = o.optInt("use_count"),
            lastUsed = o.optDoubleOrNull("last_used"),
            created = o.optDoubleOrNull("created"),
            updated = o.optDoubleOrNull("updated"),
        )
    }
}

/** Fields the user can edit; sent to `bcc/cards/create` and `bcc/cards/update`. */
data class CardFields(
    val name: String,
    val number: String,
    val format: String,
    val color: String,
    val notes: String,
    val favorite: Boolean,
    val preview: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name.trim())
        put("number", number.trim())
        put("format", format)
        put("color", color)
        put("notes", notes)
        put("favorite", favorite)
        put("preview", preview)
    }

    companion object {
        fun from(card: Card) = CardFields(
            card.name, card.number, card.format, card.color, card.notes, card.favorite, card.preview,
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

val BARCODE_FORMATS = listOf(
    "code128", "ean13", "ean8", "upca", "upce", "code39", "code93",
    "interleaved2of5", "itf14", "rationalizedCodabar",
    "qrcode", "pdf417", "azteccode", "datamatrix", "none",
)

val FORMAT_LABELS = mapOf(
    "code128" to "Code 128",
    "ean13" to "EAN-13",
    "ean8" to "EAN-8",
    "upca" to "UPC-A",
    "upce" to "UPC-E",
    "code39" to "Code 39",
    "code93" to "Code 93",
    "interleaved2of5" to "ITF (Interleaved 2 of 5)",
    "itf14" to "ITF-14",
    "rationalizedCodabar" to "Codabar",
    "qrcode" to "QR Code",
    "pdf417" to "PDF417",
    "azteccode" to "Aztec",
    "datamatrix" to "Data Matrix",
    "none" to "—",
)
