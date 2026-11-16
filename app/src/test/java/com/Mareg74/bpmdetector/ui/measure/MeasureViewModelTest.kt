package com.Mareg74.bpmdetector.ui.measure

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.Mareg74.bpmdetector.audio.AudioError
import com.Mareg74.bpmdetector.audio.AudioFrame
import com.Mareg74.bpmdetector.audio.AudioInput
import com.Mareg74.bpmdetector.audio.AudioInputFactory
import com.Mareg74.bpmdetector.audio.AudioSourceKind
import com.Mareg74.bpmdetector.data.SettingsStore
import com.Mareg74.bpmdetector.domain.BpmEngine
import com.Mareg74.bpmdetector.domain.GenreCatalog
import com.Mareg74.bpmdetector.domain.MeasureState
import com.Mareg74.bpmdetector.domain.SessionStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class MeasureViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun permissionDenied_showsBannerAndIdle() =
        runTest(testDispatcher) {
            val fakeInput = FakeAudioInput(denyOnStart = true)
            val vm = viewModel(fakeInput, backgroundScope)
            advanceUntilIdle()

            vm.startMeasurement()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.state).isEqualTo(MeasureState.Idle)
            assertThat(state.errorBanner).isNotNull()
            assertThat(fakeInput.started).isFalse()
        }

    @Test
    fun stopMeasurement_freezesSessionAndIdles() =
        runTest(testDispatcher) {
            val fakeInput = FakeAudioInput()
            val vm = viewModel(fakeInput, backgroundScope)
            advanceUntilIdle()

            vm.startMeasurement()
            advanceUntilIdle()
            assertThat(vm.uiState.value.state).isEqualTo(MeasureState.Starting)

            vm.stopMeasurement()
            advanceUntilIdle()

            assertThat(vm.uiState.value.state).isEqualTo(MeasureState.Idle)
            assertThat(vm.uiState.value.statusLabel).isNull()
            assertThat(fakeInput.started).isFalse()
        }

    private suspend fun TestScope.viewModel(
        fakeInput: FakeAudioInput,
        scope: CoroutineScope,
    ): MeasureViewModel {
        val store =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    File.createTempFile("settings", ".preferences_pb")
                },
            )
        store.update { it.copy(genreId = "wide") }
        return MeasureViewModel(
            settingsStore = store,
            audioInputFactory =
                AudioInputFactory(
                    context = object : android.content.ContextWrapper(null) {},
                ),
            engine = BpmEngine(GenreCatalog.byId("wide")),
            session = SessionStore(),
            createInput = { fakeInput },
            availabilityProbe = { mapOf(AudioSourceKind.Mic to true) },
        )
    }

    private class FakeAudioInput(
        private val denyOnStart: Boolean = false,
    ) : AudioInput {
        private val _frames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 8)
        private val _errors = MutableSharedFlow<AudioError>(extraBufferCapacity = 4)
        var started = false

        override val frames: Flow<AudioFrame> = _frames
        override val errors: SharedFlow<AudioError> = _errors.asSharedFlow()

        override suspend fun start() {
            if (denyOnStart) {
                _errors.emit(AudioError.PermissionDenied)
                return
            }
            started = true
        }

        override suspend fun stop() {
            started = false
        }

        override fun isAvailable(): Boolean = true
    }
}