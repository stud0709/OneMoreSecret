package org.onemoresecret.protocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pairing application v1: explicitly separate request timeout from session cleanup TTL. */
public final class NostrPairing {
    private NostrPairing() {}
    public static final class Payload {
        public final String topic;
        public final byte[] psk;
        public final int requestTimeout, sessionTtl;
        public final List<String> relays;
        Payload(String topic, byte[] psk, int requestTimeout, int sessionTtl, List<String> relays) { this.topic = topic; this.psk = psk; this.requestTimeout = requestTimeout; this.sessionTtl = sessionTtl; this.relays = relays; }
    }
    public static byte[] create(String topic, byte[] psk, int requestTimeout, int sessionTtl, List<String> relays) throws IOException {
        if (!topic.matches("[0-9a-f]{64}") || psk.length != 32) throw new IOException("Invalid pairing keys");
        if (requestTimeout < 1 || requestTimeout > 86400 || sessionTtl < 1 || sessionTtl > 604800 || relays.size() < 1 || relays.size() > 32) throw new IOException("Invalid pairing limits");
        ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(b); d.writeByte(1);
        for (int i = 0; i < 32; i++) d.writeByte(Integer.parseInt(topic.substring(i*2,i*2+2),16));
        d.write(psk); d.writeInt(requestTimeout); d.writeInt(sessionTtl); d.writeShort(relays.size());
        for (String url : relays) OmsProtocol.writeBlob(d,url.getBytes(StandardCharsets.UTF_8));
        byte[] result = b.toByteArray(); read(result); return result;
    }
    public static Payload read(byte[] bytes) throws IOException {
        if (bytes.length > 65536) throw new IOException("Pairing payload too large");
        DataInputStream d = new DataInputStream(new ByteArrayInputStream(bytes));
        if (d.readUnsignedByte() != 1) throw new IOException("Recreate pairing with updated clients");
        byte[] topic = new byte[32], psk = new byte[32]; d.readFully(topic); d.readFully(psk);
        int timeout = d.readInt(), ttl = d.readInt(), count = d.readUnsignedShort();
        if (timeout < 1 || timeout > 86400 || ttl < 1 || ttl > 604800 || count < 1 || count > 32) throw new IOException("Invalid pairing limits");
        List<String> relays = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String url = new String(OmsProtocol.readBlob(d,2048), StandardCharsets.UTF_8);
            java.net.URI uri;
            try { uri = java.net.URI.create(url); } catch (IllegalArgumentException e) { throw new IOException("Invalid relay URL",e); }
            if (!"wss".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IOException("Invalid relay URL"); relays.add(url);
        }
        if (d.read() != -1) throw new IOException("Trailing pairing data");
        StringBuilder hex = new StringBuilder(); for (byte v : topic) hex.append(String.format(Locale.ROOT,"%02x",v & 255));
        return new Payload(hex.toString(),psk,timeout,ttl,relays);
    }
}
