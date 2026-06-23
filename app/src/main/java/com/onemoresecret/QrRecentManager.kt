package com.onemoresecret

import android.content.SharedPreferences
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

object QrRecentManager {
    const val PROP_RECENT_ENTRIES = "recent_entries"
    const val PROP_RECENT_SIZE = "recent_size"
    private const val DEF_RECENT_SIZE = 3
    private val RECENT_TTL = TimeUnit.HOURS.toMillis(12)

    fun setRecent(preferences: SharedPreferences, message: String, applicationId: Int) {
        try {
            val recentEntries = OmsJson.decodeRecentEntries(
                preferences.getString(PROP_RECENT_ENTRIES, "[]")
            ).toMutableList()

            val currentTime = System.currentTimeMillis()
            recentEntries.removeIf { it.ttl < currentTime }

            if (recentEntries.isNotEmpty() && recentEntries.first().message == message) {
                return // do not store duplicates in history
            }

            val recentSize = preferences.getInt(PROP_RECENT_SIZE, DEF_RECENT_SIZE)

            // add latest recent values
            val newEntry = RecentEntry(
                message,
                applicationId,
                currentTime + RECENT_TTL
            )

            recentEntries.add(0, newEntry)

            // crop to maximal size
            while (recentEntries.size > recentSize) {
                recentEntries.removeAt(recentEntries.size - 1)
            }

            preferences.edit {
                putString(PROP_RECENT_ENTRIES, OmsJson.encodeRecentEntries(recentEntries))
            }
        } catch (ex: Exception) {
            Util.printStackTrace(ex)
        }
    }

    fun getRecentEntries(preferences: SharedPreferences): List<RecentEntry> {
        return try {
            val recentEntries = OmsJson.decodeRecentEntries(
                preferences.getString(PROP_RECENT_ENTRIES, "[]")
            ).toMutableList()

            val currentTime = System.currentTimeMillis()
            recentEntries.removeIf { it.ttl < currentTime }

            val recentSize = preferences.getInt(PROP_RECENT_SIZE, DEF_RECENT_SIZE)
            recentEntries.take(recentSize)
        } catch (ex: Exception) {
            Util.printStackTrace(ex)
            emptyList()
        }
    }
}
