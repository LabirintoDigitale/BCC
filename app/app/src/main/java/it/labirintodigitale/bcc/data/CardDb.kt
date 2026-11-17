package it.labirintodigitale.bcc.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Local copy of the cards, used to open the app without a connection.
 *
 * Each card is stored as the JSON object returned by Home Assistant, so new
 * fields added by the integration survive a round trip without a migration.
 */
class CardDb(context: Context) : SQLiteOpenHelper(context, "bcc.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cards (id TEXT PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadCards(): List<Card> {
        val out = ArrayList<Card>()
        readableDatabase.rawQuery("SELECT json FROM cards", null).use { c ->
            while (c.moveToNext()) {
                runCatching { Card.fromJson(JSONObject(c.getString(0))) }.onSuccess(out::add)
            }
        }
        return out
    }

    /** Replace the whole local copy with a fresh list from the server. */
    fun replaceAll(cards: List<Card>, syncedAt: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cards", null, null)
            for (card in cards) db.insert("cards", null, values(card))
            setMeta(db, KEY_LAST_SYNC, syncedAt.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsert(card: Card) {
        writableDatabase.insertWithOnConflict("cards", null, values(card), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(cardId: String) {
        writableDatabase.delete("cards", "id = ?", arrayOf(cardId))
    }

    fun clear() {
        val db = writableDatabase
        db.delete("cards", null, null)
        db.delete("meta", null, null)
    }

    /** Epoch millis of the last complete download, or null if never synced. */
    fun lastSync(): Long? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(KEY_LAST_SYNC)).use { c ->
            if (c.moveToFirst()) c.getString(0)?.toLongOrNull() else null
        }

    private fun setMeta(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            "meta", null,
            ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun values(card: Card) = ContentValues().apply {
        put("id", card.id)
        put("json", card.toJson().toString())
    }

    private companion object {
        const val KEY_LAST_SYNC = "last_sync"
    }
}
