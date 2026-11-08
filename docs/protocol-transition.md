# Coordinated OMS v1 transition

## User-visible changes

Update Android OneMoreSecret, omsCompanion and oms4web together. New clipboard/QR messages are bare Base64 whose decoded bytes start with `%OMS-` and version 1. New encrypted files use `.oms`. New secrets hide their application ID and application-format version inside authenticated encryption. All clients continue reading `oms00_` and `.oms00` artifacts. Old clients cannot read new output.

Keep existing RSA keys, printed QR codes, ciphertext fields, histories and externally stored secrets. No bulk migration is necessary or possible for arbitrary storage. The PWA rewrites its managed vault after successful unlock/save, preserving encrypted field values. FileSync automatically rebuilds every legacy mirror from its plaintext source as `.oms`, even when its source checksum is unchanged. Current mirrors with matching source checksums and recipient keys are skipped on subsequent runs.

Recreate Nostr pairings after updating; old persisted transport sessions are invalidated. Pairing now authenticates sender/topic/timestamp and rejects repeated authenticated messages. Its 24-hour expiry is convenience cleanup for stale connections. A 120-second request timeout is a separate setting.

New key backups use a 32-bit PBKDF2 iteration count. Imports validate both RSA keys before replacement. Legacy default-overflow backups can be recovered automatically using the passphrase. Unknown custom overflow counts are not guessed. Keep the original backup until recovery is verified.

Android's private-key import dialog also offers **Update format** after successful decryption. It creates a fresh HTML/QR backup using the validated RSA pair, displayed alias and same transport passphrase, without requiring import. Save the document from the share sheet and verify it before discarding an older backup.

## Optional upgrades after decryption

For legacy messages/passwords, OTP/PIN messages, TOTP secrets and Bitcoin private-key payloads, Android's decrypted-message screen offers **Update format**. On the updated-message screen, use **Type** to send the new ciphertext to a computer over Bluetooth, or use Copy or Share. Replace the old value in your chosen storage with this output. The original decrypted application body is preserved; a TOTP seed is upgraded rather than its current code, and a Bitcoin private key rather than its displayed address/WIF text. The original RSA recipient is retained. For Bitcoin keys, the updated-message screen also offers **Create backup** to share a cold-wallet HTML document containing the same public address and its QR code, plus the updated encrypted private key as a QR sequence and Base64 text. This document uses the same ciphertext offered by Type, Copy and Share; it does not generate a new wallet. Current v1 secrets and protocol controls do not offer this conversion; recreate old Nostr pairings.

For a legacy encrypted file, decrypt it first, then choose **Update format** to share a fresh .oms file for the same RSA recipient. The original encrypted file is retained. Source filenames keep their extension after later decryption; updated exports use unique names.

FileSync is different: plaintext sources are already available, so no private-key exchange is required for its automatic upgrade. A legacy .oms00 mirror is removed only after its .oms replacement has been encrypted and atomically published. Failures/cancellation retain old copies. Per-file envelope checks allow interrupted migrations to resume; already upgraded, unchanged files are skipped. Renaming .oms00 to .oms does not bypass the format check. An unrecognized future OMS version fails rather than being downgraded.

## Release checklist

- Freeze/review the [v1 wire specification](protocol-v1.md); retain [v0 documentation](protocol-v0.md).
- Compare identical shared Java sources and fixture SHA-256 across all three repositories. Never use fixture keys for real secrets.
- Run both Android flavors' unit tests/compile checks, Companion `mvn test`, and PWA `npm test`, `npm run typecheck`, `npm run build`.
- Verify QR animation, clipboard detection, Android intent callback, paired file unlock, large-file cancellation/failure cleanup, and successful/failed key replacement on actual supported devices/browsers.
- Confirm old published Hello World/backup examples still decrypt; add v1 examples while labeling legacy examples.
- Confirm exports leave the existing PWA quick-unlock wrapper intact and vault/wrapper saves are atomic. A failed save must preserve the previous stored vault.
- Update all three wikis, README links, generated backup descriptions and coordinated release notes.
- Publish the three clients together. For the PWA, users finish edits/key exchanges before explicitly applying its update prompt.
- Recreate pairings and exercise cross-client request/response flows after deployment. Publishing/installing releases is a separate release action.

## Developer verification

Portable JVM tests: `./protocol/test/run-tests.ps1`. The harness can also verify PWA-produced fixtures: run PWA tests with `OMS_INTEROP_OUT` pointing to a temporary JSON file, then pass that file as `-Fixtures` to the JVM test script.

On affected Windows JDK installations, Gradle's loopback/pipe initialization succeeds with `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.WindowsSelectorProvider`. This is an invocation workaround, not a change to project JVM settings.
