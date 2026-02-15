package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_12_13"

/**
 * Migration from version 12 to 13
 * Adds workflow_state column to ConversationEntity for Workflow feature
 */
val Migration_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 12 to 13 (add workflow_state column)")
        db.beginTransaction()
        try {
            // Add workflow_state column to ConversationEntity
            db.execSQL(
                "ALTER TABLE ConversationEntity ADD COLUMN workflow_state TEXT NOT NULL DEFAULT ''"
            )

            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 12 to 13 success")
        } finally {
            db.endTransaction()
        }
    }
}
