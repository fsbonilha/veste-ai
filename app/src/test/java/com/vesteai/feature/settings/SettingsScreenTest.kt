package com.vesteai.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import com.vesteai.R
import com.vesteai.core.credentials.CredentialFormatError
import com.vesteai.core.credentials.CredentialState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private fun string(id: Int, vararg args: Any): String = resources.getString(id, *args)

    private fun baseState(
        credential: CredentialState = CredentialState.Absent,
        apiKeyInput: String = "pk_live_abcdef3456",
        engineName: String = "Mock (offline)",
    ) = SettingsUiState(
        loading = false,
        credential = credential,
        apiKeyInput = apiKeyInput,
        engineName = engineName,
    )

    private fun showScreen(
        state: SettingsUiState = baseState(),
        onSaveKey: () -> Unit = {},
        onRemoveKey: () -> Unit = {},
        onRetakeBaseline: () -> Unit = {},
        onRequestClearData: () -> Unit = {},
    ) {
        compose.setContent {
            SettingsScreen(
                state = state,
                onApiKeyChanged = {},
                onSaveKey = onSaveKey,
                onRemoveKey = onRemoveKey,
                onBaseUrlChanged = {},
                onRetakeBaseline = onRetakeBaseline,
                onRequestClearData = onRequestClearData,
                onConfirmClearData = {},
                onCancelClearData = {},
                onDismissClearDataResult = {},
                onDismissKeyMessage = {},
            )
        }
    }

    /** The settings list scrolls, so a node is only "displayed" once it has been scrolled into view. */
    private fun assertReachable(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saveKeyIsDisplayedAndReachable() {
        var saves = 0
        showScreen(onSaveKey = { saves++ })

        assertReachable(string(R.string.settings_key_save))
        compose.onNodeWithText(string(R.string.settings_key_save)).performClick()

        assertEquals(1, saves)
    }

    @Test
    fun removeKeyIsDisplayedAndReachableWhenAKeyIsStored() {
        var removes = 0
        showScreen(
            state = baseState(
                credential = CredentialState.Present("****3456", "https://api.pixelapi.dev"),
            ),
            onRemoveKey = { removes++ },
        )

        assertReachable(string(R.string.settings_key_remove))
        compose.onNodeWithText(string(R.string.settings_key_remove)).performClick()

        assertEquals(1, removes)
    }

    @Test
    fun removeKeyIsHiddenWhenNothingIsStored() {
        showScreen()

        compose.onNodeWithText(string(R.string.settings_key_remove)).assertDoesNotExist()
    }

    @Test
    fun theStoredKeyIsShownMasked() {
        showScreen(
            state = baseState(
                credential = CredentialState.Present("****3456", "https://api.pixelapi.dev"),
            ),
        )

        assertReachable(string(R.string.settings_key_stored, "****3456"))
    }

    @Test
    fun theKeyFieldRendersDotsRatherThanTheTypedKey() {
        showScreen()

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, maskedText()))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("pk_live_abcdef3456"),
            ),
        ).assertDoesNotExist()
    }

    @Test
    fun retakeBaselineIsDisplayedAndReachable() {
        var taps = 0
        showScreen(onRetakeBaseline = { taps++ })

        assertReachable(string(R.string.settings_baseline_retake))
        compose.onNodeWithText(string(R.string.settings_baseline_retake)).performClick()

        assertEquals(1, taps)
    }

    @Test
    fun clearDataIsDisplayedAndReachable() {
        var taps = 0
        showScreen(onRequestClearData = { taps++ })

        assertReachable(string(R.string.settings_clear_data_action))
        compose.onNodeWithText(string(R.string.settings_clear_data_action)).performClick()

        assertEquals(1, taps)
    }

    @Test
    fun theEngineNameIsShownFromStateNotHardcoded() {
        showScreen(state = baseState(engineName = "Engine Under Test"))

        assertReachable("Engine Under Test")
    }

    @Test
    fun thePrivacyCopyNamesTheUsersOwnKeyAndSaysPhotosLeaveTheDevice() {
        showScreen(
            state = baseState(
                credential = CredentialState.Present("****3456", "https://api.pixelapi.dev"),
            ),
        )

        assertReachable(string(R.string.settings_privacy_own_key))
        assertReachable(string(R.string.settings_privacy_photos_leave))
    }

    @Test
    fun theBaseUrlFieldIsDisplayedNextToTheKey() {
        showScreen()

        assertReachable(string(R.string.settings_base_url_title))
        assertReachable(string(R.string.settings_base_url_field_label))
    }

    @Test
    fun theMockEngineHintPromisesNothingLeavesTheDevice() {
        showScreen(state = baseState(credential = CredentialState.Absent))

        assertReachable(string(R.string.settings_engine_mock_hint))
    }

    @Test
    fun theRemoteEngineHintSaysPhotosLeaveTheDevice() {
        showScreen(
            state = baseState(
                credential = CredentialState.Present("****3456", "https://api.pixelapi.dev"),
            ),
        )

        assertReachable(string(R.string.settings_engine_remote_hint))
    }

    @Test
    fun saveIsDisabledUntilTheKeyLooksValid() {
        showScreen(state = baseState(apiKeyInput = "nope"))

        compose.onNodeWithText(string(R.string.settings_key_save))
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun saveIsEnabledForAPrefixedKey() {
        showScreen()

        compose.onNodeWithText(string(R.string.settings_key_save)).performScrollTo().assertIsEnabled()
    }

    @Test
    fun aRejectedFormatIsExplainedInline() {
        showScreen(
            state = baseState(apiKeyInput = "nope").copy(
                keyMessage = KeyMessage.RejectedFormat(CredentialFormatError.MISSING_PREFIX),
            ),
        )

        assertReachable(string(R.string.settings_key_error_prefix))
    }

    @Test
    fun anUnreadableStoreAsksForTheKeyAgain() {
        showScreen(state = baseState(credential = CredentialState.Unreadable))

        assertReachable(string(R.string.settings_key_error_reentry))
        assertReachable(string(R.string.settings_key_remove))
    }

    @Test
    fun aClearedResultIsAcknowledged() {
        showScreen(state = baseState().copy(clearData = ClearDataState.Cleared))

        assertReachable(string(R.string.settings_clear_data_done))
    }

    @Test
    fun loadingShowsProgressInsteadOfTheForm() {
        showScreen(state = SettingsUiState(loading = true))

        compose.onNodeWithText(string(R.string.settings_key_title)).assertDoesNotExist()
    }

    private fun maskedText(): AnnotatedString {
        val typed = "pk_live_abcdef3456"
        return AnnotatedString("•".repeat(typed.length))
    }
}