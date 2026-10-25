package com.stepvault.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.stepvault.app.MainActivity
import com.stepvault.app.R
import com.stepvault.app.domain.StepSnapshot
import com.stepvault.app.domain.StepStatus
import com.stepvault.app.util.Formatters

object WidgetRenderer {
    const val EXTRA_OPEN_GOAL = "com.stepvault.app.extra.OPEN_GOAL"

    fun render(context: Context, layoutId: Int, snapshot: StepSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        if (layoutId == R.layout.widget_health_4x2) {
            bindWide(context, views, snapshot)
        } else {
            bindCompact(context, views, snapshot)
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            openApp(context, requestCode = 100, openGoal = false),
        )
        return views
    }

    private fun bindWide(context: Context, views: RemoteViews, snapshot: StepSnapshot) {
        val ready = snapshot.status == StepStatus.READY
        val showData = ready || snapshot.lastSync != null
        views.setViewVisibility(R.id.widget_setup, if (showData) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_steps, if (showData) View.VISIBLE else View.GONE)

        if (showData) {
            views.setTextViewText(R.id.widget_steps_count, Formatters.steps(snapshot.today.steps))
            views.setTextViewText(
                R.id.widget_goal,
                context.getString(
                    R.string.widget_goal_format,
                    Formatters.steps(snapshot.goal),
                ),
            )
            views.setTextViewText(
                R.id.widget_percent,
                if (ready) {
                    context.getString(R.string.widget_percent_format, snapshot.progressPercent)
                } else {
                    context.getString(R.string.widget_cached)
                },
            )
            views.setProgressBar(R.id.widget_progress, 100, snapshot.progressPercent, false)
            views.setTextViewText(
                R.id.widget_distance,
                context.getString(
                    R.string.widget_distance_format,
                    Formatters.distanceKm(snapshot.today.distanceKm),
                ),
            )
            views.setTextViewText(
                R.id.widget_calories,
                context.getString(
                    R.string.widget_calories_format,
                    Formatters.calories(snapshot.today.calories),
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_set_goal,
                openApp(context, requestCode = 102, openGoal = true),
            )
            views.setOnClickPendingIntent(
                R.id.widget_details,
                openApp(context, requestCode = 101, openGoal = false),
            )
        } else {
            views.setTextViewText(
                R.id.widget_setup_subtitle,
                context.getString(setupMessage(snapshot.status)),
            )
            views.setOnClickPendingIntent(
                R.id.widget_setup_button,
                openApp(context, requestCode = 103, openGoal = false),
            )
        }
    }

    private fun bindCompact(context: Context, views: RemoteViews, snapshot: StepSnapshot) {
        val ready = snapshot.status == StepStatus.READY
        val showData = ready || snapshot.lastSync != null
        views.setViewVisibility(
            R.id.widget_2x1_progress,
            if (showData) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.widget_2x1_metrics,
            if (showData) View.VISIBLE else View.GONE,
        )
        if (showData) {
            views.setTextViewText(
                R.id.widget_2x1_title,
                context.getString(R.string.widget_steps_title),
            )
            views.setTextViewText(R.id.widget_2x1_value, Formatters.steps(snapshot.today.steps))
            views.setTextViewText(
                R.id.widget_2x1_subtitle,
                if (ready) {
                    context.getString(
                        R.string.widget_2x1_of_format,
                        Formatters.steps(snapshot.goal),
                    )
                } else {
                    context.getString(R.string.widget_cached)
                },
            )
            views.setProgressBar(R.id.widget_2x1_progress, 100, snapshot.progressPercent, false)
            views.setTextViewText(
                R.id.widget_2x1_distance,
                context.getString(
                    R.string.widget_distance_format,
                    Formatters.distanceKm(snapshot.today.distanceKm),
                ),
            )
            views.setTextViewText(
                R.id.widget_2x1_calories,
                context.getString(
                    R.string.widget_calories_format,
                    Formatters.calories(snapshot.today.calories),
                ),
            )
        } else {
            views.setTextViewText(
                R.id.widget_2x1_title,
                context.getString(R.string.widget_2x1_setup),
            )
            views.setTextViewText(R.id.widget_2x1_value, "\u2014")
            views.setTextViewText(R.id.widget_2x1_subtitle, "")
            views.setProgressBar(R.id.widget_2x1_progress, 100, 0, false)
        }
    }

    private fun setupMessage(status: StepStatus): Int = when (status) {
        StepStatus.PERMISSION_REQUIRED,
        StepStatus.PERMISSION_DENIED -> R.string.widget_setup_permission

        StepStatus.PROVIDER_UNAVAILABLE -> R.string.widget_setup_provider_missing

        else -> R.string.widget_setup_retry
    }

    private fun openApp(context: Context, requestCode: Int, openGoal: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_GOAL, openGoal)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
