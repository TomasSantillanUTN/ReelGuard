package com.example.pruebareel.data.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val Context.statsStore: DataStore<Preferences> by preferencesDataStore(name = "stats")

/** Qué se mide. Todo se guarda por día. */
enum class StatMetric(val key: String) {
    IG_REELS("ig_reels"),
    TT_VIDEOS("tt_videos"),
    YT_SHORTS_SECONDS("yt_seconds"),
    LOCKS("locks")
}

/** Valores de un día. */
data class DayStats(
    val dayKey: String,
    val values: Map<StatMetric, Int>
) {
    operator fun get(metric: StatMetric): Int = values[metric] ?: 0
}

/**
 * Guarda estadísticas de uso por día (todo local, en el celular).
 *
 * Cada valor se guarda con la clave `yyyy-MM-dd|métrica`. Se conservan los últimos
 * [KEEP_DAYS] días; lo más viejo se borra solo.
 */
class StatsRepository(private val context: Context) {

    companion object {
        const val KEEP_DAYS = 14

        /** Clave del día (fecha local del celular), con [offsetDays] días hacia atrás. */
        fun dayKey(offsetDays: Int = 0, now: Long = System.currentTimeMillis()): String {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -offsetDays)
            }
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }

        private fun key(day: String, metric: StatMetric) = intPreferencesKey("$day|${metric.key}")
    }

    /** Emite los últimos 7 días, del más reciente (hoy) al más viejo. */
    val lastWeekFlow: Flow<List<DayStats>> = context.statsStore.data.map { p ->
        (0 until 7).map { offset ->
            val day = dayKey(offset)
            DayStats(day, StatMetric.entries.associateWith { m -> p[key(day, m)] ?: 0 })
        }
    }

    /** Suma [amount] a la métrica del día de hoy. */
    suspend fun add(metric: StatMetric, amount: Int = 1) {
        if (amount <= 0) return
        val today = dayKey()
        val oldestKept = dayKey(KEEP_DAYS)
        context.statsStore.edit { p ->
            val k = key(today, metric)
            p[k] = (p[k] ?: 0) + amount

            // Limpieza: borrar días más viejos que KEEP_DAYS (las claves empiezan con la fecha).
            p.asMap().keys
                .filter { it.name.substringBefore('|') < oldestKept }
                .forEach {
                    @Suppress("UNCHECKED_CAST")
                    p.remove(it as Preferences.Key<Any>)
                }
        }
    }
}
