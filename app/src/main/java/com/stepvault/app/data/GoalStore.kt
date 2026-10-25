package com.stepvault.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stepvault.app.domain.StepAggregator
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "stepvault_settings",
)

class GoalStore(private val context: Context) {
    private val goalKey = intPreferencesKey("goal_steps")

    val goal: Flow<Int> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            (preferences[goalKey] ?: StepAggregator.DEFAULT_GOAL).coerceIn(MIN_GOAL, MAX_GOAL)
        }

    suspend fun setGoal(value: Int) {
        val safeValue = value.coerceIn(MIN_GOAL, MAX_GOAL)
        context.settingsDataStore.edit { preferences ->
            preferences[goalKey] = safeValue
        }
    }

    companion object {
        const val MIN_GOAL = 1_000
        const val MAX_GOAL = 100_000
    }
}
