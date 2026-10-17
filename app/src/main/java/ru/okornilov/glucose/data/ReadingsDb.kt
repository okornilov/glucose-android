package ru.okornilov.glucose.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** История показаний: LibreView отдаёт только ~12 часов, поэтому копим сами. */
class ReadingsDb(context: Context) : SQLiteOpenHelper(context, "readings.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE readings (ts INTEGER PRIMARY KEY, mgdl INTEGER NOT NULL, trend INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(readings: List<Reading>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement("INSERT OR IGNORE INTO readings (ts, mgdl, trend) VALUES (?, ?, ?)")
            for (r in readings) {
                st.clearBindings()
                st.bindLong(1, r.ts)
                st.bindLong(2, r.mgdl.toLong())
                if (r.trend != null) st.bindLong(3, r.trend.toLong()) else st.bindNull(3)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun latest(): Reading? = query("SELECT ts, mgdl, trend FROM readings ORDER BY ts DESC LIMIT 1").firstOrNull()

    fun between(from: Long, to: Long): List<Reading> =
        query("SELECT ts, mgdl, trend FROM readings WHERE ts >= ? AND ts < ? ORDER BY ts", from.toString(), to.toString())

    fun deleteBefore(ts: Long) {
        writableDatabase.delete("readings", "ts < ?", arrayOf(ts.toString()))
    }

    private fun query(sql: String, vararg args: String): List<Reading> =
        readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Reading(c.getLong(0), c.getInt(1), if (c.isNull(2)) null else c.getInt(2)))
                }
            }
        }
}
