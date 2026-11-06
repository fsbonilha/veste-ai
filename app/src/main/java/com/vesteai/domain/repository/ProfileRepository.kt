package com.vesteai.domain.repository

import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeOnboardingComplete(): Flow<Boolean>

    suspend fun completeOnboarding()

    fun observeBaselinePresent(): Flow<Boolean>

    fun observeBaselineSavedAtMillis(): Flow<Long?>

    suspend fun markBaselineSaved(savedAtMillis: Long)

    /** Forgets onboarding state and the baseline marker, as part of "Clear data" in Settings. */
    suspend fun reset()
}
