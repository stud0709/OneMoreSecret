# OneMoreSecret v1.1

OneMoreSecret 1.1 introduces the OMS v1 encrypted format, tools for upgrading existing secrets and backups, and fixes for private-key backup recovery and Swiss keyboard auto-type.

## Before updating

- **Update OneMoreSecret, omsCompanion and oms4web together.** Older clients cannot read newly generated v1 output.
- Existing RSA keys, `oms00_` messages, `.oms00` files and printed backups remain readable. You do not need to generate new keys or convert everything at once.
- **Recreate Nostr pairings after updating.** Existing transport sessions are cleared because the paired-message format has changed.
- Keep your original backups until you have saved and verified any replacements.

## New encrypted format

- New text and QR output is bare Base64. The old human-readable `oms00_` prefix is replaced by a binary `%OMS-` marker and protocol version inside the encoded data.
- New encrypted files use the `.oms` extension. Legacy `.oms00` files remain supported.
- Secret application IDs and application-format versions are encrypted with the payload.
- New RSA-encrypted output uses RSA-OAEP with SHA-256 and MGF1-SHA256, plus AES-256-GCM. Files use authenticated chunks and a completion record to detect corruption and truncation.

## Update existing secrets and backups

- **Messages, passwords, OTP secrets and Bitcoin keys:** after decrypting a legacy payload, choose **Update format**. A read-only result screen provides the usual Bluetooth **Type** button, target and keyboard layout controls, plus Copy and Share, so you can replace ciphertext stored on your computer.
- Updates preserve the original secret and RSA recipient. TOTP updates retain the seed; Bitcoin updates retain the wallet key and address.
- **Bitcoin backups:** choose **Create backup** on the updated-message screen to export a cold-wallet HTML document containing the public address QR code and the updated encrypted private key as a QR sequence and Base64 text.
- **Encrypted files:** after decryption, **Update format** exports a new `.oms` file for the same recipient, keeping the original encrypted file.
- **Private-key backups:** after successfully decrypting a backup in the import dialog, **Update format** creates a new HTML/QR backup using the same RSA pair, displayed alias and transport passphrase. Creating this document does not require importing or replacing the key.

## Backup recovery and file handling

- Fixed the private-key backup iteration-count overflow. New backups preserve the full PBKDF2 count in a 32-bit field.
- Import can recover legacy backups affected by the historical default-count overflow, using the original transport passphrase. Unknown custom overflow counts cannot be recovered automatically.
- Imported RSA public and private keys are validated as a pair before saving.
- For v1 files, decryption validates the complete authenticated payload before making the result available. Failed or cancelled operations discard staged output.

## Nostr and auto-type fixes

- Paired messages now authenticate the topic, sender and timestamp, reject replayed messages, and correlate key responses with pending requests.
- The existing 24-hour pairing expiry remains a convenience feature for clearing stale connections, separate from request timeouts.
- Fixed Swiss keyboard auto-type turning `^E` into `§Ê`. A literal caret is committed before typing the next character.
- Leaving an output screen stops auto-type.

## Companion upgrade behavior

With the matching omsCompanion update, FileSync automatically regenerates included legacy mirrors from their plaintext sources on the next sync, even if the sources have not changed. It publishes the new `.oms` file before removing the old `.oms00` copy. Later syncs skip unchanged files that are already upgraded.
