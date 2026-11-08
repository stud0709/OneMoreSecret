import org.onemoresecret.protocol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;
import java.util.regex.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Dependency-free interoperability and corruption tests; fixture keys are TEST ONLY. */
public final class ProtocolTest {
    static int checks;
    static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    interface Attempt { void run() throws Exception; }
    static void rejects(Attempt action, String label) throws Exception { try { action.run(); } catch (Exception expected) { checks++; return; } throw new AssertionError(label); }
    static byte[] decode(String s) { return Base64.getDecoder().decode(s); }
    static String encode(byte[] b) { return Base64.getEncoder().encodeToString(b); }
    static Map<String,String> load(Path path) throws IOException {
        Map<String,String> values = new LinkedHashMap<>(); Matcher m = Pattern.compile("\"([^\"]+)\": \"([^\"]*)\"").matcher(Files.readString(path));
        while(m.find()) values.put(m.group(1),m.group(2)); return values;
    }
    static byte[] key(char[] password, byte[] salt, int count) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, count, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); } finally { spec.clearPassword(); }
    }
    static byte[] cbc(byte[] key, byte[] plain, byte[] iv) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"), new IvParameterSpec(iv)); return c.doFinal(plain);
    }
    static void legacyBlob(DataOutputStream out, byte[] b) throws IOException { out.writeShort(b.length); out.write(b); }
    static byte[] legacyBackup(KeyPair pair, char[] password, int iterations) throws Exception {
        var plain = new ByteArrayOutputStream(); var p = new DataOutputStream(plain);
        legacyBlob(p,pair.getPrivate().getEncoded()); legacyBlob(p,pair.getPublic().getEncoded());
        byte[] salt = OmsProtocol.random(16), iv = OmsProtocol.random(16);
        var result = new ByteArrayOutputStream(); var out = new DataOutputStream(result); out.writeShort(0); legacyBlob(out,"fixture".getBytes(StandardCharsets.UTF_8));
        legacyBlob(out,salt); legacyBlob(out,iv); out.writeShort(0); out.writeShort(0); out.writeShort(256); out.writeShort(iterations & 65535);
        legacyBlob(out,cbc(key(password,salt,iterations),plain.toByteArray(),iv)); return result.toByteArray();
    }
    static byte[] legacyFile(RSAPublicKey pub, byte[] aes, byte[] body) throws Exception {
        var b = new ByteArrayOutputStream(); var d = new DataOutputStream(b); byte[] iv=OmsProtocol.random(16);
        d.writeShort(3); d.writeShort(2); legacyBlob(d,OmsProtocol.fingerprint(pub)); d.writeShort(0); legacyBlob(d,iv); legacyBlob(d,OmsProtocol.rsa(Cipher.ENCRYPT_MODE,pub,aes)); d.write(cbc(aes,body,iv)); return b.toByteArray();
    }
    static byte[] backupBody(KeyPair pair) throws Exception {
        var b=new ByteArrayOutputStream(); var d=new DataOutputStream(b); OmsProtocol.writeBlob(d,"fixture – 🔐".getBytes(StandardCharsets.UTF_8)); OmsProtocol.writeBlob(d,pair.getPrivate().getEncoded()); OmsProtocol.writeBlob(d,pair.getPublic().getEncoded()); return b.toByteArray();
    }
    static void generate(Path path) throws Exception {
        KeyPairGenerator g=KeyPairGenerator.getInstance("RSA"); g.initialize(2048); KeyPair pair=g.generateKeyPair(); RSAPublicKey pub=(RSAPublicKey)pair.getPublic();
        byte[] text="Hello OMS – 🔐".getBytes(StandardCharsets.UTF_8), file=new byte[65549]; for(int i=0;i<file.length;i++) file[i]=(byte)i;
        byte[] salt=OmsProtocol.random(16); String password="fixture-password-🔑"; byte[] aes=OmsProtocol.random(32), psk=OmsProtocol.random(32);
        Map<String,String> v=new LinkedHashMap<>(); v.put("publicKey",encode(pub.getEncoded())); v.put("privateKey",encode(pair.getPrivate().getEncoded()));
        v.put("textBody",encode(text)); v.put("rsaText",encode(OmsProtocol.seal(pub,8,text))); v.put("fileBody",encode(file)); v.put("rsaFile",encode(OmsProtocol.seal(pub,3,file)));
        v.put("password",password); v.put("backupV1",encode(OmsProtocol.passwordSeal(key(password.toCharArray(),salt,600000),salt,600000,0,backupBody(pair))));
        v.put("backupOverflow",encode(legacyBackup(pair,password.toCharArray(),600000))); v.put("backupLegacy",encode(legacyBackup(pair,password.toCharArray(),10176)));
        byte[] json="{\"entries\":[],\"settings\":{\"workspaceProtection\":\"none\"}}".getBytes(StandardCharsets.UTF_8);
        v.put("legacyVaultKey",encode(aes)); v.put("vaultJson",encode(json)); v.put("legacyVault",encode(legacyFile(pub,aes,json)));
        int padding=16-json.length%16; byte[] doublePad=Arrays.copyOf(json,json.length+padding); Arrays.fill(doublePad,json.length,doublePad.length,(byte)padding);
        v.put("legacyPwaVault",encode(legacyFile(pub,aes,doublePad)));
        String topic="11".repeat(32), sender="22".repeat(32); long timestamp=1800000000L;
        v.put("psk",encode(psk)); v.put("topic",topic); v.put("sender",sender); v.put("timestamp",Long.toString(timestamp));
        v.put("nostr",encode(NostrTransport.encrypt(text,psk,topic,sender,timestamp)));
        Path legacySample=path.resolveSibling("hello-world-v0.txt");
        if (!Files.exists(legacySample)) legacySample=Path.of("protocol/test-vectors/hello-world-v0.txt");
        v.put("helloWorldBackup",Files.readString(legacySample,StandardCharsets.UTF_8).trim()); v.put("helloWorldPassword","we are all mad here");
        StringJoiner j=new StringJoiner(",\n","{\n","\n}\n"); v.forEach((k,value)->j.add("  \""+k+"\": \""+value+"\"")); Files.createDirectories(path.getParent()); Files.writeString(path,j.toString(),StandardCharsets.UTF_8);
    }
    static InputStream shortReads(byte[] bytes) { return new FilterInputStream(new ByteArrayInputStream(bytes)) { @Override public int read(byte[] b,int off,int len) throws IOException { return super.read(b,off,Math.min(len,3)); } }; }
    public static void run(Path path) throws Exception {
        Map<String,String> v=load(path); KeyFactory f=KeyFactory.getInstance("RSA"); PrivateKey privateKey=f.generatePrivate(new PKCS8EncodedKeySpec(decode(v.get("privateKey"))));
        RSAPublicKey publicKey=(RSAPublicKey)f.generatePublic(new X509EncodedKeySpec(decode(v.get("publicKey"))));
        for(String name:List.of("rsaText","rsaFile")) {
            byte[] encoded=decode(v.get(name)); var in=new DataInputStream(shortReads(encoded)); var h=OmsProtocol.readHeader(in); byte[] aes=OmsProtocol.rsa(Cipher.DECRYPT_MODE,privateKey,h.wrappedKey), records=in.readAllBytes();
            var plain=new DataInputStream(new ByteArrayInputStream(OmsProtocol.open(h,aes,records))); int id=OmsProtocol.readApplication(plain); byte[] body=plain.readAllBytes();
            check(id==(name.equals("rsaFile")?3:8),"application"); check(Arrays.equals(body,decode(v.get(name.equals("rsaFile")?"fileBody":"textBody"))),"fixture plaintext");
            check(Arrays.equals(encoded,OmsProtocol.decodeText(OmsProtocol.encodeText(encoded))),"text transport");
            byte[] changed=records.clone(); changed[changed.length-1]^=1; rejects(()->OmsProtocol.open(h,aes,changed),"tag tamper");
            rejects(()->OmsProtocol.open(h,aes,Arrays.copyOf(records,records.length-21)),"missing final");
            rejects(()->OmsProtocol.open(h,aes,Arrays.copyOf(records,records.length-1)),"truncated final");
            byte[] trailing=Arrays.copyOf(records,records.length+1); rejects(()->OmsProtocol.open(h,aes,trailing),"trailing byte");
            byte[] wrongKey=aes.clone(); wrongKey[0]^=1; rejects(()->OmsProtocol.open(h,wrongKey,records),"wrong key");
            byte[] header=encoded.clone(); header[9]^=1; var bad=OmsProtocol.readHeader(new DataInputStream(new ByteArrayInputStream(header))); rejects(()->OmsProtocol.open(bad,aes,records),"header tamper");
            if(id==3) {
                var out=new ByteArrayOutputStream(); OmsProtocol.decryptFile(h,aes,shortReads(records),out,null,null); check(Arrays.equals(out.toByteArray(),body),"file header removed");
                int firstLength=ByteBufferReader(records)+5; byte[] swapped=new byte[records.length]; int secondLength=ByteBufferReader(Arrays.copyOfRange(records,firstLength,records.length))+5;
                System.arraycopy(records,firstLength,swapped,0,secondLength); System.arraycopy(records,0,swapped,secondLength,firstLength); System.arraycopy(records,firstLength+secondLength,swapped,firstLength+secondLength,records.length-firstLength-secondLength);
                rejects(()->OmsProtocol.open(h,aes,swapped),"reordered chunks");
                rejects(()->OmsProtocol.encrypt(publicKey,3,new ByteArrayInputStream(body),new ByteArrayOutputStream(),()->true,null),"cancel encryption");
            }
        }
        check(OmsProtocol.decodeText(encode("ordinary text".getBytes(StandardCharsets.UTF_8)))==null,"ordinary Base64 ignored");
        byte[] future=decode(v.get("rsaText")); future[5]=2; rejects(()->OmsProtocol.decodeText(encode(future)),"future protocol");
        byte[] wrongApp=OmsProtocol.application(8,new byte[0]); wrongApp[3]=2; rejects(()->OmsProtocol.readApplication(new DataInputStream(new ByteArrayInputStream(wrongApp))),"future application");
        for(String name:List.of("backupV1","backupOverflow","backupLegacy")) {
            var backup=BackupCodec.open(decode(v.get(name)),v.get("password").toCharArray()); check(Arrays.equals(backup.privateKey,decode(v.get("privateKey"))),"backup private key"); check(Arrays.equals(backup.publicKey,decode(v.get("publicKey"))),"backup public key");
            rejects(()->BackupCodec.open(decode(v.get(name)),"wrong".toCharArray()),"wrong backup password");
        }
        var published = BackupCodec.open(decode(v.get("helloWorldBackup")), v.get("helloWorldPassword").toCharArray());
        check(published.privateKey.length > 0 && published.publicKey.length > 0, "published v0 Hello World key imports");
        rejects(() -> OmsProtocol.validateKeyPair(decode(v.get("privateKey")), published.publicKey), "mismatched backup keys");
        byte[] legacyRequest={0,4,0,0}, requestId=OmsProtocol.random(16); var request=OmsProtocol.readRequest(OmsProtocol.request(legacyRequest,1,requestId));
        check(Arrays.equals(request.legacy,legacyRequest),"control adaptation"); byte[] aes=OmsProtocol.random(32), reply=OmsProtocol.reply(requestId,true,aes); check(Arrays.equals(aes,OmsProtocol.readReply(reply,requestId,true)),"paired reply");
        rejects(()->OmsProtocol.readReply(reply,OmsProtocol.random(16),true),"wrong request ID"); rejects(()->OmsProtocol.readReply(reply,requestId,false),"wrong reply mode");
        byte[] psk=decode(v.get("psk")), nostr=decode(v.get("nostr")); long time=Long.parseLong(v.get("timestamp"));
        check(Arrays.equals(decode(v.get("textBody")),NostrTransport.decrypt(nostr,psk,v.get("topic"),v.get("sender"),time,time)),"Nostr plaintext");
        rejects(()->NostrTransport.decrypt(nostr,psk,v.get("topic"),"33".repeat(32),time,time),"re-signed sender");
        rejects(()->NostrTransport.decrypt(nostr,psk,v.get("topic"),v.get("sender"),time+1,time),"changed timestamp");
        rejects(()->NostrTransport.decrypt(nostr,psk,v.get("topic"),v.get("sender"),time,time+61),"stale event");
        var replay=new NostrTransport.ReplayWindow("",time); String id=UUID.randomUUID().toString(); check(replay.accept(id,time,time),"fresh ID"); check(!replay.accept(id,time,time),"replay rejected");
        check(!new NostrTransport.ReplayWindow(replay.encode(),time).accept(id,time,time),"replay rejected after restart"); check(!replay.accept(UUID.randomUUID().toString(),time+31,time),"future event");
        System.out.println("OMS protocol: " + checks + " checks passed");
    }
    static int ByteBufferReader(byte[] b) { return java.nio.ByteBuffer.wrap(b).getInt(); }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args.length>1?args[1]:"protocol/test-vectors/fixtures.json"); if(args.length>0&&args[0].equals("--generate")) generate(path); run(path);
    }
}
