package com.example.stocksignal.data.local.repository

import com.example.stocksignal.data.local.dao.NotesDao
import com.example.stocksignal.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val notesDao: NotesDao
) {

    val notesFlow: Flow<List<NoteEntity>> = notesDao.observeNotes()

    suspend fun getNote(symbol: String): NoteEntity? {
        return notesDao.getNote(symbol)
    }

    suspend fun upsert(note: NoteEntity) {
        notesDao.upsert(note)
    }

    suspend fun delete(symbol: String) {
        notesDao.delete(symbol)
    }
}
