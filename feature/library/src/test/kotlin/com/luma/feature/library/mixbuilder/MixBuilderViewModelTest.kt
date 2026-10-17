package com.luma.feature.library.mixbuilder

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.luma.core.data.db.dao.LumaMixRecipeDao
import com.luma.core.data.db.entity.LumaMixRecipeEntity
import com.luma.core.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking

/**
 * Pins the [MixBuilderViewModel] contract for Task 4 of the customizable
 * LUMA-Mixes UI: create-mode insert produces a TAG_GRAPH custom recipe
 * from the form, edit-mode hydrates the form from an existing recipe, and
 * a blank-name form never inserts.
 *
 * Mirrors the in-repo harness (mockito-kotlin + StandardTestDispatcher,
 * no turbine). The WorkManager static enqueue inside `save()` throws in the
 * unit-test JVM (no WorkManager.getInstance); the DAO insert/update runs
 * BEFORE it, so the load-bearing assertions are on the captured entity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MixBuilderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `save inserts a new TAG_GRAPH custom recipe`() = runTest {
        val recipeDao = mock<LumaMixRecipeDao> {
            onBlocking { insert(any()) } doReturn 7L
        }
        val vm = buildVm(recipeDao = recipeDao, savedStateHandle = SavedStateHandle())

        // Collect the saved one-shot before save() runs. save()'s viewModelScope
        // coroutine guards its LumaMixRefreshWorker.enqueueOneTime call in
        // runCatching (the unit-test JVM has no initialized WorkManager — the
        // same untested static seam as HomeViewModel.refreshMix), so it never
        // throws and the emission fires.
        val emissions = mutableListOf<Unit>()
        val collectJob = launch { vm.saved.collect { emissions.add(it) } }
        runCurrent()

        vm.setName("Late Night Jazz")
        vm.toggleGenre("jazz")
        vm.toggleMood("chill")
        vm.save()
        advanceUntilIdle()

        // The captured entity is the load-bearing behavior under test.
        val captor = argumentCaptor<LumaMixRecipeEntity>()
        verifyBlocking(recipeDao) { insert(captor.capture()) }
        val inserted = captor.firstValue
        assertThat(inserted.name).isEqualTo("Late Night Jazz")
        assertThat(inserted.includeTagsCsv).isEqualTo("jazz")
        assertThat(inserted.moodKeysCsv).isEqualTo("chill")
        assertThat(inserted.seedStrategy).isEqualTo("TAG_GRAPH")
        assertThat(inserted.isBuiltin).isFalse()
        // The saved one-shot fired after the insert + guarded enqueue.
        assertThat(emissions).isNotEmpty()
        collectJob.cancel()
    }

    @Test
    fun `edit mode loads existing recipe into the form`() = runTest {
        val existing = LumaMixRecipeEntity(
            id = 42L,
            name = "Old",
            includeTagsCsv = "rock",
        )
        val recipeDao = mock<LumaMixRecipeDao> {
            onBlocking { getById(42L) } doReturn existing
        }
        val vm = buildVm(
            recipeDao = recipeDao,
            savedStateHandle = SavedStateHandle(mapOf("recipeId" to 42L)),
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isEditing).isTrue()
        assertThat(state.form.name).isEqualTo("Old")
        assertThat(state.form.genreTags).contains("rock")
    }

    @Test
    fun `save with no genre or mood does not insert`() = runTest {
        val recipeDao = mock<LumaMixRecipeDao>()
        val vm = buildVm(recipeDao = recipeDao, savedStateHandle = SavedStateHandle())

        vm.setName("My Mix") // a name but no ingredients → invalid
        vm.save()
        advanceUntilIdle()

        verifyBlocking(recipeDao, never()) { insert(any()) }
    }

    @Test
    fun `save with a genre and blank name inserts (name is optional)`() = runTest {
        val recipeDao = mock<LumaMixRecipeDao> { onBlocking { insert(any()) } doReturn 5L }
        val vm = buildVm(recipeDao = recipeDao, savedStateHandle = SavedStateHandle())

        vm.toggleGenre("jazz") // no name, but a genre → valid (auto-named)
        vm.save()
        advanceUntilIdle()

        verifyBlocking(recipeDao) { insert(any()) }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildVm(
        recipeDao: LumaMixRecipeDao = mock(),
        musicRepository: MusicRepository = mock(),
        downloadNetworkPreference: com.luma.core.data.prefs.DownloadNetworkPreference = mock(),
        context: Context = mock(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): MixBuilderViewModel = MixBuilderViewModel(
        savedStateHandle = savedStateHandle,
        recipeDao = recipeDao,
        musicRepository = musicRepository,
        downloadNetworkPreference = downloadNetworkPreference,
        context = context,
    )
}
