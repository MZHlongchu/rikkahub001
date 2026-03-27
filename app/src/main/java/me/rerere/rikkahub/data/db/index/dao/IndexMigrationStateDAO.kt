package me.rerere.rikkahub.data.db.index.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.index.entity.IndexMigrationStateEntity

@Dao
interface IndexMigrationStateDAO {
    @Query("SELECT * FROM index_migration_state WHERE id = 1 LIMIT 1")
    suspend fun getState(): IndexMigrationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: IndexMigrationStateEntity)
}
