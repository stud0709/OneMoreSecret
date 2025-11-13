package com.onemoresecret.qr

import android.util.Log
import com.onemoresecret.crypto.OneTimePassword
import java.util.BitSet
import java.util.Objects
import kotlin.IllegalArgumentException
import kotlin.Int

abstract class MessageParser {
    protected val chunks: MutableList<String?> = ArrayList()
    protected var transactionId: String? = null

    fun consume(qrCode: String) {
        //check other supported formats
        if (OneTimePassword(qrCode).valid) {
            Log.d(TAG, "Looks like a valid TOTP")
            transactionId = null
            onMessage(qrCode)
            return
        }

        val sArr = qrCode.split("\\t".toRegex(), limit = 5).toTypedArray()
        val tId = sArr[0]
        val chunkNo = sArr[1].toInt()
        val totalChunks = sArr[2].toInt()
        val chunkSize = sArr[3].toInt()
        val text = sArr[4]

        if (chunkSize > 1024) {
            transactionId = null
            throw IllegalArgumentException("Too large chunks: $chunkSize")
        }

        if (totalChunks > 1024) {
            transactionId = null
            throw IllegalArgumentException("Too many chunks: $totalChunks")
        }

        if (text.length < chunkSize) {
            transactionId = null
            throw IllegalArgumentException("chunkSize = " + chunkSize + " invalid: data length = " + text.length)
        }

        if (tId != transactionId) {
            transactionId = tId
            chunks.clear()
            (0..<totalChunks).forEach { _ ->
                chunks.add(null)
            }
        }

        if (totalChunks != chunks.size) {
            transactionId = null
            throw IllegalArgumentException("Unexpected change of totalChunks : " + totalChunks + " <> " + chunks.size)
        }

        val data = text.take(chunkSize) //remove padding

        val chunk = chunks[chunkNo]
        if (chunk != null && chunk != data) {
            transactionId = null
            throw IllegalArgumentException("Chunk #$chunkNo: different data received")
        }

        Log.d(
            TAG,
            "Got chunk $chunkNo of $totalChunks, size: $chunkSize, tId: $tId"
        )
        chunks.removeAt(chunkNo)
        chunks.add(chunkNo, data)

        val cntReceived =
            chunks.stream().filter { obj -> Objects.nonNull(obj) }.count()
                .toInt()

        val bs = BitSet()
        for (i in chunks.indices) {
            bs.set(i, chunks[i] != null)
        }
        onChunkReceived(bs, cntReceived, totalChunks)

        if (cntReceived == chunks.size) {
            Log.d(TAG, "All chunks have been received")
            //remove line breaks
            val msg = chunks.joinToString("").replace("[\\r\\n]".toRegex(), "")
            transactionId = null
            onMessage(msg)
        }
    }

    abstract fun onMessage(message: String?)

    abstract fun onChunkReceived(receivedChunks: BitSet?, cntReceived: Int, totalChunks: Int)

    companion object {
        private val TAG: String = MessageParser::class.java.simpleName
    }
}

