package com.onemoresecret

import kotlinx.serialization.Serializable

@Serializable
data class RecentEntry(
    val message: String,
    val drawableId: Int,
    val ttl: Long
)
