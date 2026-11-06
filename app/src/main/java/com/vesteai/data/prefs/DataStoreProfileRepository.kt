package com.vesteai.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.vesteai.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {

    override fun observeOnboardingComplete(): Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    override suspend fun completeOnboarding() {
        dataStore.edit { it[ONBOARDING_COMPLETE] = true }
    }

    override fun observeBaselinePresent(): Flow<Boolean> =
        dataStore.data.map { it[BASELINE_PRESENT] ?: false }

    override fun observeBaselineSavedAtMillis(): Flow<Long?> =
        dataStore.data.map { it[BASELINE_SAVED_AT_MILLIS] }

    override suspend fun markBaselineSaved(savedAtMillis: Long) {
        dataStore.edit {
            it[BASELINE_PRESENT] = true
            it[BASELINE_SAVED_AT_MILLIS] = savedAtMillis
        }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val BASELINE_PRESENT = booleanPreferencesKey("baseline_present")
        val BASELINE_SAVED_AT_MILLIS = longPreferencesKey("baseline_saved_at_millis")
    }
}
