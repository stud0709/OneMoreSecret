package com.onemoresecret.compose

import android.content.Context

class ResourceProvider(private val context: Context) {
    fun getString(resId: Int): String {
        return context.getString(resId)
    }

    fun getSystemService(name: String): Any {
        return context.getSystemService(name)
    }
}