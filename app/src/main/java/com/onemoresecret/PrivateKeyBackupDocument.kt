package com.onemoresecret

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.text.Html
import com.google.zxing.WriterException
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.qr.QRUtil
import com.onemoresecret.qr.QRUtil.getQrSequence
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import kotlin.math.min

/** Shared document layout for newly generated and updated private-key backups. */
object PrivateKeyBackupDocument {
    @Throws(WriterException::class, IOException::class)
    fun createHtml(
        preferences: SharedPreferences, alias: String, fingerprint: ByteArray, message: ByteArray
    ): String = buildString {
        val list = getQrSequence(
            encodeAsOmsText(message),
            QRUtil.getChunkSize(preferences),
            QRUtil.getBarcodeSize(preferences)
        )
        append(
            """
            <html><body><h1>OneMoreSecret Private Key Backup</h1>
            <p><b>Keep this file / printout in a secure location</b></p><p>
            This is a hard copy of your Private Key for OneMoreSecret.
            It can be used to import your Private Key into a new device or after a reset of OneMoreSecret App.
            This document is encrypted with AES, you will need your TRANSPORT PASSWORD to complete the import procedure.</p>
            <h2>WARNING:</h2>
            <p>DO NOT share this document with other persons.<br>
            DO NOT provide its content to untrusted apps, on the Internet etc.<br>
            If you need to restore your Key, start OneMoreSecret App on your phone BY HAND and scan the codes.
            DO NOT trust unexpected prompts and pop-ups.<br>
            <b>THIS DOCUMENT IS THE ONLY WAY TO RESTORE YOUR PRIVATE KEY</b></p>
            <p><b>Key alias:&nbsp;${Html.escapeHtml(alias)}</b></p>
            <p><b>RSA Fingerprint:&nbsp;${byteArrayToHex(fingerprint)}</b></p>
            <p>Scan this with your OneMoreSecret App:</p><p>
            """.trimIndent()
        )

        for (i in list.indices) {
            val bitmap = list[i]
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.flush()
                append(
                    """
                    <table style="display: inline-block;">
                    <tr style="vertical-align: bottom;">
                    <td>${(i + 1)}</td>
                    <td>
                    <img src="data:image/png;base64,${
                        Base64.getEncoder().encodeToString(baos.toByteArray())
                    }" style="width:200px;height:200px;">
                    </td>
                    </tr>
                    </table>
                    """.trimIndent()
                )
            }
        }
        append(
            """
            </p>
            <h2>Long-Term Backup and Technical Details</h2>
            <p>Base64 Encoded Message:&nbsp;</p>
            <p style="font-family:monospace;">
            """.trimIndent()
        )

        val messageAsUrl = encodeAsOmsText(message)
        var offset = 0

        while (offset < messageAsUrl.length) {
            val s = messageAsUrl.substring(
                offset, min(offset + Util.BASE64_LINE_LENGTH, messageAsUrl.length)
            )
            append(s).append("<br>")
            offset += Util.BASE64_LINE_LENGTH
        }

        append(
            """
            </p>
            <p>Message format: bare Base64 containing %OMS- protocol version 1</p>
            <p>Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/docs/protocol-v1.md</p>
            </body>
            </html>
            """.trimIndent()
        )
    }

}
