package org.onemoresecret.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;
import java.util.function.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Portable OMS v1 codec. Keep this source identical in Android and Companion. */
public final class OmsProtocol {
    public static final byte[] MAGIC = "%OMS-".getBytes(StandardCharsets.US_ASCII);
    public static final int VERSION = 1, RSA = 1, PASSWORD = 2, CONTROL = 3;
    public static final int CHUNK_SIZE = 65536, MAX_BLOB = 1048576, MAX_ITERATIONS = 10000000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private OmsProtocol() {}

    public static final class Header {
        public final int kind, iterations;
        public final byte[] encoded, fingerprint, wrappedKey, noncePrefix, salt;
        Header(int kind, int iterations, byte[] encoded, byte[] fingerprint, byte[] wrappedKey, byte[] noncePrefix, byte[] salt) {
            this.kind = kind; this.iterations = iterations; this.encoded = encoded;
            this.fingerprint = fingerprint; this.wrappedKey = wrappedKey; this.noncePrefix = noncePrefix; this.salt = salt;
        }
    }
    public static boolean hasMagic(byte[] bytes) {
        if (bytes.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) if (bytes[i] != MAGIC[i]) return false;
        return true;
    }
    public static String encodeText(byte[] bytes) {
        return (hasMagic(bytes) ? "" : "oms00_") + Base64.getEncoder().encodeToString(bytes);
    }
    /** A bare Base64 string is OMS only when its decoded magic matches. */
    public static byte[] decodeText(String text) {
        String clean = text.trim().replaceAll("[\\t\\r\\n ]", "");
        boolean legacy = clean.startsWith("oms00_");
        if (legacy) clean = clean.substring(6);
        if (clean.length() > MAX_BLOB * 2) throw new IllegalArgumentException("OMS text too large");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(clean); }
        catch (IllegalArgumentException e) { if (legacy) throw e; return null; }
        if (legacy) return bytes;
        if (!hasMagic(bytes)) return null;
        try { readHeader(new DataInputStream(new ByteArrayInputStream(bytes))); }
        catch (IOException e) { throw new IllegalArgumentException(e.getMessage(), e); }
        return bytes;
    }
    public static byte[] random(int length) { byte[] b = new byte[length]; RANDOM.nextBytes(b); return b; }
    private static byte[] frame(int kind, byte[] parameters) throws IOException {
        if (parameters.length > 2048) throw new IOException("OMS header too large");
        ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(b);
        d.write(MAGIC); d.writeByte(VERSION); d.writeByte(kind); d.writeShort(parameters.length); d.write(parameters);
        return b.toByteArray();
    }
    public static Header readHeader(DataInputStream in) throws IOException {
        return readHeader(in, in.readUnsignedShort());
    }
    /** first two bytes have already been consumed by the legacy/v1 dispatcher. */
    public static Header readHeader(DataInputStream in, int first) throws IOException {
        byte[] magic = new byte[5]; magic[0] = (byte)(first >>> 8); magic[1] = (byte)first; in.readFully(magic, 2, 3);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Invalid OMS magic");
        int version = in.readUnsignedByte();
        if (version != VERSION) throw new IOException("Unsupported OMS protocol version: " + version);
        int kind = in.readUnsignedByte(), length = in.readUnsignedShort();
        if (length > 2048) throw new IOException("OMS header too large");
        byte[] parameters = new byte[length]; in.readFully(parameters);
        DataInputStream p = new DataInputStream(new ByteArrayInputStream(parameters));
        byte[] fingerprint = new byte[0], wrapped = new byte[0], nonce = new byte[0], salt = new byte[0]; int iterations = 0;
        if (kind == RSA) {
            fingerprint = new byte[32]; p.readFully(fingerprint); wrapped = readBlob(p, 1024);
            if (wrapped.length < 256) throw new IOException("RSA keys must be at least 2048 bits");
            nonce = new byte[8]; p.readFully(nonce);
        } else if (kind == PASSWORD) {
            salt = new byte[16]; p.readFully(salt); iterations = p.readInt(); checkIterations(iterations);
            nonce = new byte[8]; p.readFully(nonce);
        } else if (kind != CONTROL || length != 0) throw new IOException("Unsupported OMS envelope kind");
        if (p.read() != -1) throw new IOException("Unexpected OMS header fields");
        return new Header(kind, iterations, frame(kind, parameters), fingerprint, wrapped, nonce, salt);
    }
    /** Bounded, API-31-compatible alternative to InputStream.readAllBytes. */
    public static byte[] readRemaining(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096];
        while (true) {
            int n = in.read(buffer); if (n < 0) return out.toByteArray();
            if (n == 0) { int b = in.read(); if (b < 0) return out.toByteArray(); if (out.size() == limit) throw new IOException("OMS input too large"); out.write(b); }
            else { if (n > limit - out.size()) throw new IOException("OMS input too large"); out.write(buffer, 0, n); }
        }
    }
    public static byte[] readBlob(DataInputStream in, int limit) throws IOException {
        long length = Integer.toUnsignedLong(in.readInt());
        if (length > limit) throw new IOException("OMS field too large");
        byte[] bytes = new byte[(int)length]; in.readFully(bytes); return bytes;
    }
    public static void writeBlob(DataOutputStream out, byte[] bytes) throws IOException {
        if (bytes.length > MAX_BLOB) throw new IOException("OMS field too large");
        out.writeInt(bytes.length); out.write(bytes);
    }
    public static byte[] application(int id, byte[] body) {
        if (id < 0 || id > 65535) throw new IllegalArgumentException("Invalid application ID");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try { DataOutputStream d = new DataOutputStream(b); d.writeShort(id); d.writeShort(1); d.write(body); }
        catch (IOException e) { throw new UncheckedIOException(e); }
        return b.toByteArray();
    }
    public static int readApplication(DataInputStream in) throws IOException {
        int id = in.readUnsignedShort(), version = in.readUnsignedShort();
        if (version != 1) throw new IOException("Unsupported OMS application version: " + version);
        return id;
    }
    public static byte[] control(int id, byte[] body) {
        try { ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(frame(CONTROL, new byte[0])); b.write(application(id, body)); return b.toByteArray(); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static final class Request {
        public final int applicationId, targetProtocol;
        public final byte[] id, legacy;
        Request(int applicationId, int targetProtocol, byte[] id, byte[] legacy) { this.applicationId = applicationId; this.targetProtocol = targetProtocol; this.id = id; this.legacy = legacy; }
    }
    /** Control v1 keeps the bounded v0 request fields after its correlation and target-version fields. */
    public static byte[] request(byte[] legacy, int targetProtocol, byte[] id) {
        if (legacy.length < 2 || legacy.length > 16384 || id.length != 16 || targetProtocol < 0 || targetProtocol > 1) throw new IllegalArgumentException("Invalid OMS request");
        int app = ((legacy[0] & 255) << 8) | (legacy[1] & 255);
        if (app != 4 && app != 11 && app != 13) throw new IllegalArgumentException("Invalid request application");
        byte[] b = new byte[17 + legacy.length - 2]; System.arraycopy(id, 0, b, 0, 16); b[16] = (byte)targetProtocol; System.arraycopy(legacy, 2, b, 17, legacy.length - 2);
        return control(app, b);
    }
    public static Request readRequest(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        Header h = readHeader(in); if (h.kind != CONTROL) throw new IOException("Not a control message");
        int app = readApplication(in); if (app != 4 && app != 11 && app != 13) throw new IOException("Invalid request application");
        byte[] id = new byte[16]; in.readFully(id); int target = in.readUnsignedByte(); if (target > 1) throw new IOException("Unsupported target protocol");
        byte[] rest = readRemaining(in, 16382);
        ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(b); d.writeShort(app); d.write(rest);
        return new Request(app, target, id, b.toByteArray());
    }
    public static byte[] reply(byte[] requestId, boolean paired, byte[] key) {
        if (requestId.length != 16 || key.length > 1024 || (paired && key.length != 16 && key.length != 24 && key.length != 32)) throw new IllegalArgumentException("Invalid OMS reply");
        try { ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(b); d.write(requestId); d.writeByte(paired ? 1 : 0); writeBlob(d, key); return control(5, b.toByteArray()); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static byte[] readReply(byte[] bytes, byte[] requestId, boolean paired) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (readHeader(in).kind != CONTROL || readApplication(in) != 5) throw new IOException("Not a key response");
        byte[] id = new byte[16]; in.readFully(id); if (!MessageDigest.isEqual(id, requestId)) throw new IOException("Mismatched OMS response");
        if (in.readUnsignedByte() != (paired ? 1 : 0)) throw new IOException("Wrong OMS response mode");
        byte[] key = readBlob(in, 1024); if (paired && key.length != 16 && key.length != 24 && key.length != 32) throw new IOException("Invalid AES key length");
        if (in.read() != -1) throw new IOException("Trailing response data"); return key;
    }
    public static byte[] fingerprint(RSAPublicKey key) {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(key.getModulus().toByteArray()); return digest.digest(key.getPublicExponent().toByteArray()); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static byte[] rsa(int mode, Key key, byte[] bytes) {
        try { Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding"); c.init(mode, key, OAEP); return c.doFinal(bytes); }
        catch (GeneralSecurityException e) { throw new IllegalArgumentException("OMS RSA operation failed", e); }
    }
    private static Header rsaHeader(RSAPublicKey publicKey, byte[] key) throws IOException {
        if (publicKey.getModulus().bitLength() < 2048) throw new IOException("RSA keys must be at least 2048 bits");
        ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(b);
        d.write(fingerprint(publicKey)); writeBlob(d, rsa(Cipher.ENCRYPT_MODE, publicKey, key)); d.write(random(8));
        return readHeader(new DataInputStream(new ByteArrayInputStream(frame(RSA, b.toByteArray()))));
    }
    public static byte[] seal(RSAPublicKey publicKey, int app, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try { encrypt(publicKey, app, new ByteArrayInputStream(body), out, null, null); return out.toByteArray(); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static void encrypt(RSAPublicKey publicKey, int app, InputStream source, OutputStream out, Supplier<Boolean> cancelled, Consumer<Long> progress) throws IOException {
        byte[] key = random(32);
        try {
            Header h = rsaHeader(publicKey, key); out.write(h.encoded);
            InputStream payload = new SequenceInputStream(new ByteArrayInputStream(application(app, new byte[0])), source);
            encryptRecords(h, key, payload, out, cancelled, progress);
        } finally { Arrays.fill(key, (byte)0); }
    }
    public static byte[] passwordSeal(byte[] key, byte[] salt, int iterations, int app, byte[] body) {
        try {
            if (salt.length != 16 || key.length != 32) throw new IOException("Invalid password parameters"); checkIterations(iterations);
            ByteArrayOutputStream p = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(p);
            d.write(salt); d.writeInt(iterations); d.write(random(8));
            Header h = readHeader(new DataInputStream(new ByteArrayInputStream(frame(PASSWORD, p.toByteArray()))));
            ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(h.encoded);
            encryptRecords(h, key, new ByteArrayInputStream(application(app, body)), out, null, null); return out.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static void checkIterations(int iterations) throws IOException {
        if (iterations < 1 || iterations > MAX_ITERATIONS) throw new IOException("Unsupported PBKDF2 iteration count");
    }
    public static byte[] passwordKey(char[] password, Header h) {
        PBEKeySpec spec = new PBEKeySpec(password, h.salt, h.iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalArgumentException("OMS password derivation failed", e); }
        finally { spec.clearPassword(); }
    }
    private static byte[] crypt(int mode, Header h, byte[] key, long index, int flag, byte[] bytes, int length) {
        if (key.length != 32 || index > 0xffffffffL) throw new IllegalArgumentException("Invalid OMS record key or index");
        try {
            byte[] nonce = ByteBuffer.allocate(12).put(h.noncePrefix).putInt((int)index).array();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            c.updateAAD(h.encoded); c.updateAAD(ByteBuffer.allocate(9).putInt((int)index).put((byte)flag).putInt(length).array());
            return c.doFinal(bytes);
        } catch (GeneralSecurityException e) { throw new IllegalArgumentException("OMS record authentication failed", e); }
    }
    private static void checkCancelled(Supplier<Boolean> cancelled) throws IOException { if (cancelled != null && cancelled.get()) throw new InterruptedIOException("OMS operation cancelled"); }
    public static void encryptRecords(Header h, byte[] key, InputStream in, OutputStream out, Supplier<Boolean> cancelled, Consumer<Long> progress) throws IOException {
        DataOutputStream d = new DataOutputStream(out); long index = 0, total = 0;
        byte[] buffer = new byte[CHUNK_SIZE];
        try {
            while (true) {
                checkCancelled(cancelled); int count = 0;
                while (count < buffer.length) { int n = in.read(buffer, count, buffer.length - count); if (n < 0) break; if (n == 0) { int b = in.read(); if (b < 0) break; buffer[count++] = (byte)b; } else count += n; }
                if (count == 0) break;
                if (index == 0xffffffffL) throw new IOException("OMS record counter exhausted");
                int length = count + 16; d.writeInt(length); d.writeByte(0); d.write(crypt(Cipher.ENCRYPT_MODE, h, key, index++, 0, Arrays.copyOf(buffer, count), length));
                total += count; if (progress != null) progress.accept(total);
            }
            checkCancelled(cancelled); d.writeInt(16); d.writeByte(1); d.write(crypt(Cipher.ENCRYPT_MODE, h, key, index, 1, new byte[0], 16));
        } finally { Arrays.fill(buffer, (byte)0); }
    }
    /** Writes authenticated records to a private staging output; caller must publish only after this returns. */
    public static void decryptRecords(Header h, byte[] key, InputStream input, OutputStream output, Supplier<Boolean> cancelled, Consumer<Long> progress) throws IOException {
        DataInputStream in = new DataInputStream(input); long index = 0, total = 0;
        while (true) {
            checkCancelled(cancelled); long length = Integer.toUnsignedLong(in.readInt()); int flag = in.readUnsignedByte();
            if ((flag == 0 && (length < 17 || length > CHUNK_SIZE + 16 || index == 0xffffffffL)) || (flag == 1 && length != 16) || flag > 1) throw new IOException("Invalid OMS record");
            byte[] cipher = new byte[(int)length]; in.readFully(cipher);
            byte[] plain = crypt(Cipher.DECRYPT_MODE, h, key, index++, flag, cipher, (int)length);
            try {
                if (flag == 1) { if (in.read() != -1) throw new IOException("Trailing OMS data"); return; }
                output.write(plain); total += plain.length; if (progress != null) progress.accept(total);
            } finally { Arrays.fill(plain, (byte)0); }
        }
    }
    public static byte[] open(Header h, byte[] key, byte[] records) {
        try { ByteArrayOutputStream b = new ByteArrayOutputStream(); decryptRecords(h, key, new ByteArrayInputStream(records), b, null, null); return b.toByteArray(); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    /** Removes/validates the encrypted file application header before writing file contents. */
    public static void decryptFile(Header h, byte[] key, InputStream in, OutputStream out, Supplier<Boolean> cancelled, Consumer<Long> progress) throws IOException {
        final int[] consumed = {0};
        OutputStream body = new OutputStream() {
            final byte[] prefix = new byte[4]; int count;
            @Override public void write(int value) throws IOException { if (count < 4) { prefix[count++] = (byte)value; consumed[0] = count; if (count == 4 && readApplication(new DataInputStream(new ByteArrayInputStream(prefix))) != 3) throw new IOException("Not an OMS file"); } else out.write(value); }
            @Override public void write(byte[] b, int off, int len) throws IOException { while (count < 4 && len > 0) { write(b[off++]); len--; } if (len > 0) out.write(b, off, len); }
        };
        decryptRecords(h, key, in, body, cancelled, progress);
        if (consumed[0] != 4) throw new IOException("Missing OMS application header");
    }
    public static void validateKeyPair(byte[] privateBytes, byte[] publicBytes) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA"); PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(publicBytes));
            byte[] challenge = random(32); Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(privateKey); signature.update(challenge); byte[] signed = signature.sign();
            signature.initVerify(publicKey); signature.update(challenge); if (!signature.verify(signed)) throw new GeneralSecurityException("Mismatched RSA keys");
        } catch (GeneralSecurityException e) { throw new IllegalArgumentException("Invalid RSA key pair", e); }
    }
}
