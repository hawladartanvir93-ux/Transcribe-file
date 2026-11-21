package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val languageId: String,
    val languageLabel: String,
    val engineUsed: String,
    val durationMs: Long,
    val createdAtMs: Long,
    val segmentsJson: String,
    val waveformJson: String,
    val fullTextPreview: String,
)
