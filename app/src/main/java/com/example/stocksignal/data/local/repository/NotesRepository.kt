package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.NotesDao
import com.example.stocksignal.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val notesDao: NotesDao
) {

    val notesFlow: Flow<List<NoteEntity>> = notesDao.observeNotes()
        .catch { e ->
            Log.e(TAG, "Error observing notes", e)
            emit(emptyList())
        }

    suspend fun getNote(symbol: String): NoteEntity? {
        return try {
            notesDao.getNote(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting note for symbol: $symbol", e)
            null
        }
    }

    suspend fun upsert(note: NoteEntity) {
        try {
            notesDao.upsert(note)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting note for symbol: ${note.symbol}", e)
            throw e
        }
    }

    suspend fun delete(symbol: String) {
        try {
            notesDao.delete(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note for symbol: $symbol", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "NotesRepository"
    }
}
