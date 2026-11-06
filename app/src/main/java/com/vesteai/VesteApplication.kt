package com.vesteai

import android.app.Application
import com.vesteai.core.di.IoDispatcher
import com.vesteai.domain.usecase.OrphanFileSweep
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VesteApplication : Application() {

    @Inject
    @IoDispatcher
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var orphanFileSweep: OrphanFileSweep

    override fun onCreate() {
        super.onCreate()
        // Startup hygiene, off the main thread. The sweep reports its own failures, so this job
        // only has to not outlive the process.
        applicationScope.launch { orphanFileSweep.sweep() }
    }
}
