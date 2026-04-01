package com.example.stocksignal.ui.notes

import android.util.Log
import com.example.stocksignal.data.local.entity.NoteEntity
import com.example.stocksignal.data.local.repository.NotesRepository
import com.example.stocksignal.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val notesRepository = mockk<NotesRepository>()
    private val notesFlow = MutableStateFlow(emptyList<NoteEntity>())

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { notesRepository.notesFlow } returns notesFlow
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `save note trims uppercases and delete clears state`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { notesRepository.upsert(any()) } answers {
            notesFlow.value = listOf(firstArg())
        }
        coEvery { notesRepository.delete("AAPL") } answers {
            notesFlow.value = emptyList()
        }

        val viewModel = NotesViewModel(notesRepository)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.saveNote(" aapl ", "  buy the dip  ")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.notes.size)
        assertEquals("AAPL", viewModel.uiState.value.notes.single().symbol)
        assertEquals("buy the dip", viewModel.uiState.value.notes.single().content)
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.deleteNote(" aapl ")
        advanceUntilIdle()
        assertEquals(emptyList<NoteEntity>(), viewModel.uiState.value.notes)
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.saveNote(" ", "ignored")
        viewModel.saveNote("AAPL", " ")
        advanceUntilIdle()
        coVerify(exactly = 1) { notesRepository.upsert(any()) }

        collector.cancel()
    }

    @Test
    fun `save and delete failures surface error and clearError removes it`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { notesRepository.upsert(any()) } throws IllegalStateException("write failed")
        coEvery { notesRepository.delete("AAPL") } throws IllegalStateException("delete failed")

        val viewModel = NotesViewModel(notesRepository)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.saveNote("AAPL", "watch support")
        advanceUntilIdle()
        assertEquals("Failed to save note: write failed", viewModel.uiState.value.errorMessage)

        viewModel.deleteNote("AAPL")
        advanceUntilIdle()
        assertEquals("Failed to delete note: delete failed", viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)

        collector.cancel()
    }
}
