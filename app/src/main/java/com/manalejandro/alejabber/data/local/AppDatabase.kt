package com.manalejandro.alejabber.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.manalejandro.alejabber.data.local.dao.AccountDao
import com.manalejandro.alejabber.data.local.dao.ContactDao
import com.manalejandro.alejabber.data.local.dao.MessageDao
import com.manalejandro.alejabber.data.local.dao.RoomDao
import com.manalejandro.alejabber.data.local.entity.AccountEntity
import com.manalejandro.alejabber.data.local.entity.ContactEntity
import com.manalejandro.alejabber.data.local.entity.MessageEntity
import com.manalejandro.alejabber.data.local.entity.RoomEntity

@Database(
    entities = [
        AccountEntity::class,
        ContactEntity::class,
        MessageEntity::class,
        RoomEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun roomDao(): RoomDao

    companion object {
        /**
         * Migration 1 → 2:
         *
         * Adds a unique composite index on (accountId, jid) in the contacts table.
         * Before creating the index, existing duplicate rows (same accountId + jid)
         * are removed, keeping only the row with the highest presence rank (most available).
         *
         * This fixes the crash:
         *   java.lang.IllegalArgumentException: Key "<jid>" was already used
         * that occurred in ContactsScreen's LazyColumn when the roster sync
         * inserted duplicate rows for the same contact.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Remove duplicates — keep only the row with the lowest id (oldest entry)
                //    for each (accountId, jid) pair.
                db.execSQL(
                    """
                    DELETE FROM contacts
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM contacts
                        GROUP BY accountId, jid
                    )
                    """.trimIndent()
                )
                // 2. Create the unique composite index.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_contacts_accountId_jid " +
                            "ON contacts (accountId, jid)"
                )
            }
        }
    }
}
