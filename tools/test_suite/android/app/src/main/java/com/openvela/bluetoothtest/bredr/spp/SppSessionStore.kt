package com.openvela.bluetoothtest.bredr.spp

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SppSessionStore(context: Context) {
    data class Record(
        val id: Int,
        val title: String,
        val serviceUuid: String,
        val remoteAddress: String,
        val dataToSend: String,
        val cycles: String,
        val log: String
    )

    private val helper = object : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                    $COL_ID INTEGER PRIMARY KEY,
                    $COL_TITLE TEXT NOT NULL,
                    $COL_SERVICE_UUID TEXT NOT NULL,
                    $COL_REMOTE_ADDRESS TEXT NOT NULL,
                    $COL_DATA_TO_SEND TEXT NOT NULL,
                    $COL_CYCLES TEXT NOT NULL,
                    $COL_LOG TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No schema changes yet.
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun loadSessions(): List<Record> {
        return executor.submit(Callable {
            val db = helper.readableDatabase
            val cursor = db.query(
                TABLE_SESSIONS,
                arrayOf(
                    COL_ID,
                    COL_TITLE,
                    COL_SERVICE_UUID,
                    COL_REMOTE_ADDRESS,
                    COL_DATA_TO_SEND,
                    COL_CYCLES,
                    COL_LOG
                ),
                null,
                null,
                null,
                null,
                "$COL_ID ASC"
            )
            cursor.use { c ->
                val records = mutableListOf<Record>()
                while (c.moveToNext()) {
                    records += c.toRecord()
                }
                records
            }
        }).get()
    }

    fun upsert(record: Record) {
        executor.execute {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put(COL_ID, record.id)
                put(COL_TITLE, record.title)
                put(COL_SERVICE_UUID, record.serviceUuid)
                put(COL_REMOTE_ADDRESS, record.remoteAddress)
                put(COL_DATA_TO_SEND, record.dataToSend)
                put(COL_CYCLES, record.cycles)
                put(COL_LOG, record.log)
            }
            db.insertWithOnConflict(TABLE_SESSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun delete(id: Int) {
        executor.execute {
            val db = helper.writableDatabase
            db.delete(TABLE_SESSIONS, "$COL_ID = ?", arrayOf(id.toString()))
        }
    }

    fun clear() {
        executor.execute {
            val db = helper.writableDatabase
            db.delete(TABLE_SESSIONS, null, null)
        }
    }

    fun close() {
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
        helper.close()
    }

    private fun Cursor.toRecord(): Record = Record(
        id = getInt(getColumnIndexOrThrow(COL_ID)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        serviceUuid = getString(getColumnIndexOrThrow(COL_SERVICE_UUID)),
        remoteAddress = getString(getColumnIndexOrThrow(COL_REMOTE_ADDRESS)),
        dataToSend = getString(getColumnIndexOrThrow(COL_DATA_TO_SEND)),
        cycles = getString(getColumnIndexOrThrow(COL_CYCLES)),
        log = getString(getColumnIndexOrThrow(COL_LOG))
    )

    companion object {
        private const val DATABASE_NAME = "spp_sessions.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SESSIONS = "sessions"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_SERVICE_UUID = "service_uuid"
        private const val COL_REMOTE_ADDRESS = "remote_address"
        private const val COL_DATA_TO_SEND = "data_to_send"
        private const val COL_CYCLES = "cycles"
        private const val COL_LOG = "log"
    }
}
