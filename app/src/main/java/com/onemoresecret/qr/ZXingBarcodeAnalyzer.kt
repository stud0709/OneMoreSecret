package com.onemoresecret.qr

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.function.Consumer

class ZXingBarcodeAnalyzer : Analyzer {
    override fun analyze(imageProxy: ImageProxy, onQRCodeFound: Consumer<String?>) {
        try {
            if (imageProxy.format !in setOf(
                    ImageFormat.YUV_420_888,
                    ImageFormat.YUV_422_888,
                    ImageFormat.YUV_444_888
                )
            ) return

            val byteBuffer = imageProxy.planes[0].buffer
            val imageData = ByteArray(byteBuffer.capacity())
            byteBuffer.get(imageData)

            val source = PlanarYUVLuminanceSource(
                imageData,
                imageProxy.width, imageProxy.height,
                0, 0,
                imageProxy.width, imageProxy.height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = QRCodeReader().decode(binaryBitmap)
                onQRCodeFound.accept(result.text)
            } catch (_: FormatException) {
            } catch (_: ChecksumException) {
            } catch (_: NotFoundException) {
            }
        } finally {
            imageProxy.close()
        }
    }
}
