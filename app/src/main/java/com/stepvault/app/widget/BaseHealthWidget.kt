package com.stepvault.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.stepvault.app.StepVaultApp
import com.stepvault.app.work.StepSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseHealthWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        notifyWidgetsChanged(context)
        refresh(context, force = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_MIUI_UPDATE) return

        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
        if (ids.isNotEmpty()) refresh(context, force = false)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context, force = true)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        notifyWidgetsChanged(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        notifyWidgetsChanged(context)
    }

    private fun refresh(context: Context, force: Boolean) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateAll(context, force = force)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notifyWidgetsChanged(context: Context) {
        val app = context.applicationContext as? StepVaultApp
        if (app != null) {
            app.onWidgetsChanged()
        } else {
            StepSyncWorker.syncIfNeeded(context)
        }
    }

    companion object {
        const val ACTION_MIUI_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"
    }
}
