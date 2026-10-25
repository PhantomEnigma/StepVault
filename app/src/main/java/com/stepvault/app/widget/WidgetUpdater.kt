package com.stepvault.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.stepvault.app.R
import com.stepvault.app.StepVaultApp
import com.stepvault.app.domain.StepSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetUpdater {
    private val providers = listOf(
        HealthWidgetProvider::class.java to R.layout.widget_health_4x2,
        HealthWidget2x1Provider::class.java to R.layout.widget_health_2x1,
    )

    suspend fun updateAll(context: Context, snapshot: StepSnapshot? = null) =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext as? StepVaultApp ?: return@withContext
            val resolved = snapshot ?: app.container.repository.refresh()
            val manager = AppWidgetManager.getInstance(context)

            for ((provider, layoutId) in providers) {
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isEmpty()) continue
                val views = WidgetRenderer.render(context, layoutId, resolved)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }
}
