# OMS protocol v1

This is the canonical specification for Android OneMoreSecret, omsCompanion, and oms4web. The protocol version is independent of each application's release version and Android package identifier. New writes use v1; v0 remains readable. The fixture bundle in `protocol/test-vectors/fixtures.json` contains publicly disclosed **test keys**, never production keys.

## Transport and framing

Binary integers are unsigned, big endian. New text is canonical, padded, standard Base64 of the complete binary frame, without an `oms_` prefix. Readers accept ASCII spaces, tabs, CR and LF. Clipboard recognition requires decoded `%OMS-` magic; ordinary Base64 is not an OMS message. A matching magic with an unknown version is an unsupported OMS message, never plaintext to be encrypted again. Text decoding is capped at 2,097,152 Base64 characters.

Files use `.oms`. `.oms00` imports remain supported. V1 magic is recognized independently of the extension. Nostr carries the complete binary message as Base64 inside its authenticated transport. QR sequence splitting is a separate transport layer and remains unchanged.

```
ASCII "%OMS-" (5 bytes)
protocolVersion:u8 = 1
envelopeKind:u8
parameterLength:u16 (maximum 2048)
parameters:byte[parameterLength]
body:bytes to EOF
```

| Kind | Parameters | Body |
|---|---|---|
| 1: RSA secret | fingerprint:32, wrappedKey:blob32 (256–1024 bytes), noncePrefix:8 | Authenticated records |
| 2: password backup | salt:16, iterations:u32, noncePrefix:8 | Authenticated records |
| 3: control | Empty | applicationId:u16, applicationVersion:u16, control fields |

`blob32` means a u32 byte length followed by exactly that many bytes. General blobs are capped at 1 MiB; individual fields have tighter bounds below. EOF, oversize fields, extra header fields and unsupported kinds/versions are errors. Never guess a different protocol after decryption failure.

The fixed RSA suite is RSA-OAEP with SHA-256, MGF1-SHA256 and an empty label. Keys are at least 2048 bits; wrapped keys are at most 1024 bytes. A fresh 32-byte AES key is generated for each RSA envelope. The fingerprint remains SHA-256 of Java signed-positive BigInteger encodings of the modulus followed by the public exponent, preserving existing key identity. Recipient identity is public; the secret application ID is encrypted. Kind 1 implicitly replaces the old outer RSA_AES_GENERIC ID 6.

Password backups use PBKDF2-HMAC-SHA256, UTF-8 passphrases without normalization, a fresh 16-byte salt, and a 32-byte AES key. The default iteration count is 600000. Readers/writers accept 1–10000000 iterations and reject larger values before derivation. The iteration count is a u32 field, never a legacy u16 field.

## Authenticated records

All encrypted envelopes use AES-256-GCM with 128-bit tags. Plaintext begins with `applicationId:u16 | applicationVersion:u16 = 1 | applicationBody:bytes to EOF`.

```
ciphertextLength:u32
flag:u8 (0 = data; 1 = final)
ciphertextAndTag:byte[ciphertextLength]
```

Data records contain 1–65536 plaintext bytes and 17–65552 ciphertext bytes. The mandatory final record encrypts zero plaintext bytes, has length 16 and flag 1. No bytes may follow it. An empty file still contains its four-byte encrypted application header in a data record.

Record indices start at zero. Each nonce is `noncePrefix:8 | recordIndex:u32`. Additional authenticated data is the **entire exact encoded envelope header**, then `recordIndex:u32 | flag:u8 | ciphertextLength:u32`. Thus parameters, position, length and completion are authenticated. Index 0xffffffff is reserved for the final record; overflow fails. Every new encryption attempt generates fresh key/salt/nonce material. Never reuse a partially written envelope's parameters for a retry.

JVM clients stream records using bounded buffers. Decryption writes to a private staging file and publishes with atomic replacement only after the final record, EOF and application version validate. Failure/cancellation deletes staging output and retains the existing destination. The PWA returns a complete vault buffer only after all records validate. Per-record overhead is 16 tag bytes plus 5 framing bytes; final-record/header overhead is additional.

## Secret applications

| ID | Application body v1 |
|---|---|
| 0 | alias:blob32 UTF-8 (≤4096 bytes), PKCS8 private key:blob32 (≤16384), SPKI public key:blob32 (≤16384); password envelopes only |
| 3 | Original file bytes; PWA vaults use their existing UTF-8 JSON schema |
| 7 | Existing Bitcoin private-key byte representation |
| 8 | UTF-8 message |
| 9 | UTF-8 OTP URI |
| 10 | Nostr pairing body described below |
| 12 | Existing UTF-8 OTP/PIN message |

Deprecated IDs 1 and 2, and outer ID 6, belong to v0 only. Changing an application's body schema requires changing its application version; incompatible framing/crypto changes require a new protocol version.

Backup import parses both RSA keys, verifies their pairing by signing/verifying a random challenge, and rejects trailing data before preparing a replacement. Android prepares the complete stored-key entry and replaces matching alias/fingerprint entries in one preferences commit. It does not delete the old key before preparation succeeds. V1 encoded backups are capped at 128 KiB.

## Versioned key controls

Requests 4 (QR), 11 (paired Nostr) and 13 (Android callback) use kind 3, application version 1. Their body starts with a fresh random `requestId:16 | targetProtocol:u8 (0 or 1)` followed by the **existing bounded v0 request fields without their initial application ID**. Retaining these small u16-prefixed fields preserves the request display/reference and avoids a second migration of unrelated field encodings. Total legacy request representation is capped at 16384 bytes.

