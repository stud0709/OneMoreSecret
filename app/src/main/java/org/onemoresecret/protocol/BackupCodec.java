package org.onemoresecret.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Validated key backup import, including the historical default-iteration overflow. */
public final class BackupCodec {
    private BackupCodec() {}
    public static final class Backup {
        public final String alias;
        public final byte[] privateKey, publicKey;
        Backup(String alias, byte[] privateKey, byte[] publicKey) { this.alias = alias; this.privateKey = privateKey; this.publicKey = publicKey; }
    }
    private static byte[] legacyBlob(DataInputStream in) throws IOException { byte[] b = new byte[in.readUnsignedShort()]; in.readFully(b); return b; }
    private static Backup parse(byte[] plain, String legacyAlias, boolean modern) throws IOException {
        byte[] privateKey = null, publicKey = null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain)); String alias = legacyAlias;
            if (modern) {
                if (OmsProtocol.readApplication(in) != 0) throw new IOException("Not a key backup");
                alias = new String(OmsProtocol.readBlob(in, 4096), StandardCharsets.UTF_8);
                privateKey = OmsProtocol.readBlob(in, 16384); publicKey = OmsProtocol.readBlob(in, 16384);
            } else { privateKey = legacyBlob(in); publicKey = legacyBlob(in); }
            if (in.read() != -1 || alias == null || alias.trim().isEmpty()) throw new IOException("Invalid key backup");
            OmsProtocol.validateKeyPair(privateKey, publicKey); return new Backup(alias, privateKey, publicKey);
        } catch (IOException | RuntimeException e) { if (privateKey != null) Arrays.fill(privateKey, (byte)0); throw e; }
        finally { Arrays.fill(plain, (byte)0); }
    }
    public static Backup open(byte[] encoded, char[] password) throws IOException {
        if (encoded.length > 131072) throw new IOException("Key backup too large");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        if (OmsProtocol.hasMagic(encoded)) {
            OmsProtocol.Header header = OmsProtocol.readHeader(in); if (header.kind != OmsProtocol.PASSWORD) throw new IOException("Not a password backup");
            byte[] key = OmsProtocol.passwordKey(password, header);
            try { return parse(OmsProtocol.open(header, key, OmsProtocol.readRemaining(in, 131072)), null, true); }
            finally { Arrays.fill(key, (byte)0); }
        }
        if (in.readUnsignedShort() != 0) throw new IOException("Not a legacy key backup");
        String alias = new String(legacyBlob(in), StandardCharsets.UTF_8);
        byte[] salt = legacyBlob(in), iv = legacyBlob(in); int aes = in.readUnsignedShort(), kdf = in.readUnsignedShort(), bits = in.readUnsignedShort(), count = in.readUnsignedShort();
        byte[] encrypted = legacyBlob(in);
        if (in.read() != -1 || aes > 2 || kdf != 0 || (bits != 128 && bits != 192 && bits != 256) || count < 1 || salt.length < 8 || salt.length > 1024 || iv.length != (aes == 2 ? 12 : 16)) throw new IOException("Invalid legacy backup parameters");
        try { return legacyOpen(alias, password, salt, iv, aes, bits, count, encrypted); }
        catch (IOException | RuntimeException e) { if (count != 10176) throw e; return legacyOpen(alias, password, salt, iv, aes, bits, 600000, encrypted); }
    }
    private static Backup legacyOpen(String alias, char[] password, byte[] salt, byte[] iv, int aes, int bits, int count, byte[] encrypted) throws IOException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, count, bits); byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance(aes == 2 ? "AES/GCM/NoPadding" : "AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), aes == 2 ? new GCMParameterSpec(128, iv) : new IvParameterSpec(iv));
            return parse(cipher.doFinal(encrypted), alias, false);
        } catch (java.security.GeneralSecurityException e) { throw new IOException("Invalid backup or passphrase", e); }
        finally { spec.clearPassword(); if (key != null) Arrays.fill(key, (byte)0); }
    }
}
