package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TranscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranscriptionEntity): Long

    @Query("UPDATE transcriptions SET segmentsJson = :segmentsJson, fullTextPreview = :fullTextPreview WHERE id = :id")
    suspend fun updateSegments(id: Long, segmentsJson: String, fullTextPreview: String)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()
}
