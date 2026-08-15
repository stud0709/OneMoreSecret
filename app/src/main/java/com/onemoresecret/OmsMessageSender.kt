package com.onemoresecret

interface OmsMessageSender {
    fun sendReply(data: ByteArray, shouldClose: Boolean)
}
