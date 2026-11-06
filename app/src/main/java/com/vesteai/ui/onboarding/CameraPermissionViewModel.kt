package com.vesteai.ui.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

sealed interface CameraPermissionState {
    data object Checking : CameraPermissionState
    data object Needed : CameraPermissionState
    data object Denied : CameraPermissionState
    data object PermanentlyDenied : CameraPermissionState
    data object Granted : CameraPermissionState
}

@HiltViewModel
class CameraPermissionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow<CameraPermissionState>(CameraPermissionState.Checking)
    val state: StateFlow<CameraPermissionState> = _state.asStateFlow()

    /** Called on entry and on every resume, so a grant made in Settings is picked up. */
    fun onStatusChecked(granted: Boolean) {
        _state.update { current ->
            when {
                granted -> CameraPermissionState.Granted
                current == CameraPermissionState.Checking || current == CameraPermissionState.Granted ->
                    CameraPermissionState.Needed
                else -> current
            }
        }
    }

    /**
     * After a denial, the system reports rationale only while another prompt is still
     * allowed. No rationale on a denial therefore means the user chose "don't ask again".
     */
    fun onRequestResult(granted: Boolean, showRationale: Boolean) {
        _state.value = when {
            granted -> CameraPermissionState.Granted
            showRationale -> CameraPermissionState.Denied
            else -> CameraPermissionState.PermanentlyDenied
        }
    }
}
