package com.stepvault.app

import android.app.Application
import android.content.Context
import com.stepvault.app.data.GoalStore
import com.stepvault.app.data.StepProviderClient
import com.stepvault.app.data.StepRepository
import com.stepvault.app.data.local.StepDatabase
import com.stepvault.app.work.StepSyncWorker

class StepVaultApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        StepSyncWorker.schedule(this)
    }
}

class AppContainer(context: Context) {
    private val database = StepDatabase.get(context)
    val goalStore = GoalStore(context)
    private val providerClient = StepProviderClient(context)
    val repository = StepRepository(database.stepDao(), goalStore, providerClient)
}
