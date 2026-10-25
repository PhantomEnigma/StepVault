package com.stepvault.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.core.content.ContextCompat
import com.stepvault.app.domain.StepSample

class StepProviderClient(private val context: Context) {

    sealed interface QueryResult {
        data class Success(val samples: List<StepSample>) : QueryResult
        data object PermissionRequired : QueryResult
        data object PermissionDenied : QueryResult
        data object ProviderUnavailable : QueryResult
        data class Error(val message: String) : QueryResult
    }

    fun providerAvailable(): Boolean = runCatching {
        context.packageManager.resolveContentProvider(AUTHORITY, 0) != null
    }.getOrDefault(false)

    fun activityPermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACTIVITY_RECOGNITION,
    ) == PackageManager.PERMISSION_GRANTED

    fun query(startMillis: Long, endMillis: Long): QueryResult {
        if (!providerAvailable()) return QueryResult.ProviderUnavailable
        if (!activityPermissionGranted()) return QueryResult.PermissionRequired

        var lastError: String? = null
        var permissionDenied = false
        for (uri in CANDIDATE_URIS) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    PROJECTION,
                    SELECTION,
                    arrayOf(startMillis.toString(), endMillis.toString()),
                    SORT_ORDER,
                ) ?: continue

                return cursor.use { QueryResult.Success(readSamples(it)) }
            } catch (error: SecurityException) {
                permissionDenied = true
                lastError = error.message ?: "SecurityException"
            } catch (error: Exception) {
                lastError = error.message ?: error.javaClass.simpleName
            }
        }
        return if (permissionDenied) {
            QueryResult.PermissionDenied
        } else {
            QueryResult.Error(lastError ?: "The step provider returned no cursor")
        }
    }

    private fun readSamples(cursor: Cursor): List<StepSample> {
        val beginIndex = cursor.getColumnIndex(COLUMN_BEGIN)
        val endIndex = cursor.getColumnIndex(COLUMN_END)
        val modeIndex = cursor.getColumnIndex(COLUMN_MODE)
        val stepsIndex = cursor.getColumnIndex(COLUMN_STEPS)

        require(beginIndex >= 0 && endIndex >= 0 && stepsIndex >= 0) {
            "The step provider is missing required columns"
        }

        val samples = ArrayList<StepSample>(cursor.count.coerceAtLeast(0))
        while (cursor.moveToNext()) {
            val begin = cursor.getLong(beginIndex)
            val end = cursor.getLong(endIndex)
            val steps = cursor.getInt(stepsIndex)
            if (begin <= 0L || steps < 0) continue
            samples += StepSample(
                beginTime = begin,
                endTime = maxOf(end, begin),
                mode = if (modeIndex >= 0) cursor.getInt(modeIndex) else -1,
                steps = steps,
            )
        }
        return samples
    }

    companion object {
        const val AUTHORITY = "com.miui.providers.steps"

        private const val COLUMN_BEGIN = "_begin_time"
        private const val COLUMN_END = "_end_time"
        private const val COLUMN_MODE = "_mode"
        private const val COLUMN_STEPS = "_steps"

        private val PROJECTION = arrayOf(COLUMN_BEGIN, COLUMN_END, COLUMN_MODE, COLUMN_STEPS)
        val CANDIDATE_URIS = listOf(
            Uri.parse("content://0@$AUTHORITY/item"),
            Uri.parse("content://$AUTHORITY/item"),
        )
        private val SELECTION = "$COLUMN_BEGIN >= ? AND $COLUMN_BEGIN < ?"
        private val SORT_ORDER = "_id ASC"
    }
}
