package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(25, 26)
        try {
            // Version 26 keeps the main schema stable and moves heavy index payloads
            // to the dedicated index database managed outside Room.
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
