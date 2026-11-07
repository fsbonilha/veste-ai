package com.vesteai.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vesteai.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    profileRepository: ProfileRepository,
) : ViewModel() {

    /** Null until the stored flag is read. Read once so finishing onboarding never re-keys the NavHost. */
    private val _startsInOnboarding = MutableStateFlow<Boolean?>(null)
    val startsInOnboarding: StateFlow<Boolean?> = _startsInOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            _startsInOnboarding.value = !profileRepository.observeOnboardingComplete().first()
        }
    }

    /**
     * Onboarding finished: the host swaps the launch graph for the tab shell. The flag is already
     * persisted by the baseline save, so this only mirrors it into state for this session.
     */
    fun onOnboardingFinished() {
        _startsInOnboarding.value = false
    }
}
