package com.vesteai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.core.credentials.API_KEY_PREFIX
import com.vesteai.core.credentials.CredentialFormatError
import com.vesteai.core.credentials.CredentialSettings
import com.vesteai.core.credentials.CredentialState
import com.vesteai.core.credentials.SaveOutcome
import com.vesteai.core.credentials.validateApiKey
import com.vesteai.core.reset.AppDataReset
import com.vesteai.core.result.DataResult
import com.vesteai.domain.vton.VtonEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** What the key section says right now. Exactly one value, so two banners can never stack. */
sealed interface KeyMessage {
    data class RejectedFormat(val error: CredentialFormatError) : KeyMessage

    data object SaveFailed : KeyMessage

    /** The encrypted store could not be opened, so nothing was written. */
    data object StorageUnavailable : KeyMessage
}

sealed interface ClearDataState {
    data object Idle : ClearDataState
    data object Confirming : ClearDataState
    data object Clearing : ClearDataState
    data object Cleared : ClearDataState
    data object Failed : ClearDataState
}

data class SettingsUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val credential: CredentialState = CredentialState.Absent,
    val apiKeyInput: String = "",
    val baseUrlInput: String = DEFAULT_BASE_URL,
    val saving: Boolean = false,
    val keyMessage: KeyMessage? = null,
    val engineName: String = "",
    val clearData: ClearDataState = ClearDataState.Idle,
) {
    val maskedKey: String? get() = (credential as? CredentialState.Present)?.maskedKey

    /** A stored key became unreadable, so the only way forward is entering it again. */
    val needsReentry: Boolean get() = credential == CredentialState.Unreadable

    /** The field holds something that would pass the local format check. */
    val canSave: Boolean get() = !saving && validateApiKey(apiKeyInput) == null

    companion object {
        /** PixelAPI's documented base URL (plan §6.5). A default value, never a provider name. */
        const val DEFAULT_BASE_URL = "https://api.pixelapi.dev"
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentials: CredentialSettings,
    engine: VtonEngine,
    private val appDataReset: AppDataReset,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(engineName = engine.displayName))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (!loadCredential()) return@launch
            credentials.observe()
                .catch { _uiState.value = _uiState.value.copy(loadFailed = true) }
                .collect { state ->
                    _uiState.value = _uiState.value.copy(
                        credential = state,
                        baseUrlInput = state.baseUrlOrDefault(),
                    )
                }
        }
    }

    /** Re-reads the stored entry after a load failure; the form is only shown once it succeeds. */
    fun retry() {
        _uiState.value = _uiState.value.copy(loading = true, loadFailed = false)
        viewModelScope.launch { loadCredential() }
    }

    /** False when the stored entry could not be read at all, so the screen has nothing to show. */
    private suspend fun loadCredential(): Boolean {
        val stored = try {
            credentials.state()
        } catch (e: IOException) {
            _uiState.value = _uiState.value.copy(loading = false, loadFailed = true)
            return false
        }
        _uiState.value = _uiState.value.copy(
            loading = false,
            loadFailed = false,
            credential = stored,
            baseUrlInput = stored.baseUrlOrDefault(),
        )
        return true
    }

    fun onApiKeyChanged(value: String) {
        _uiState.value = _uiState.value.copy(apiKeyInput = value, keyMessage = null)
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.value = _uiState.value.copy(baseUrlInput = value)
    }

    /** Saves the key and its base URL together, after a local format check. Never touches the network. */
    fun save() {
        val state = _uiState.value
        if (state.saving) return
        validateApiKey(state.apiKeyInput)?.let { error ->
            _uiState.value = state.copy(keyMessage = KeyMessage.RejectedFormat(error))
            return
        }
        _uiState.value = state.copy(saving = true, keyMessage = null)
        viewModelScope.launch {
            val outcome = credentials.save(state.apiKeyInput, state.baseUrlInput)
            _uiState.value = _uiState.value.copy(
                saving = false,
                // Keep a rejected key in the field so the user can correct it rather than retype it.
                apiKeyInput = if (outcome == SaveOutcome.Saved) "" else _uiState.value.apiKeyInput,
                keyMessage = outcome.toMessage(),
            )
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            credentials.clear()
            _uiState.value = _uiState.value.copy(apiKeyInput = "", keyMessage = null)
        }
    }

    fun requestClearData() {
        _uiState.value = _uiState.value.copy(clearData = ClearDataState.Confirming)
    }

    fun cancelClearData() {
        _uiState.value = _uiState.value.copy(clearData = ClearDataState.Idle)
    }

    fun confirmClearData() {
        if (_uiState.value.clearData == ClearDataState.Clearing) return
        _uiState.value = _uiState.value.copy(clearData = ClearDataState.Clearing)
        viewModelScope.launch {
            val result = appDataReset.clearAll()
            _uiState.value = _uiState.value.copy(
                clearData = if (result is DataResult.Success) {
                    ClearDataState.Cleared
                } else {
                    ClearDataState.Failed
                },
                apiKeyInput = "",
                baseUrlInput = SettingsUiState.DEFAULT_BASE_URL,
                credential = CredentialState.Absent,
            )
        }
    }

    fun dismissClearDataResult() {
        _uiState.value = _uiState.value.copy(clearData = ClearDataState.Idle)
    }

    fun dismissKeyMessage() {
        _uiState.value = _uiState.value.copy(keyMessage = null)
    }

    private fun CredentialState.baseUrlOrDefault(): String =
        (this as? CredentialState.Present)?.baseUrl?.takeIf { it.isNotBlank() }
            ?: SettingsUiState.DEFAULT_BASE_URL

    private fun SaveOutcome.toMessage(): KeyMessage? = when (this) {
        SaveOutcome.Saved -> null
        is SaveOutcome.InvalidFormat -> KeyMessage.RejectedFormat(error)
        SaveOutcome.StorageUnavailable -> KeyMessage.StorageUnavailable
        SaveOutcome.Failed -> KeyMessage.SaveFailed
    }
}