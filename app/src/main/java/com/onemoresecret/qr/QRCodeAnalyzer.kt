package com.onemoresecret.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.onemoresecret.BuildConfig
import com.onemoresecret.Util

abstract class QRCodeAnalyzer(private val zxingEnabled: Boolean) : ImageAnalysis.Analyzer {
    private var analyzer: Analyzer? = null

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (zxingEnabled || BuildConfig.FLAVOR == Util.FLAVOR_FOSS) {
                if (analyzer == null || analyzer !is ZXingBarcodeAnalyzer) {
                    analyzer = ZXingBarcodeAnalyzer()
                }
            } else {
                if (analyzer == null || analyzer?.javaClass?.simpleName != ML_KIT_CLASSNAME) {
                    analyzer = Class.forName(ML_KIT_CLASSNAME).getDeclaredConstructor().newInstance() as Analyzer?
                }
            }

            analyzer!!.analyze(
                imageProxy
            ) { barcodeValue: String? -> this.onQRCodeFound(barcodeValue) }
        } catch (ex: ClassNotFoundException) {
            ex.printStackTrace()
            imageProxy.close()
        } catch (ex: IllegalAccessException) {
            ex.printStackTrace()
            imageProxy.close()
        } catch (ex: InstantiationException) {
            ex.printStackTrace()
            imageProxy.close()
        }
    }

    abstract fun onQRCodeFound(barcodeValue: String?)

    companion object {
        private val TAG: String = QRCodeAnalyzer::class.java.simpleName

        private const val ML_KIT_CLASSNAME = "com.onemoresecret.qr.MLKitBarcodeAnalyzer"
    }
}
