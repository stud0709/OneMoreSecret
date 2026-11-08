package com.onemoresecret.crypto

import org.junit.Assert.*
import org.junit.Test
import org.onemoresecret.protocol.BackupCodec
import org.onemoresecret.protocol.OmsProtocol
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class PrivateKeyBackupTest {
    private fun fixture(name: String): String {
        val json = Files.readString(Path.of(System.getProperty("oms.fixtures")))
        return Regex("\"$name\": \"([^\"]*)\"").find(json)!!.groupValues[1]
    }

    @Test fun updatesLegacyOverflowAndCurrentBackupsWithoutChangingKeysOrPassphrase() {
        val password = fixture("password").toCharArray()
        for (name in listOf("backupOverflow", "backupLegacy", "backupV1")) {
            val original = Base64.getDecoder().decode(fixture(name))
            val originalCopy = original.copyOf()
            val recovered = BackupCodec.open(original, password)
            val privateCopy = recovered.privateKey.copyOf()
            val publicCopy = recovered.publicKey.copyOf()
            val updated = PrivateKeyBackup.create("Updated alias – 🔐", recovered.privateKey, recovered.publicKey, password, 600000)
            val header = OmsProtocol.readHeader(DataInputStream(ByteArrayInputStream(updated)))
            assertEquals(OmsProtocol.PASSWORD, header.kind)
            assertEquals(600000, header.iterations)
            val imported = BackupCodec.open(updated, password)
            assertEquals("Updated alias – 🔐", imported.alias)
            assertArrayEquals(privateCopy, imported.privateKey)
            assertArrayEquals(publicCopy, imported.publicKey)
            assertArrayEquals(originalCopy, original)
            assertArrayEquals(privateCopy, recovered.privateKey)
            assertArrayEquals(publicCopy, recovered.publicKey)
            assertArrayEquals(fixture("password").toCharArray(), password)
            assertThrows(Exception::class.java) { BackupCodec.open(updated, "wrong passphrase".toCharArray()) }
        }
    }

    @Test fun repeatedUpdatesUseFreshEncryptionAndRejectInvalidParameters() {
        val password = fixture("password").toCharArray()
        val backup = BackupCodec.open(Base64.getDecoder().decode(fixture("backupV1")), password)
        val first = PrivateKeyBackup.create(backup.alias, backup.privateKey, backup.publicKey, password, 600000)
        val second = PrivateKeyBackup.create(backup.alias, backup.privateKey, backup.publicKey, password, 600000)
        assertFalse(first.contentEquals(second))
        val a = OmsProtocol.readHeader(DataInputStream(ByteArrayInputStream(first)))
        val b = OmsProtocol.readHeader(DataInputStream(ByteArrayInputStream(second)))
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.noncePrefix.contentEquals(b.noncePrefix))
        for (iterations in listOf(0, -1, OmsProtocol.MAX_ITERATIONS + 1)) {
            assertThrows(IllegalArgumentException::class.java) {
                PrivateKeyBackup.create(backup.alias, backup.privateKey, backup.publicKey, password, iterations)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { PrivateKeyBackup.create(" ", backup.privateKey, backup.publicKey, password, 600000) }
        assertThrows(IllegalArgumentException::class.java) { PrivateKeyBackup.create("a".repeat(4097), backup.privateKey, backup.publicKey, password, 600000) }
        val mismatched = BackupCodec.open(Base64.getDecoder().decode(fixture("helloWorldBackup")), fixture("helloWorldPassword").toCharArray())
        assertThrows(IllegalArgumentException::class.java) { PrivateKeyBackup.create(backup.alias, backup.privateKey, mismatched.publicKey, password, 600000) }
    }
}
