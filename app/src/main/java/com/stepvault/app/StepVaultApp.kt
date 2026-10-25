package com.stepvault.app

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.stepvault.app.data.GoalStore
import com.stepvault.app.data.StepProviderClient
import com.stepvault.app.data.StepRepository
import com.stepvault.app.data.local.StepDatabase
import com.stepvault.app.widget.WidgetUpdater
import com.stepvault.app.work.StepSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StepVaultApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var providerObserver: ContentObserver? = null
    private var widgetSyncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        StepSyncWorker.schedule(this)
        registerProviderObserver()
    }

    override fun onTerminate() {
        providerObserver?.let { contentResolver.unregisterContentObserver(it) }
        providerObserver = null
        widgetSyncJob?.cancel()
        super.onTerminate()
    }

    private fun registerProviderObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleWidgetRefresh()
            }
        }
        providerObserver = observer
        StepProviderClient.CANDIDATE_URIS.forEach { uri ->
            runCatching {
                contentResolver.registerContentObserver(uri, true, observer)
            }
        }
    }

    private fun scheduleWidgetRefresh() {
        widgetSyncJob?.cancel()
        widgetSyncJob = appScope.launch {
            delay(1_500)
            try {
                WidgetUpdater.updateAll(this@StepVaultApp)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        }
    }
}

class AppContainer(context: Context) {
    private val database = StepDatabase.get(context)
    val goalStore = GoalStore(context)
    private val providerClient = StepProviderClient(context)
    val repository = StepRepository(database.stepDao(), goalStore, providerClient)
}
