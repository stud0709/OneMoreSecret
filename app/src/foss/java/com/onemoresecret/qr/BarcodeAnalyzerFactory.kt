package com.onemoresecret.qr

object BarcodeAnalyzerFactory {
    fun createAnalyzer(): Analyzer {
        return ZXingBarcodeAnalyzer()
    }
}
