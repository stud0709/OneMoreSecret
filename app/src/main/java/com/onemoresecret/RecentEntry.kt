package com.onemoresecret

import kotlinx.serialization.Serializable

@Serializable
data class RecentEntry(
    val message: String,
    val applicationId: Int = com.onemoresecret.crypto.MessageComposer.APPLICATION_RSA_AES_GENERIC,
    val ttl: Long
)
