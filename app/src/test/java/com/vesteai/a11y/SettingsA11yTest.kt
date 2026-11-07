package com.vesteai.a11y

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.vesteai.R
import com.vesteai.core.credentials.CredentialFormatError
import com.vesteai.core.credentials.CredentialState
import com.vesteai.feature.settings.ClearDataState
import com.vesteai.feature.settings.KeyMessage
import com.vesteai.feature.settings.SettingsScreen
import com.vesteai.feature.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** TalkBack and touch-target audit for Settings (plan §7, §8 M8). */
@RunWith(RobolectricTestRunner::class)
class SettingsA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private fun showScreen(state: SettingsUiState = baseState()) {
        compose.setContent {
            SettingsScreen(
                state = state,
                onApiKeyChanged = {},
                onSaveKey = {},
                onRemoveKey = {},
                onBaseUrlChanged = {},
                onRetakeBaseline = {},
                onRequestClearData = {},
                onConfirmClearData = {},
                onCancelClearData = {},
                onDismissClearDataResult = {},
                onDismissKeyMessage = {},
            )
        }
    }

    private fun baseState() = SettingsUiState(
        loading = false,
        credential = CredentialState.Absent,
        apiKeyInput = "pk_live_abcdef3456",
        engineName = "Mock (offline)",
    )

    private fun string(id: Int, vararg args: Any) = resources.getString(id, *args)

    @Test
    fun theLoadingStateIsAnnouncedRatherThanAnEmptyForm() {
        showScreen(SettingsUiState(loading = true))

        compose.assertImageHasDescription(string(R.string.state_loading))
        compose.onNodeWithText(string(R.string.settings_key_title)).assertDoesNotExist()
    }

    @Test
    fun aFailedLoadOffersCopyAndARetryInsteadOfABlankForm() {
        showScreen(SettingsUiState(loading = false, loadFailed = true))

        compose.assertTextDisplayed(string(R.string.settings_load_failed))
        compose.assertPrimaryActionReachable(string(R.string.action_retry))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun everyControlIsNamedAndTouchSized() {
        showScreen(
            baseState().copy(
                credential = CredentialState.Present("****3456", "https://api.pixelapi.dev"),
            ),
        )

        compose.assertControlsAreAccessible()
    }

    @Test
    fun savingTheKeyIsThePrimaryActionAndStaysReachable() {
        showScreen()

        compose.assertPrimaryActionReachable(string(R.string.settings_key_save))
    }

    @Test
    fun clearDataAndRetakeBaselineStayReachableAtTheirOwnHeight() {
        showScreen()

        compose.assertPrimaryActionReachable(string(R.string.settings_baseline_retake))
        compose.assertPrimaryActionReachable(string(R.string.settings_clear_data_action))
    }

    @Test
    fun aRejectedKeyIsAnnouncedWhenItAppears() {
        showScreen(
            baseState().copy(keyMessage = KeyMessage.RejectedFormat(CredentialFormatError.BLANK)),
        )

        compose.onNodeWithText(string(R.string.settings_key_error_blank))
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, liveRegion()))
    }

    @Test
    fun aFailedClearSaysSoAndItsDismissIsReachable() {
        showScreen(baseState().copy(clearData = ClearDataState.Failed))

        compose.assertTextReachable(string(R.string.settings_clear_data_failed))
        compose.assertControlsAreAccessible()
    }

    @Test
    fun aClearedResultIsAnnounced() {
        showScreen(baseState().copy(clearData = ClearDataState.Cleared))

        compose.assertTextReachable(string(R.string.settings_clear_data_done))
    }

    @Test
    fun theClearDataDialogNamesBothActionsAndStaysTouchSized() {
        showScreen(baseState().copy(clearData = ClearDataState.Confirming))

        compose.assertTextDisplayed(string(R.string.settings_clear_data_confirm_title))
        compose.assertControlsAreAccessible()
    }

    private fun liveRegion() = androidx.compose.ui.semantics.LiveRegionMode.Polite
}
