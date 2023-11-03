package com.onemoresecret.qr;

import static android.graphics.ImageFormat.YUV_420_888;
import static android.graphics.ImageFormat.YUV_422_888;
import static android.graphics.ImageFormat.YUV_444_888;

import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.w3c.dom.Text;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Supplier;

public abstract class QRCodeAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = QRCodeAnalyzer.class.getSimpleName();
    public QRCodeAnalyzer(Supplier<Boolean> isZxingEnabled) {
        this.isZxingEnabled = isZxingEnabled;
    }
    private final Supplier<Boolean> isZxingEnabled;
    private BarcodeScanner barcodeScanner;
    private Task<List<Barcode>> mlTask = null;

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (isZxingEnabled.get()) {
            useZxing(imageProxy);
        } else {
            useMlKit(imageProxy);
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void useMlKit(ImageProxy imageProxy) {
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
                            onQRCodeFound(barcode.getRawValue());
                        }
                    })
                    .addOnFailureListener(e -> {
                        imageProxy.close();
                        e.printStackTrace();
                    });
        }
    }

    private void useZxing(ImageProxy imageProxy) {
        try {
            if (imageProxy.getFormat() != YUV_420_888 && imageProxy.getFormat() != YUV_422_888 && imageProxy.getFormat() != YUV_444_888)
                return;

            ByteBuffer byteBuffer = imageProxy.getPlanes()[0].getBuffer();
            byte[] imageData = new byte[byteBuffer.capacity()];
            byteBuffer.get(imageData);

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    imageData,
                    imageProxy.getWidth(), imageProxy.getHeight(),
                    0, 0,
                    imageProxy.getWidth(), imageProxy.getHeight(),
                    false
            );

            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            try {
                Result result = new QRCodeReader().decode(binaryBitmap);
                onQRCodeFound(result.getText());
            } catch (FormatException | ChecksumException | NotFoundException ignored) {

            }
        } finally {
            imageProxy.close();
        }
    }

    public abstract void onQRCodeFound(String barcodeValue);

}
