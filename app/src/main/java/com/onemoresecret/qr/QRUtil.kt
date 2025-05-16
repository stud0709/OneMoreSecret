package com.onemoresecret.qr;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class QRUtil {
    public static final String PROP_CHUNK_SIZE = "chunk_size", PROP_BARCODE_SIZE = "barcode_size";

    private QRUtil() {
    }

    /**
     * Cuts a message into chunks and creates a barcode for every chunk. Every barcode contains (as readable text, separated by TAB):
     * <ul>
     *     <li>transaction ID, same for all QR codes in the sequence</li>
     *     <li>chunk number</li>
     *     <li>total number of chunks</li>
     *     <li>data length in this chunk (padding is added to the last code)</li>
     *     <li>data</li>
     * </ul>
     */

    public static List<Bitmap> getQrSequence(String message, int chunkSize, int barcodeSize)
            throws WriterException {
        char[] data = message.toCharArray();
        QRCodeWriter writer = new QRCodeWriter();
        List<Bitmap> list = new ArrayList<>();
        char[] cArr;
        int chunks = (int) Math.ceil(data.length / (double) chunkSize);
        int charsToSend = data.length;
        String transactionId = Integer.toHexString((int) (Math.random() * 0xffff));

        for (int chunkNo = 0; chunkNo < chunks; chunkNo++) {
            // copy with padding to keep all barcodes equal in size
            cArr = Arrays.copyOfRange(data, chunkSize * chunkNo, chunkSize * (chunkNo + 1));

            List<String> bc = new ArrayList<>();
            bc.add(transactionId);
            bc.add(Integer.toString(chunkNo));
            bc.add(Integer.toString(chunks));
            bc.add(Integer.toString(Math.min(chunkSize, charsToSend)));
            bc.add(new String(cArr));

            String bcs = String.join("\t", bc);
            BitMatrix bitMatrix = writer.encode(bcs, BarcodeFormat.QR_CODE, barcodeSize, barcodeSize);
            list.add(toBitmap(bitMatrix));

            charsToSend -= chunkSize;
        }

        return list;
    }

    public static Bitmap getQr(String message, int barcodeSize)
            throws WriterException {
        var writer = new QRCodeWriter();
        var bitMatrix = writer.encode(message, BarcodeFormat.QR_CODE, barcodeSize, barcodeSize);
        return toBitmap(bitMatrix);
    }

    private static Bitmap toBitmap(BitMatrix bitMatrix) {
        var height = bitMatrix.getHeight();
        var width = bitMatrix.getWidth();
        var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    public static int getChunkSize(SharedPreferences preferences) {
        return preferences.getInt(PROP_CHUNK_SIZE, 200);
    }

    public static int getBarcodeSize(SharedPreferences preferences) {
        return preferences.getInt(PROP_BARCODE_SIZE, 400);
    }

}