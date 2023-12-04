package com.onemoresecret.qr;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.function.Consumer;

public class MLKitBarcodeAnalyzer implements Analyzer {
    private BarcodeScanner barcodeScanner;
    private Task<List<Barcode>> mlTask = null;

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(ImageProxy imageProxy, Consumer<String> onQRCodeFound) {
        if (mlTask != null) return;

        var mediaImage = imageProxy.getImage();

        if (mediaImage != null) {
            if (barcodeScanner == null) {
                barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
            }
            var inputImage =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            mlTask = barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        mlTask = null;
                        imageProxy.close();
                        for (var barcode : barcodes) {
                            onQRCodeFound.accept(barcode.getRawValue());
                        }
                    })
                    .addOnFailureListener(e -> {
                        imageProxy.close();
                        e.printStackTrace();
                    });
        }
    }
}
