package com.onemoresecret.qr;

import android.util.Log;

import com.onemoresecret.crypto.OneTimePassword;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MessageParser {
    private static final String TAG = MessageParser.class.getSimpleName();
    protected final List<String> chunks = new ArrayList<>();
    protected String transactionId = null;
    protected final AtomicBoolean eventFired = new AtomicBoolean(false);

    public void consume(String qrCode) {
        //check other supported formats
        if (new OneTimePassword(qrCode).isValid()) {
            Log.d(TAG, "Looks like a valid TOTP");
            transactionId = null;
            if (!eventFired.get()) {
                onMessage(qrCode);
                eventFired.set(true);
            }
            return;
        }

        String[] sArr = qrCode.split("\\t", 5);
        String tId = sArr[0];
        int chunkNo = Integer.parseInt(sArr[1]);
        int totalChunks = Integer.parseInt(sArr[2]);
        int chunkSize = Integer.parseInt(sArr[3]);
        String text = sArr[4];

        if (chunkSize > 1024) {
            transactionId = null;
            throw new IllegalArgumentException("Too large chunks: " + chunkSize);
        }

        if (totalChunks > 1024) {
            transactionId = null;
            throw new IllegalArgumentException("Too many chunks: " + totalChunks);
        }

        if (text.length() < chunkSize) {
            transactionId = null;
            throw new IllegalArgumentException("chunkSize = " + chunkSize + " invalid: data length = " + text.length());
        }

        if (!tId.equals(transactionId)) {
            transactionId = tId;
            chunks.clear();
            for (int i = 0; i < totalChunks; i++) {
                chunks.add(null);
            }
        }

        if (totalChunks != chunks.size()) {
            transactionId = null;
            throw new IllegalArgumentException("Unexpected change of totalChunks : " + totalChunks + " <> " + chunks.size());
        }

        String data = text.substring(0, chunkSize); //remove padding

        String chunk = chunks.get(chunkNo);
        if (chunk != null && !chunk.equals(data)) {
            transactionId = null;
            throw new IllegalArgumentException("Chunk #" + chunkNo + ": different data received");
        }

        Log.d(TAG, "Got chunk " + chunkNo + " of " + totalChunks + ", size: " + chunkSize + ", tId: " + tId);
        chunks.remove(chunkNo);
        chunks.add(chunkNo, data);

        int cntReceived = (int) chunks.stream().filter(Objects::nonNull).count();

        BitSet bs = new BitSet();
        for (int i = 0; i < chunks.size(); i++) {
            bs.set(i, chunks.get(i) != null);
        }
        onChunkReceived(bs, cntReceived, totalChunks);

        if (cntReceived == chunks.size()) {
            Log.d(TAG, "All chunks have been received");
            //remove line breaks
            String msg = String.join("", chunks).replaceAll("[\\r\\n]", "");
            transactionId = null;
            if (!eventFired.get()) {
                onMessage(msg);
                eventFired.set(true);
            }
        }
    }

    public abstract void onMessage(String message);

    public abstract void onChunkReceived(BitSet receivedChunks, int cntReceived, int totalChunks);
}

