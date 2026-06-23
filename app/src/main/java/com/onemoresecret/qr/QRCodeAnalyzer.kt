package com.onemoresecret.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

abstract class QRCodeAnalyzer : ImageAnalysis.Analyzer {
    private var analyzer: Analyzer? = null

    override fun analyze(imageProxy: ImageProxy) {
        if (analyzer == null) {
            analyzer = BarcodeAnalyzerFactory.createAnalyzer()
        }

        analyzer!!.analyze(imageProxy) { barcodeValue: String? ->
            this.onQRCodeFound(barcodeValue)
        }
    }

    abstract fun onQRCodeFound(barcodeValue: String?)

    companion object {
        private val TAG: String = QRCodeAnalyzer::class.java.simpleName
    }
}
