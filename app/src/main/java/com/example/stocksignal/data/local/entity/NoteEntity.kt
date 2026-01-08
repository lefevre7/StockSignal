package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val symbol: String,
    val content: String,
    val updatedAt: LocalDateTime
)
