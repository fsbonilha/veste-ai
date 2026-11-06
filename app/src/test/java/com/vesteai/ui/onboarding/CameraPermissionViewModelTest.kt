package com.vesteai.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionViewModelTest {
    private val viewModel = CameraPermissionViewModel()

    @Test
    fun startsCheckingBeforeTheGrantIsRead() {
        assertEquals(CameraPermissionState.Checking, viewModel.state.value)
    }

    @Test
    fun uncheckedPermissionMovesToNeeded() {
        viewModel.onStatusChecked(granted = false)

        assertEquals(CameraPermissionState.Needed, viewModel.state.value)
    }

    @Test
    fun grantedOnEntryMovesToGranted() {
        viewModel.onStatusChecked(granted = true)

        assertEquals(CameraPermissionState.Granted, viewModel.state.value)
    }

    @Test
    fun denialWithRationaleMovesToDenied() {
        viewModel.onStatusChecked(granted = false)

        viewModel.onRequestResult(granted = false, showRationale = true)

        assertEquals(CameraPermissionState.Denied, viewModel.state.value)
    }

    @Test
    fun denialWithoutRationaleMovesToPermanentlyDenied() {
        viewModel.onStatusChecked(granted = false)

        viewModel.onRequestResult(granted = false, showRationale = false)

        assertEquals(CameraPermissionState.PermanentlyDenied, viewModel.state.value)
    }

    @Test
    fun grantAfterDenialMovesToGranted() {
        viewModel.onRequestResult(granted = false, showRationale = true)

        viewModel.onRequestResult(granted = true, showRationale = false)

        assertEquals(CameraPermissionState.Granted, viewModel.state.value)
    }

    @Test
    fun resumeWithoutGrantKeepsPermanentlyDeniedState() {
        viewModel.onRequestResult(granted = false, showRationale = false)

        viewModel.onStatusChecked(granted = false)

        assertEquals(CameraPermissionState.PermanentlyDenied, viewModel.state.value)
    }

    @Test
    fun resumeAfterSettingsGrantMovesToGranted() {
        viewModel.onRequestResult(granted = false, showRationale = false)

        viewModel.onStatusChecked(granted = true)

        assertEquals(CameraPermissionState.Granted, viewModel.state.value)
    }
}
