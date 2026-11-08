package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.crypto.AESUtil.process
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.function.Consumer
import java.util.function.Supplier
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

object EncryptedFile {
    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class,
        InvalidKeyException::class,
        InvalidAlgorithmParameterException::class,
        IOException::class
    )
    @JvmStatic
    fun create(
        fis: InputStream,
        oFile: File,
        rsaPublicKeyMaterial: ByteArray,
        rsaTransformation: RsaTransformation,
        aesKeyLength: Int,
        aesTransformation: AesTransformation,
        cancellationSupplier: Supplier<Boolean>?,
        progressConsumer: Consumer<Int>?
    ) {
        val target = oFile.toPath()
        val staging = java.nio.file.Files.createTempFile(target.toAbsolutePath().parent, ".oms-", ".part")
        try {
            FileOutputStream(staging.toFile()).use { out ->
                org.onemoresecret.protocol.OmsProtocol.encrypt(RSAUtil.restorePublicKey(rsaPublicKeyMaterial),
                    MessageComposer.APPLICATION_ENCRYPTED_FILE, fis, out, cancellationSupplier,
                    { n -> progressConsumer?.accept(n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) })
            }
            java.nio.file.Files.move(staging, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { java.nio.file.Files.deleteIfExists(staging) }
    }
}
