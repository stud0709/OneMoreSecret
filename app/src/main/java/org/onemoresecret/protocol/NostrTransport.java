package org.onemoresecret.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Metadata-bound PSK transport. Old pairings must be discarded when upgrading. */
public final class NostrTransport {
    private static final byte[] PREFIX = "%OMS-NOSTR-".getBytes(StandardCharsets.US_ASCII);
    private NostrTransport() {}
    private static byte[] hex(String s) {
        if (!s.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid Nostr metadata");
        byte[] b = new byte[32]; for (int i = 0; i < 32; i++) b[i] = (byte)Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16); return b;
    }
    private static byte[] aad(String topic, String pubkey, long createdAt) {
        return ByteBuffer.allocate(PREFIX.length + 1 + 32 + 32 + 8).put(PREFIX).put((byte)1).put(hex(topic)).put(hex(pubkey)).putLong(createdAt).array();
    }
    public static byte[] encrypt(byte[] plain, byte[] psk, String topic, String pubkey, long createdAt) {
        if (psk.length != 32 || plain.length > OmsProtocol.MAX_BLOB) throw new IllegalArgumentException("Invalid Nostr payload");
        try {
            byte[] nonce = OmsProtocol.random(12); Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(psk, "AES"), new GCMParameterSpec(128, nonce)); c.updateAAD(aad(topic, pubkey, createdAt));
            byte[] cipher = c.doFinal(plain); return ByteBuffer.allocate(PREFIX.length + 1 + nonce.length + cipher.length).put(PREFIX).put((byte)1).put(nonce).put(cipher).array();
        } catch (java.security.GeneralSecurityException e) { throw new IllegalArgumentException("Nostr encryption failed", e); }
    }
    public static byte[] decrypt(byte[] encrypted, byte[] psk, String topic, String pubkey, long createdAt, long now) {
        if (createdAt < now - 60 || createdAt > now + 30) throw new IllegalArgumentException("Nostr event outside freshness window");
        if (psk.length != 32 || encrypted.length < PREFIX.length + 29 || encrypted.length > OmsProtocol.MAX_BLOB + PREFIX.length + 29) throw new IllegalArgumentException("Invalid Nostr payload");
        ByteBuffer b = ByteBuffer.wrap(encrypted); byte[] prefix = new byte[PREFIX.length]; b.get(prefix);
        if (!Arrays.equals(prefix, PREFIX) || b.get() != 1) throw new IllegalArgumentException("Unsupported Nostr transport");
        byte[] nonce = new byte[12]; b.get(nonce); byte[] cipher = new byte[b.remaining()]; b.get(cipher);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(psk, "AES"), new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(topic, pubkey, createdAt)); return c.doFinal(cipher);
        } catch (java.security.GeneralSecurityException e) { throw new IllegalArgumentException("Nostr authentication failed", e); }
    }
    /** Authenticated message IDs survive reconnects/restarts until their timestamp expires. */
    public static final class ReplayWindow {
        private final Map<String, Long> seen = new LinkedHashMap<>();
        public ReplayWindow(String encoded, long now) {
            if (encoded == null || encoded.isEmpty()) return;
            if (encoded.length() > 262144) throw new IllegalArgumentException("Replay state too large");
            for (String entry : encoded.split(";")) {
                String[] fields = entry.split(":");
                if (fields.length != 2 || !validId(fields[0])) throw new IllegalArgumentException("Invalid replay state");
                long expiry = Long.parseLong(fields[1]); if (expiry > now) seen.put(fields[0], expiry);
            }
            if (seen.size() > 4096) throw new IllegalArgumentException("Replay state too large");
        }
        private static boolean validId(String id) { return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
        public synchronized boolean accept(String id, long createdAt, long now) {
            if (!validId(id) || createdAt < now - 60 || createdAt > now + 30) return false;
            seen.entrySet().removeIf(e -> e.getValue() <= now);
            if (seen.containsKey(id) || seen.size() >= 4096) return false;
            seen.put(id, createdAt + 61); return true;
        }
        public synchronized String encode() { StringJoiner j = new StringJoiner(";"); seen.forEach((id, expiry) -> j.add(id + ":" + expiry)); return j.toString(); }
    }
}
