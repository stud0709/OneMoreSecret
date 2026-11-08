package com.onemoresecret

import android.content.SharedPreferences
import android.graphics.Bitmap
import com.onemoresecret.qr.QRUtil
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Cold-wallet layout shared by address generation and format updates. Receives no plaintext key. */
object BitcoinBackupDocument {
    fun createHtml(preferences: SharedPreferences, address: String, encryptedMessage: String): String {
        val size = QRUtil.getBarcodeSize(preferences)
        val publicQr = pngBase64(QRUtil.getQr(address, size))
        val encryptedQrs = QRUtil.getQrSequence(
            encryptedMessage, QRUtil.getChunkSize(preferences), size
        ).map(::pngBase64)
        return renderHtml(address, encryptedMessage, publicQr, encryptedQrs)
    }

    private fun pngBase64(bitmap: Bitmap): String = ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Cannot encode backup QR code" }
        Base64.getEncoder().encodeToString(output.toByteArray())
    }

    internal fun renderHtml(
        address: String, encryptedMessage: String, publicQr: String, encryptedQrs: List<String>
    ): String = buildString {
        append("<html><body><h1>OneMoreSecret Cold Wallet</h1>")
        append("<p>This is a hard copy of your Bitcoin Address <b>").append(address)
        append("</b>:</p><p><img src=\"data:image/png;base64,").append(publicQr)
        append("\" style=\"width:200px;height:200px;\"></p>")
        append("<p>The above QR code contains the public bitcoin address, ")
        append("use a regular QR code scanner to read it.</p><p>The private key is encrypted, ")
        append("scan the following QR code sequence <b>with OneMoreSecret</b> to access it:</p><p>")
        encryptedQrs.forEachIndexed { index, qr ->
            append("<table style=\"display: inline-block;\"><tr style=\"vertical-align: bottom;\"><td>")
            append(index + 1).append("</td><td><img src=\"data:image/png;base64,").append(qr)
            append("\" style=\"width:200px;height:200px;\"></td></tr></table>")
        }
        append("</p><p>The same as text:&nbsp;</p><p style=\"font-family:monospace;\">")
        encryptedMessage.chunked(Util.BASE64_LINE_LENGTH).forEach { append(it).append("<br>") }
        append("</p><p>Message format: bare Base64 containing %OMS- protocol version 1</p>")
        append("<p>Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/docs/protocol-v1.md</p>")
        append("</body></html>")
    }
}
