package com.Mareg74.bpmdetector.ui.measure

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Mareg74.bpmdetector.domain.MeasureState
import com.Mareg74.bpmdetector.ui.theme.BpmDetectorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation smoke test for START → Starting… status.
 *
 * Requires an Android device or emulator (ANDROID_HOME / SDK). This cloud VM has no SDK;
 * run locally: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MeasureScreenSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startButtonShowsStartingStatus() {
        val uiState = mutableStateOf(MeasureUiState.idle())
        composeRule.setContent {
            BpmDetectorTheme(dynamicColor = false) {
                MeasureScreenContent(
                    uiState = uiState.value,
                    onStart = {
                        uiState.value =
                            uiState.value.copy(
                                state = MeasureState.Starting,
                                statusLabel = MeasureState.Starting.uiLabel(),
                                bpm = null,
                                bpmDimmed = false,
                            )
                    },
                    onStop = {
                        uiState.value = MeasureUiState.idle(settings = uiState.value.settings)
                    },
                )
            }
        }
        composeRule.onNodeWithText("START").performClick()
        composeRule.onNodeWithTag("status").assertIsDisplayed()
        composeRule.onNodeWithText("Starting…").assertIsDisplayed()
    }
}