Requests 4/13: reference:UTF-8 blob16, ephemeral response SPKI:blob16, target fingerprint:blob16, target RSA algorithm:u16, response RSA algorithm:u16, wrapped target key:blob16. Request 11: reference:UTF-8 blob16, target fingerprint:blob16, target RSA algorithm:u16, wrapped target key:blob16. Algorithm codes retain v0 meanings (0=PKCS1, 1=OAEP-SHA1, 2=OAEP-SHA256). A v1 target requires code 2; a v1 request's wrapped response requires code 2. The target's AES algorithm/padding stays in the immutable file context, not the control reply.

Response 5: `requestId:16 | mode:u8 | key:blob32 (≤1024 bytes)`. Mode 0 is an OAEP-SHA256-wrapped key for the request's ephemeral public key. Mode 1 is a raw 16/24/32-byte AES key and is accepted only for a matching paired request over authenticated Nostr. A v1 target requires 32 bytes. QR/callback contexts reject mode 1. Reject mismatched IDs, modes, application versions and trailing data. A request may target an existing v0 file. Incoming v0 requests receive their exact legacy response format; new requests always use v1 controls.

## Nostr pairing and transport v1

Pairing application 10 is inside an RSA envelope. Its body is `transportVersion:u8=1 | topic:32 | PSK:32 | requestTimeoutSeconds:u32 | sessionTtlSeconds:u32 | relayCount:u16 | relayURLs:blob32 UTF-8`. Defaults are 120 seconds for request timeout and 86400 for session cleanup. Timeout must be 1–86400; session TTL 1–604800; 1–32 relay URLs, each ≤2048 bytes, with `wss` scheme, a hostname and no credentials. The complete pairing body is capped at 65536 bytes. Old pairing bodies are rejected with a request to recreate the pairing. Upgrades invalidate persisted transport-v0 sessions while retaining RSA keys.

Outer events use kind 25000 and a `t` tag containing the lowercase 64-hex topic. Event signatures must validate before processing. Content is Base64 of `ASCII "%OMS-NOSTR-" | transportVersion:u8=1 | nonce:12 | ciphertextAndTag`. AES-256-GCM uses the PSK; authenticated data is `ASCII "%OMS-NOSTR-" | 1:u8 | topic:32 | senderPubkey:32 | created_at:u64`.

Encrypted JSON includes `v:1`, fresh UUIDv4 `msg_id`, and `type` (`paired`, `request`, `response`, `disconnect`). Requests contain UUID `req_id` and Base64 binary `payload`; responses contain matching `reply_to` and `payload`. There is one paired acknowledgment, no ping/pong handshake. Responses without a matching pending request are rejected. Android admits one interactive request at a time and binds its response sender to that request.

Accepted event timestamps lie between local now−60 and now+30 seconds. Authenticated message IDs are deduplicated until `created_at+61`; expired entries are pruned, with a maximum 4096 live entries and rejection when full. Android preferences and PWA local storage persist the replay window **before** dispatch; a persistence failure rejects the operation. Companion retains it for the life of the pairing, which has no persisted restart/resume feature. No unauthenticated event timestamp advances a watermark. Re-signing captured ciphertext with another sender/time fails GCM authentication; changing unrelated tags still cannot bypass authenticated message-ID deduplication.

Session expiry is stale-connection cleanup checked on use/resume, not a promise that keys disappear precisely at the deadline. It is separate from request timeout and message freshness.

## Compatibility and migration

V0 text keeps its `oms00_` prefix. V0 binary framing and algorithm codes do not change. Legacy u16 writers reject overflow at runtime; readers use exact reads. Historical PWA double-padded CBC vaults and ordinary Java/Android CBC vaults remain readable.

Legacy backup recovery uses the recorded count first and validates the complete key pair. Only when count 10176 fails does it retry 600000, the known default-count overflow. It never tries arbitrary multiples. A custom overwritten count cannot generally be recovered; preserve the original backup and passphrase rather than pretending its count is known.

Do not scan or rewrite external secrets, printed/buried QR codes, or arbitrary storage. A managed PWA vault is rewritten after successful unlock/save; encrypted fields/history remain unchanged unless explicitly replaced. Vault bytes and a freshly prepared quick-unlock wrapper are saved atomically. Export has no quick-unlock side effects. FileSync rebuilds legacy mirrors directly from plaintext sources as `.oms`, even with unchanged checksums. It skips current mirrors with matching source checksums and recipient fingerprints, removes an obsolete `.oms00` copy only after successful atomic publication, and rejects unknown future versions instead of downgrading them. Android also offers explicit upgrades of decrypted legacy messages, OTP secrets, Bitcoin key payloads and files; external stored originals are replaced only by the user.

Release Android, PWA and Companion together. Old clients cannot consume v1 output; upgraded clients can consume old output. Preserve existing tutorial vectors as labeled legacy examples. Do not change Android package/intent identifiers or internal biometric/master-key encryption solely to version the portable protocol.

## Validation

Run Android flavor unit tests, `protocol/test/run-tests.ps1`, Companion Maven tests, and PWA `npm test`, `npm run typecheck`, `npm run build`. Cross-language fixtures cover RSA/OAEP and fingerprint interoperability, non-ASCII passphrases, multi-chunk data, header/tag tampering, truncation, completion, ordering, backup overflow and legacy padding. Fixture keys are public test material. Compare SHA-256 of the fixture bundle and shared JVM sources across repositories before release. See `protocol-transition.md` for the coordinated release checklist.
