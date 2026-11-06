package com.vesteai.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.profileDataStore by preferencesDataStore(name = "profile")
