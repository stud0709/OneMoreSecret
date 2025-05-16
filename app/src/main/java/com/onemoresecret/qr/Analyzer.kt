package com.onemoresecret.qr

import androidx.camera.core.ImageProxy
import java.util.function.Consumer

interface Analyzer {
    fun analyze(imageProxy: ImageProxy, onQRCodeFound: Consumer<String?>)
}
