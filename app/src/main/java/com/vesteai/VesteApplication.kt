package com.vesteai

import android.app.Application
import com.vesteai.core.di.IoDispatcher
import com.vesteai.core.poller.BackgroundJobPoller
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

    @Inject
    lateinit var backgroundJobPoller: BackgroundJobPoller

    override fun onCreate() {
        super.onCreate()
        // Startup hygiene: clean orphaned files, start watching for async try-on jobs.
        applicationScope.launch { orphanFileSweep.sweep() }
        backgroundJobPoller.start(applicationScope)
    }
}