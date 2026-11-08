# OMS protocol v0 (legacy, read compatibility)

V0 text is `oms00_` followed by standard Base64, optionally line-wrapped. V0 files are raw binary and conventionally end in `.oms00`. There is no binary magic or application-version field.

Integers and length prefixes are unsigned 16-bit, big endian. A blob/string is its u16 byte length followed by its bytes; strings are UTF-8. Fields must fit 0–65535; truncation/overflow is an error.

RSA/AES envelope: outer application ID:u16, RSA algorithm:u16, fingerprint:blob16, AES algorithm:u16, IV:blob16, wrapped AES key:blob16, ciphertext. Text ciphertext is blob16; file ciphertext occupies the remainder of the file. RSA codes: 0 PKCS1, 1 OAEP-SHA1/MGF1-SHA1, 2 OAEP-SHA256/MGF1-SHA256. AES codes: 0 CBC/PKCS5, 1 CBC/PKCS7, 2 GCM (128-bit tag). Fingerprint: SHA-256 of signed-positive BigInteger modulus/exponent bytes. Generic outer ID 6 decrypts to inner application ID:u16 and payload:blob16.

Backup ID 0: alias:string16, salt:blob16, IV:blob16, AES algorithm:u16, KDF:u16 (0=PBKDF2-HMAC-SHA256), AES key bits:u16, iterations:u16, ciphertext:blob16. Decrypted ciphertext is PKCS8 private key:blob16 followed by SPKI public key:blob16. Historically, a default count of 600000 overflowed to 10176. The new reader retries that known case only after the recorded-count attempt fails full key validation.

Control request IDs 4/13: reference:string16, response SPKI:blob16, target fingerprint:blob16, target RSA algorithm:u16, response RSA algorithm:u16, wrapped target AES key:blob16. Reply is ID 5:u16 plus wrapped response key:blob16, sent as bare Base64. Paired request ID 11 omits the response SPKI/algorithm; its legacy reply is a raw key:blob16 within paired transport.

The old Nostr pairing/transport is retired, not a v1 fallback. Existing v0 secrets and QR examples remain usable. See [v1 specification](protocol-v1.md) for all new writes.
