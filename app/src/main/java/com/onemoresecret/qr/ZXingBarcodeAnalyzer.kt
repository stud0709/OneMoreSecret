package com.onemoresecret.qr;

import static android.graphics.ImageFormat.YUV_420_888;
import static android.graphics.ImageFormat.YUV_422_888;
import static android.graphics.ImageFormat.YUV_444_888;

import androidx.camera.core.ImageProxy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class ZXingBarcodeAnalyzer implements Analyzer {
    @Override
    public void analyze(ImageProxy imageProxy, Consumer<String> onQRCodeFound) {
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
                onQRCodeFound.accept(result.getText());
            } catch (FormatException | ChecksumException | NotFoundException ignored) {

            }
        } finally {
            imageProxy.close();
        }
    }
}
