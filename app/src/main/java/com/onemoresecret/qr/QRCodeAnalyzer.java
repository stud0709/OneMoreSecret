package com.onemoresecret.qr;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.onemoresecret.BuildConfig;
import com.onemoresecret.Util;

import java.util.function.Supplier;

public abstract class QRCodeAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = QRCodeAnalyzer.class.getSimpleName();

    public QRCodeAnalyzer(boolean zxingEnabled) {
        this.zxingEnabled = zxingEnabled;
    }

    private final boolean zxingEnabled;
    private Analyzer analyzer = null;
    private static final String ML_KIT_CLASSNAME = "com.onemoresecret.qr.MLKitBarcodeAnalyzer";


    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        try {
            if (zxingEnabled || BuildConfig.FLAVOR.equals(Util.FLAVOR_FOSS)) {
                if (analyzer == null || !(analyzer instanceof ZXingBarcodeAnalyzer))
                    analyzer = new ZXingBarcodeAnalyzer();
            } else {
                if (analyzer == null || !analyzer.getClass().getSimpleName().equals(ML_KIT_CLASSNAME)) {
                    analyzer = (Analyzer) Class.forName(ML_KIT_CLASSNAME).newInstance();
                }
            }

            analyzer.analyze(imageProxy, qr -> onQRCodeFound(qr));
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
            ex.printStackTrace();
            imageProxy.close();
        }
    }

    public abstract void onQRCodeFound(String barcodeValue);
}
