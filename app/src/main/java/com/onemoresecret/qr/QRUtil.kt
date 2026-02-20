package com.onemoresecret.qr

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.CharArray
import kotlin.Int
import kotlin.Throws
import kotlin.math.ceil
import kotlin.math.min
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

object QRUtil {
    const val PROP_CHUNK_SIZE = "chunk_size"
    const val PROP_BARCODE_SIZE = "barcode_size"

    /**
     * Cuts a message into chunks and creates a barcode for every chunk. Every barcode contains (as readable text, separated by TAB):
     *
     *  * transaction ID, same for all QR codes in the sequence
     *  * chunk number
     *  * total number of chunks
     *  * data length in this chunk (padding is added to the last code)
     *  * data
     *
     */
    @JvmStatic
    @Throws(WriterException::class)
    fun getQrSequence(message: String, chunkSize: Int, barcodeSize: Int): MutableList<Bitmap> {
        val data = message.toCharArray()
        val writer = QRCodeWriter()
        val list: MutableList<Bitmap> = ArrayList()
        var cArr: CharArray
        val chunks = ceil(data.size / chunkSize.toDouble()).toInt()
        var charsToSend = data.size
        val transactionId = Integer.toHexString((Math.random() * 0xffff).toInt())

        for (chunkNo in 0..<chunks) {
            // copy with padding to keep all barcodes equal in size
            cArr = data.copyOfRange(chunkSize * chunkNo, min(data.size, chunkSize * (chunkNo + 1)))
            cArr = cArr.copyOf(chunkSize)

            val bc: MutableList<String> = ArrayList()
            bc.add(transactionId)
            bc.add(chunkNo.toString())
            bc.add(chunks.toString())
            bc.add(min(chunkSize, charsToSend).toString())
            bc.add(String(cArr))

            val bcs = bc.joinToString ("\t")
            val bitMatrix = writer.encode(bcs, BarcodeFormat.QR_CODE, barcodeSize, barcodeSize)
            list.add(toBitmap(bitMatrix))

            charsToSend -= chunkSize
        }

        return list
    }

    @JvmStatic
    @Throws(WriterException::class)
    fun getQr(message: String, barcodeSize: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(message, BarcodeFormat.QR_CODE, barcodeSize, barcodeSize)
        return toBitmap(bitMatrix)
    }

    private fun toBitmap(bitMatrix: BitMatrix): Bitmap {
        val height = bitMatrix.height
        val width = bitMatrix.width
        val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0..<width) {
            for (y in 0..<height) {
                bitmap[x, y] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return bitmap
    }

    @JvmStatic
    fun getChunkSize(preferences: SharedPreferences): Int {
        return preferences.getInt(PROP_CHUNK_SIZE, 200)
    }

    @JvmStatic
    fun getBarcodeSize(preferences: SharedPreferences): Int {
        return preferences.getInt(PROP_BARCODE_SIZE, 400)
    }
}