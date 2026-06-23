package com.onemoresecret.qr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.function.Consumer

class Analyzer {
    private var barcodeScanner: BarcodeScanner? = null
    private var mlTask: Task<List<Barcode>>? = null

    @OptIn(ExperimentalGetImage::class)
    fun analyze(imageProxy: ImageProxy, onQRCodeFound: Consumer<String?>) {
        if (mlTask != null) return

        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            if (barcodeScanner == null) {
                barcodeScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )
            }
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            mlTask = barcodeScanner!!.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    mlTask = null
                    imageProxy.close()
                    for (barcode in barcodes) {
                        onQRCodeFound.accept(barcode.rawValue)
                    }
                }
                .addOnFailureListener { e ->
                    mlTask = null
                    imageProxy.close()
                    e.printStackTrace()
                }
        }
    }
}
