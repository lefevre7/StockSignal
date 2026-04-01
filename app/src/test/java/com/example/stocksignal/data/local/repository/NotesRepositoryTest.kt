package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.NotesDao
import com.example.stocksignal.data.local.entity.NoteEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class NotesRepositoryTest {

    private lateinit var dao: NotesDao
    private lateinit var repository: NotesRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk(relaxed = true)
        repository = NotesRepository(dao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun repoWith(notes: List<NoteEntity>): NotesRepository {
        val freshDao = mockk<NotesDao>(relaxed = true)
        every { freshDao.observeNotes() } returns flowOf(notes)
        return NotesRepository(freshDao)
    }

    private fun repoWithFlowError(): NotesRepository {
        val freshDao = mockk<NotesDao>(relaxed = true)
        every { freshDao.observeNotes() } returns flow { throw RuntimeException("db error") }
        return NotesRepository(freshDao)
    }

    @Test
    fun `notesFlow emits notes from dao`() = runTest {
        val now = LocalDateTime.of(2026, 3, 31, 10, 0)
        val notes = listOf(NoteEntity("AAPL", "Watch support", now))
        assertEquals(notes, repoWith(notes).notesFlow.first())
    }

    @Test
    fun `notesFlow emits empty list on dao error`() = runTest {
        assertTrue(repoWithFlowError().notesFlow.first().isEmpty())
    }

    @Test
    fun `getNote returns note from dao`() = runTest {
        val note = NoteEntity("AAPL", "content", LocalDateTime.now())
        coEvery { dao.getNote("AAPL") } returns note
        assertEquals(note, repository.getNote("AAPL"))
    }

    @Test
    fun `getNote returns null on exception`() = runTest {
        coEvery { dao.getNote(any()) } throws RuntimeException("db error")
        assertNull(repository.getNote("AAPL"))
    }

    @Test
    fun `upsert delegates to dao`() = runTest {
        val note = NoteEntity("AAPL", "content", LocalDateTime.now())
        coEvery { dao.upsert(note) } returns Unit
        repository.upsert(note)
        coVerify(exactly = 1) { dao.upsert(note) }
    }

    @Test
    fun `upsert rethrows dao exception`() = runTest {
        coEvery { dao.upsert(any()) } throws RuntimeException("db error")
        var thrown: Exception? = null
        try { repository.upsert(NoteEntity("AAPL", "content", LocalDateTime.now())) }
        catch (e: Exception) { thrown = e }
        assertTrue(thrown is RuntimeException)
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        coEvery { dao.delete("AAPL") } returns Unit
        repository.delete("AAPL")
        coVerify(exactly = 1) { dao.delete("AAPL") }
    }

    @Test
    fun `delete rethrows dao exception`() = runTest {
        coEvery { dao.delete(any()) } throws RuntimeException("db error")
        var thrown: Exception? = null
        try { repository.delete("AAPL") }
        catch (e: Exception) { thrown = e }
        assertTrue(thrown is RuntimeException)
    }
}
