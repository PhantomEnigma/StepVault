package com.stepvault.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.stepvault.app.R
import com.stepvault.app.StepVaultApp
import com.stepvault.app.data.RefreshMode
import com.stepvault.app.domain.StepSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetUpdater {
    private val providers = listOf(
        HealthWidgetProvider::class.java to R.layout.widget_health_4x2,
        HealthWidget2x1Provider::class.java to R.layout.widget_health_2x1,
    )

    @Volatile
    private var lastSignature: String? = null

    fun hasWidgets(context: Context): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        providers.any { (provider, _) ->
            manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
        }
    }.getOrDefault(false)

    suspend fun updateAll(
        context: Context,
        snapshot: StepSnapshot? = null,
        force: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? StepVaultApp ?: return@withContext
        val manager = AppWidgetManager.getInstance(context)
        val idsByProvider = providers.map { (provider, layoutId) ->
            Triple(provider, layoutId, manager.getAppWidgetIds(ComponentName(context, provider)))
        }
        if (idsByProvider.all { it.third.isEmpty() }) return@withContext

        val resolved = snapshot ?: app.container.repository.refresh(RefreshMode.TODAY)
        val signature = signatureOf(resolved)
        if (!force && signature == lastSignature) return@withContext

        for ((_, layoutId, ids) in idsByProvider) {
            if (ids.isEmpty()) continue
            val views = WidgetRenderer.render(context, layoutId, resolved)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
        lastSignature = signature
    }

    private fun signatureOf(snapshot: StepSnapshot): String = buildString {
        append(snapshot.today.date.toEpochDay())
        append('|').append(snapshot.today.steps)
        append('|').append(snapshot.goal)
        append('|').append(snapshot.status)
        append('|').append(snapshot.lastSync != null)
        append('|').append(snapshot.today.distanceMeters)
        append('|').append(snapshot.today.calories)
        append('|').append(snapshot.progressPercent)
    }
}
