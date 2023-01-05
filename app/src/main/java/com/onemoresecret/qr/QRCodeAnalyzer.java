package com.onemoresecret.qr;

import static android.graphics.ImageFormat.YUV_420_888;
import static android.graphics.ImageFormat.YUV_422_888;
import static android.graphics.ImageFormat.YUV_444_888;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
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

public abstract class QRCodeAnalyzer implements ImageAnalysis.Analyzer {
    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            int rotation = image.getImageInfo().getRotationDegrees();

            if (image.getFormat() != YUV_420_888 && image.getFormat() != YUV_422_888 && image.getFormat() != YUV_444_888)
                return;

            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] imageData = new byte[byteBuffer.capacity()];
            byteBuffer.get(imageData);

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    imageData,
                    image.getWidth(), image.getHeight(),
                    0, 0,
                    image.getWidth(), image.getHeight(),
                    false
            );

            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            try {
                Result result = new QRCodeReader().decode(binaryBitmap);
                onQRCodeFound(result);
                return;
            } catch (FormatException | ChecksumException | NotFoundException e) {
                qrCodeNotFound();
            }
        } finally {
            image.close();
        }
    }

    public abstract void qrCodeNotFound();

    public abstract void onQRCodeFound(Result result);
}
