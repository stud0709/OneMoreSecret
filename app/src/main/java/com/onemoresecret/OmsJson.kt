package com.onemoresecret

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object OmsJson {
    @JvmField
    val JSON = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    inline fun <reified T> decode(value: String): T {
        return JSON.decodeFromString(value)
    }

    inline fun <reified T> encode(value: T): String {
        return JSON.encodeToString(value)
    }

    @JvmStatic
    fun decodeRecentEntries(value: String?): MutableList<RecentEntry> {
        return JSON.decodeFromString<List<RecentEntry>>(value ?: "[]").toMutableList()
    }

    @JvmStatic
    fun encodeRecentEntries(entries: List<RecentEntry>): String {
        return JSON.encodeToString(entries)
    }
}
