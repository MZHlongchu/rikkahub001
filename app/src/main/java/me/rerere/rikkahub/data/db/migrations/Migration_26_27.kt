package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(26, 27)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversation_compression_payload` (
                    `conversation_id` TEXT NOT NULL,
                    `dialogue_summary_text` TEXT NOT NULL DEFAULT '',
                    `rolling_summary_json` TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(`conversation_id`),
                    FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_conversation_compression_payload_conversation_id`
                ON `conversation_compression_payload` (`conversation_id`)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `conversation_compression_payload` (
                    `conversation_id`,
                    `dialogue_summary_text`,
                    `rolling_summary_json`
                )
                SELECT
                    `id`,
                    `dialogue_summary_text`,
                    `rolling_summary_json`
                FROM `ConversationEntity`
                WHERE `dialogue_summary_text` != '' OR `rolling_summary_json` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `compression_event_payload` (
                    `event_id` INTEGER NOT NULL,
                    `dialogue_summary_text` TEXT NOT NULL DEFAULT '',
                    `ledger_snapshot` TEXT NOT NULL DEFAULT '',
                    `summary_snapshot` TEXT NOT NULL DEFAULT '',
                    `base_dialogue_summary_text` TEXT NOT NULL DEFAULT '',
                    `base_ledger_json` TEXT NOT NULL DEFAULT '',
                    `base_summary_json` TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(`event_id`),
                    FOREIGN KEY(`event_id`) REFERENCES `compression_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_compression_event_payload_event_id`
                ON `compression_event_payload` (`event_id`)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `compression_event_payload` (
                    `event_id`,
                    `dialogue_summary_text`,
                    `ledger_snapshot`,
                    `summary_snapshot`,
                    `base_dialogue_summary_text`,
                    `base_ledger_json`,
                    `base_summary_json`
                )
                SELECT
                    `id`,
                    `dialogue_summary_text`,
                    `ledger_snapshot`,
                    `summary_snapshot`,
                    `base_dialogue_summary_text`,
                    `base_ledger_json`,
                    `base_summary_json`
                FROM `compression_event`
                WHERE
                    `dialogue_summary_text` != '' OR
                    `ledger_snapshot` != '' OR
                    `summary_snapshot` != '' OR
                    `base_dialogue_summary_text` != '' OR
                    `base_ledger_json` != '' OR
                    `base_summary_json` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE `ConversationEntity`
                SET
                    `dialogue_summary_text` = '',
                    `rolling_summary_json` = ''
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE `compression_event`
                SET
                    `dialogue_summary_text` = '',
                    `ledger_snapshot` = '',
                    `summary_snapshot` = '',
                    `base_dialogue_summary_text` = '',
                    `base_ledger_json` = '',
                    `base_summary_json` = ''
                """.trimIndent()
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
