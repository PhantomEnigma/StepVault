package com.stepvault.app

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
    private var widgetSyncJob: Job? = null
    private var observerRegistered = false

    @Volatile
    private var lastObservedRefreshAt = 0L

    @Volatile
    private var foreground = false

    private val providerObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (foreground) scheduleWidgetRefresh()
        }
    }

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            foreground = true
            updateObserverRegistration()
        }

        override fun onStop(owner: LifecycleOwner) {
            foreground = false
            updateObserverRegistration()
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        StepSyncWorker.syncIfNeeded(this)
    }

    fun onWidgetsChanged() {
        updateObserverRegistration()
        StepSyncWorker.syncIfNeeded(this)
    }

    private fun updateObserverRegistration() {
        val shouldRegister = foreground && WidgetUpdater.hasWidgets(this)
        when {
            shouldRegister && !observerRegistered -> {
                StepProviderClient.CANDIDATE_URIS.forEach { uri ->
                    runCatching {
                        contentResolver.registerContentObserver(uri, true, providerObserver)
                    }
                }
                observerRegistered = true
            }

            !shouldRegister && observerRegistered -> {
                runCatching {
                    contentResolver.unregisterContentObserver(providerObserver)
                }
                observerRegistered = false
                widgetSyncJob?.cancel()
                widgetSyncJob = null
            }
        }
    }

    private fun scheduleWidgetRefresh() {
        if (!foreground) return
        widgetSyncJob?.cancel()
        widgetSyncJob = appScope.launch {
            delay(DEBOUNCE_MS)
            val elapsed = SystemClock.elapsedRealtime() - lastObservedRefreshAt
            val remaining = MIN_REFRESH_INTERVAL_MS - elapsed
            if (remaining > 0L) delay(remaining)
            lastObservedRefreshAt = SystemClock.elapsedRealtime()
            try {
                WidgetUpdater.updateAll(this@StepVaultApp)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 1_500L
        private const val MIN_REFRESH_INTERVAL_MS = 15_000L
    }
}

class AppContainer(context: Context) {
    private val database = StepDatabase.get(context)
    val goalStore = GoalStore(context)
    private val providerClient = StepProviderClient(context)
    val repository = StepRepository(database.stepDao(), goalStore, providerClient)
}
