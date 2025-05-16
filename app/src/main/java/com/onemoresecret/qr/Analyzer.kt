package com.onemoresecret.qr;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.util.function.Consumer;

public interface Analyzer {
    void analyze(@NonNull ImageProxy imageProxy, Consumer<String> onQRCodeFound);
}
