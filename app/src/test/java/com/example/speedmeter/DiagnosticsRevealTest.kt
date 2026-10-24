package com.example.speedmeter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.speedmeter.ui.ShadTheme
import com.example.speedmeter.update.UpdateState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Diagnostics card stays hidden until Version is tapped 7 times, and Hide puts it away. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DiagnosticsRevealTest {

    @get:Rule val compose = createComposeRule()

    private var settings by mutableStateOf(UiSettings())

    private val actions = object : MainActions by NoopActions {
        override fun setShowDiagnostics(show: Boolean) {
            settings = settings.copy(showDiagnostics = show)
        }
    }

    private fun setUp() = compose.setContent {
        ShadTheme {
            MainScreen(
                meter = null,
                update = UpdateState.UpToDate,
                settings = settings,
                notificationsDenied = false,
                canInstallUpdates = true,
                tileOffer = TileOffer.CanAdd,
                actions = actions,
                currentVersion = "0.1.10",
            )
        }
    }

    private fun tapVersion(times: Int) {
        val version = compose.onNodeWithText("Version 0.1.10", substring = true).performScrollTo()
        repeat(times) { version.performClick() }
    }

    @Test
    fun `six taps are not enough`() {
        setUp()
        tapVersion(6)
        compose.onNodeWithText("Diagnostics").assertDoesNotExist()
    }

    @Test
    fun `seven taps reveal the card and Hide removes it`() {
        setUp()
        tapVersion(7)
        compose.onNodeWithText("Diagnostics").performScrollTo().assertExists()
        compose.onNodeWithText("Hide").performClick()
        compose.onNodeWithText("Diagnostics").assertDoesNotExist()
    }
}
